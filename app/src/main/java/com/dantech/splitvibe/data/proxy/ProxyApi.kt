package com.dantech.splitvibe.data.proxy

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.fx.DayRate
import com.dantech.splitvibe.data.fx.FxSnapshot
import java.io.IOException
import java.io.InterruptedIOException
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * A refusal or failure the AI proxy reported, before an answer or in place of one. [status] is 200
 * when it came as the error line of a streamed answer. [callsLeft] is set when the proxy said how
 * many calls are left after this request.
 */
class ProxyException(val status: Int, val code: String, val retryAfterSeconds: Long?, val callsLeft: CallsLeft?) :
    Exception("The AI proxy answered $status with $code")

/** The proxy answered, but the rest of its answer did not come in time. */
class AnswerTimeoutException(cause: InterruptedIOException) : IOException("The answer stopped coming before it was complete", cause)

/** The model's answer to a bill read, and the calls left after it. */
data class BillReply(val answer: JsonObject, val callsLeft: CallsLeft?)

/** What `GET /v1/usage` says. */
data class Usage(val callsLeft: CallsLeft, val aiEnabled: Boolean)

/**
 * The app's side of the AI proxy's contract (see `proxy/` and `docs/proxy.md`). Every request says
 * which install it comes from and which version of the app, and carries the session once someone
 * has signed in. Once that sign-in has run out, requests are refused here, the way the proxy
 * refuses an expired session, so that none goes out as the install in the person's place.
 * Cancelling the coroutine of a request cancels the request.
 */
class ProxyApi(
    baseUrl: String,
    private val account: AccountStore,
    private val appVersion: Int,
    private val client: OkHttpClient = defaultClient(),
) {
    private val base: HttpUrl? = baseUrl.toHttpUrlOrNull()

    /** False in a build that was given no address for the proxy. */
    val isSetUp: Boolean get() = base != null

    /** `POST /v1/bill-analysis`: reads the streamed reply up to the line with the answer. */
    suspend fun readBill(body: JsonObject): BillReply = send(post("v1/bill-analysis", body)) { response ->
        if (!response.isSuccessful) throw failureOf(response)
        readAnswer(response)
    }

    /** `POST /v1/session`: trades the ID token Google gave at sign-in for the proxy's session. */
    suspend fun startSession(idToken: String): Pair<Session, CallsLeft?> =
        // Signing in carries Google's token in place of a session, also when the old one has run out.
        send(post("v1/session", buildJsonObject { put("idToken", idToken) }, withSession = false)) { response ->
            if (!response.isSuccessful) throw failureOf(response)
            val body = jsonOf(response)
            val token = body.text("session")
            val expiresAt = body.long("expiresAt")
            if (token.isNullOrEmpty() || expiresAt == null) throw ProxyException(response.code, BAD_RESPONSE, null, null)
            Session(token, expiresAt) to body["usage"]?.let(::callsLeftOf)
        }

    /** `GET /v1/usage`: the calls left of whoever is calling, and whether AI is on. */
    suspend fun usage(): Usage = send(request("v1/usage").get().build()) { response ->
        if (!response.isSuccessful) throw failureOf(response)
        val body = jsonOf(response)
        val callsLeft = body["usage"]?.let(::callsLeftOf) ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
        Usage(callsLeft, aiEnabled = body.boolean("aiEnabled") ?: true)
    }

    /** `GET /v1/fx-rates`: the day's exchange rates. They are the same for everyone, so the request says nothing about who asks. */
    suspend fun fxRates(): FxSnapshot = send(anonymousRequest("v1/fx-rates").get().build()) { response ->
        if (!response.isSuccessful) throw failureOf(response)
        fxSnapshotOf(jsonOf(response)) ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
    }

    /** `DELETE /v1/account`: deletes what the proxy keeps about the signed-in account. */
    suspend fun deleteAccount() = send(request("v1/account").delete().build()) { response ->
        if (!response.isSuccessful) throw failureOf(response)
    }

    /**
     * A call to a route only signed-in people may use: groups, invites, payment requests and
     * devices. Returns the answer's JSON, or null for an answer without a body. Throws
     * [ProxyException] when the proxy refuses, also when nobody is signed in, which the proxy
     * would refuse anyway.
     */
    suspend fun signedIn(method: String, path: String, body: JsonElement? = null): JsonObject? {
        if (account.currentSession() == null) throw ProxyException(401, SESSION_REFUSED, null, null)
        // OkHttp wants a body for these methods even when there is nothing to send.
        val payload = (body?.toString() ?: if (method in BODY_METHODS) "{}" else null)?.toRequestBody(JSON)
        return send(request(path).method(method, payload).build()) { response ->
            if (!response.isSuccessful) throw failureOf(response)
            if (response.code == 204) null else jsonOf(response)
        }
    }

    /**
     * A call to a route anyone with a link may use, such as reading a bill link: it carries no
     * session and no install id, only what [body] holds. Throws [ProxyException] when the proxy refuses.
     */
    suspend fun public(path: String, body: JsonElement): JsonObject =
        send(anonymousRequest(path).post(body.toString().toRequestBody(JSON)).build()) { response ->
            if (!response.isSuccessful) throw failureOf(response)
            jsonOf(response)
        }

    /**
     * Opens a WebSocket to [path] offering [protocols], which is where a link's token travels, as
     * the proxy keeps addresses in its traces and header values not.
     */
    fun webSocket(path: String, protocols: List<String>, listener: WebSocketListener): WebSocket =
        client.newWebSocket(anonymousRequest(path).header("Sec-WebSocket-Protocol", protocols.joinToString(", ")).build(), listener)

    private fun post(path: String, body: JsonObject, withSession: Boolean = true): Request =
        request(path, withSession).post(body.toString().toRequestBody(JSON)).build()

    private fun anonymousRequest(path: String): Request.Builder {
        val url = checkNotNull(base) { "This build has no address for the AI proxy" }.newBuilder().addPathSegments(path).build()
        return Request.Builder().url(url)
    }

    private fun request(path: String, withSession: Boolean = true): Request.Builder {
        val builder = anonymousRequest(path)
            .header(INSTALL_HEADER, account.account.value.installId)
            .header(VERSION_HEADER, appVersion.toString())
        if (!withSession) return builder
        val session = account.currentSession()
        if (session != null) return builder.header("Authorization", "Bearer ${session.token}")
        if (account.account.value.signInExpired) throw ProxyException(401, SESSION_REFUSED, null, null)
        return builder
    }

    private suspend fun <T> send(request: Request, read: (Response) -> T): T = client.newCall(request).await(read)

    private fun readAnswer(response: Response): BillReply {
        val source = checkNotNull(response.body).source()
        while (true) {
            val line = try {
                source.readUtf8Line()
            } catch (e: InterruptedIOException) {
                // The proxy sends a line at least every 10 seconds, so this is silence after it answered.
                throw AnswerTimeoutException(e)
            } ?: throw IOException("The answer ended before it was complete")
            if (line.isBlank()) continue
            val message = parse(line) ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
            when (message.text("type")) {
                "result" -> {
                    val answer = message["answer"] as? JsonObject ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
                    return BillReply(answer, message["usage"]?.let(::callsLeftOf))
                }

                "error" -> throw ProxyException(
                    status = response.code,
                    code = message.text("code") ?: BAD_RESPONSE,
                    retryAfterSeconds = null,
                    callsLeft = message["usage"]?.let(::callsLeftOf),
                )

                // A wait line, or a kind of line this version of the app does not know yet.
                else -> Unit
            }
        }
    }

    private fun failureOf(response: Response): ProxyException {
        val body = runCatching { jsonOf(response) }.getOrNull()
        return ProxyException(
            status = response.code,
            code = body?.text("error") ?: "http_${response.code}",
            retryAfterSeconds = response.header("Retry-After")?.trim()?.toLongOrNull(),
            callsLeft = body?.get("usage")?.let(::callsLeftOf),
        )
    }

    private fun jsonOf(response: Response): JsonObject =
        parse(checkNotNull(response.body).string()) ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)

    companion object {
        const val INSTALL_HEADER = "X-SplitVibe-Install"
        const val VERSION_HEADER = "X-SplitVibe-Version"
        const val BAD_RESPONSE = "bad_response"

        /** The proxy's code for a session it does not accept, because it has run out or is not its own. */
        const val SESSION_REFUSED = "session"

        private val JSON = "application/json".toMediaType()
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // The proxy sends a wait line every 10 seconds while the model works, so a longer
            // silence means the connection is gone.
            .readTimeout(30, TimeUnit.SECONDS)
            // The proxy gives the model 120 seconds; this leaves room for the network on top.
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }
}

private fun parse(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (e: SerializationException) {
    null
}

private fun callsLeftOf(element: JsonElement): CallsLeft? {
    val usage = element as? JsonObject ?: return null
    return CallsLeft(
        limit = usage.calls("limit") ?: return null,
        used = usage.calls("used") ?: return null,
        remaining = usage.calls("remaining") ?: return null,
        period = usage.text("period") ?: return null,
        resetsAt = usage.long("resetsAt"),
    )
}

// Fields of the wrong shape read as missing, so a broken answer is a bad response rather than a crash.

/**
 * A count of calls: a JSON number from 0 with at most two decimals, such as 3, 0.2 or 2.8, read from
 * the number as written and never through a floating-point value. A proxy from before calls could
 * weigh less than one wrote whole numbers, which read the same. The value comes back without
 * trailing zeros, so 2.80 and 2.8 are equal.
 */
private fun JsonObject.calls(key: String): BigDecimal? {
    val number = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBigDecimalOrNull() ?: return null
    // Bounded before anything else is done with it: 1e999999999 is a number too.
    if (number.signum() < 0 || number > MAX_CALLS) return null
    val exact = number.stripTrailingZeros()
    if (exact.scale() > CALL_DECIMALS) return null
    return if (exact.scale() < 0) exact.setScale(0) else exact
}

/** Far more calls than anyone is given, and few enough to write out. */
private val MAX_CALLS = BigDecimal(1_000_000)
private const val CALL_DECIMALS = 2

/** The rates in a `GET /v1/fx-rates` answer; a rate that is not a positive number with a day is left out. */
private fun fxSnapshotOf(body: JsonObject): FxSnapshot? {
    val base = body.text("base") ?: return null
    val source = body.text("source") ?: return null
    val fetchedAt = body.long("fetchedAt") ?: return null
    val rates = body["rates"] as? JsonObject ?: return null
    val kept = rates.mapNotNull { (code, value) ->
        val entry = value as? JsonObject ?: return@mapNotNull null
        // Read from the number as written, never through a floating-point value.
        val rate = (entry["rate"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBigDecimalOrNull()
        val date = entry.text("date")
        if (rate == null || rate.signum() <= 0 || date == null) null else code to DayRate(rate, date)
    }.toMap()
    return if (kept.isEmpty()) null else FxSnapshot(base, source, fetchedAt, kept)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Runs the call on OkHttp's threads, reading the response with [read] there; cancelling the coroutine cancels the call. */
private suspend fun <T> Call.await(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response.use(read) })
            }
        },
    )
}
