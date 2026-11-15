package com.dantech.splitvibe.data.share

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Bill links on the proxy: a page where everyone at the table picks the items they had, which the
 * bill's owner then turns into the split. Only the owner's calls need a session; reading a link and
 * its live changes needs only the link.
 *
 * Every change of a link goes through here one at a time, and a copy of a bill older than one
 * already sent is never sent after it, so a late copy cannot undo a split already settled.
 */
class ShareRepository(private val api: ProxyApi, private val retryDelayMs: (attempt: Int) -> Long = ::backoff) {

    private val writes = Mutex()

    /** The newest revision of each link's bill sent while the app runs. */
    private val sent = mutableMapOf<String, Int>()

    /** Makes the link [shareId] for [saved], or shows [saved] on it as it is now. */
    suspend fun publish(shareId: String, saved: SavedBill, myName: String): OwnedShare = writes.withLock { put(shareId, saved, myName) }

    /**
     * Shows [saved] on the link it has, after a change, unless a copy as new was sent already.
     * Null when there was nothing to send.
     */
    suspend fun project(saved: SavedBill, myName: String): OwnedShare? = writes.withLock {
        val link = saved.share ?: return@withLock null
        if ((sent[link.shareId] ?: -1) >= saved.revision) return@withLock null
        put(link.shareId, saved, myName)
    }

    suspend fun get(shareId: String): OwnedShare = owned(api.signedIn("GET", "v1/shares/$shareId"))

    /** Stops people from picking, and answers every claim made before. */
    suspend fun lock(shareId: String): ShareView = writes.withLock { view(api.signedIn("POST", "v1/shares/$shareId/lock")) }

    /** Lets people pick again, and takes the settled amounts off the page. */
    suspend fun reopen(shareId: String): ShareView = writes.withLock { view(api.signedIn("POST", "v1/shares/$shareId/reopen")) }

    /** Puts [amounts], what each participant of [saved] owes and how to pay it, on the page of its link. */
    suspend fun finalize(saved: SavedBill, myName: String, amounts: Map<String, ShareAmount>): ShareView = writes.withLock {
        val link = checkNotNull(saved.share) { "The bill has no link" }
        val request = buildJsonObject {
            put("billRevision", saved.revision)
            put("bill", writer.encodeToJsonElement(BillProjection.serializer(), BillProjection.of(saved, myName)))
            put("amounts", writer.encodeToJsonElement(AMOUNTS, amounts))
        }
        view(api.signedIn("POST", "v1/shares/${link.shareId}/finalize", request)).also { remember(link.shareId, saved.revision) }
    }

    suspend fun removeClaim(shareId: String, claimId: String): ShareView =
        writes.withLock { view(api.signedIn("DELETE", "v1/shares/$shareId/claims/$claimId")) }

    /** Takes the link down: its page says it no longer works, and what people picked is deleted. */
    suspend fun revoke(shareId: String) = writes.withLock {
        api.signedIn("DELETE", "v1/shares/$shareId")
        sent.remove(shareId)
    }

    /** The link at [url] as anyone who has it sees it. */
    suspend fun open(url: String): ShareView = view(api.public("v1/shares/open", buildJsonObject { put("token", tokenOf(url)) }))

    /**
     * The link at [url] as it is now, then after every change, until it is gone. A dropped connection
     * is opened again after a pause that grows while it keeps failing.
     */
    fun live(url: String): Flow<LiveShare> = flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            var gone = false
            // Nothing passes after the link is gone, not even a view of it read just before.
            connection(url).transformWhile { event ->
                emit(event)
                event !is LiveShare.Gone
            }.collect { event ->
                attempt = 0
                if (event is LiveShare.Gone) gone = true
                emit(event)
            }
            if (gone) return@flow
            delay(retryDelayMs(attempt++))
        }
    }

    private fun connection(url: String): Flow<LiveShare> = callbackFlow {
        val socket = api.webSocket(
            "v1/shares/live",
            listOf(PROTOCOL, tokenOf(url)),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Changes come from now on; the link as it is now comes with a call of its own.
                    launch {
                        try {
                            trySend(LiveShare.Changed(open(url)))
                        } catch (e: ProxyException) {
                            if (e.status in GONE) trySend(LiveShare.Gone)
                            close()
                        } catch (e: IOException) {
                            close()
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // The answer to a ping is not JSON, and neither is anything this app does not know.
                    val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    when (message["type"]?.jsonPrimitive?.contentOrNull) {
                        "share" -> message["share"]
                            ?.let { runCatching { json.decodeFromJsonElement(ShareView.serializer(), it) }.getOrNull() }
                            ?.let { trySend(LiveShare.Changed(it)) }

                        "gone" -> {
                            trySend(LiveShare.Gone)
                            close()
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    // The proxy closes first when the link goes; the close is answered so it ends now.
                    webSocket.close(NORMAL_CLOSURE, null)
                    close()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Refused before it opened because the link does not exist or no longer works.
                    if (response?.code in GONE) trySend(LiveShare.Gone)
                    close()
                }
            },
        )
        val pings = launch {
            while (isActive) {
                delay(PING_MS)
                socket.send("ping")
            }
        }
        awaitClose {
            pings.cancel()
            socket.close(NORMAL_CLOSURE, null)
        }
    }

    private suspend fun put(shareId: String, saved: SavedBill, myName: String): OwnedShare {
        val request = buildJsonObject {
            put("billId", saved.id)
            put("billRevision", saved.revision)
            put("bill", writer.encodeToJsonElement(BillProjection.serializer(), BillProjection.of(saved, myName)))
        }
        return owned(api.signedIn("PUT", "v1/shares/$shareId", request)).also { remember(shareId, saved.revision) }
    }

    private fun remember(shareId: String, revision: Int) {
        sent[shareId] = maxOf(sent[shareId] ?: -1, revision)
    }

    private fun owned(answer: JsonObject?): OwnedShare {
        val body = checkNotNull(answer) { "The proxy answered nothing" }
        val url = checkNotNull(body["url"]?.jsonPrimitive?.contentOrNull) { "The proxy answered no link" }
        return OwnedShare(view(body), url)
    }

    private fun view(answer: JsonObject?): ShareView =
        json.decodeFromJsonElement(ShareView.serializer(), checkNotNull(checkNotNull(answer) { "The proxy answered nothing" }["share"]))

    companion object {
        const val PROTOCOL = "splitvibe.v1"
        private const val PING_MS = 30_000L
        private const val NORMAL_CLOSURE = 1000
        private val GONE = setOf(404, 410)

        /** Reads the proxy's answers, which may carry fields a newer proxy added. */
        private val json = ProxyCache.json

        /** Writes what goes to the proxy with its nulls, as the contract shows every field. */
        private val writer = Json
        private val AMOUNTS = MapSerializer(String.serializer(), ShareAmount.serializer())

        /** The token of a link, which travels after the `#` of its address. */
        fun tokenOf(url: String): String = url.substringAfter('#', "")

        /** One second, then two, five and ten, then half a minute between attempts. */
        fun backoff(attempt: Int): Long = listOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)[attempt.coerceIn(0, 4)]
    }
}
