package com.dantech.splitvibe.data.proxy

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
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

/**
 * A refusal or failure the AI proxy reported, before an answer or in place of one. [status] is 200
 * when it came as the error line of a streamed answer. [callsLeft] is set when the proxy said how
 * many calls are left after this request.
 */
class ProxyException(val status: Int, val code: String, val retryAfterSeconds: Long?, val callsLeft: CallsLeft?) :
    Exception("The AI proxy answered $status with $code")

/** The model's answer to a bill read, and the calls left after it. */
data class BillReply(val answer: JsonObject, val callsLeft: CallsLeft?)

/** What `GET /v1/usage` says. */
data class Usage(val callsLeft: CallsLeft, val aiEnabled: Boolean)

/**
 * The app's side of the AI proxy's contract (see `proxy/` and `docs/proxy.md`). Every request says
 * which install it comes from and which version of the app, and carries the session once someone
 * has signed in. Cancelling the coroutine of a request cancels the request.
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
        send(post("v1/session", buildJsonObject { put("idToken", idToken) })) { response ->
            if (!response.isSuccessful) throw failureOf(response)
            val body = jsonOf(response)
            val token = body["session"]?.jsonPrimitive?.contentOrNull
            val expiresAt = body["expiresAt"]?.jsonPrimitive?.longOrNull
            if (token.isNullOrEmpty() || expiresAt == null) throw ProxyException(response.code, BAD_RESPONSE, null, null)
            Session(token, expiresAt) to body["usage"]?.let(::callsLeftOf)
        }

    /** `GET /v1/usage`: the calls left of whoever is calling, and whether AI is on. */
    suspend fun usage(): Usage = send(request("v1/usage").get().build()) { response ->
        if (!response.isSuccessful) throw failureOf(response)
        val body = jsonOf(response)
        val callsLeft = body["usage"]?.let(::callsLeftOf) ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
        Usage(callsLeft, aiEnabled = body["aiEnabled"]?.jsonPrimitive?.booleanOrNull ?: true)
    }

    /** `DELETE /v1/account`: deletes what the proxy keeps about the signed-in account. */
    suspend fun deleteAccount() = send(request("v1/account").delete().build()) { response ->
        if (!response.isSuccessful) throw failureOf(response)
    }

    private fun post(path: String, body: JsonObject): Request = request(path).post(body.toString().toRequestBody(JSON)).build()

    private fun request(path: String): Request.Builder {
        val url = checkNotNull(base) { "This build has no address for the AI proxy" }.newBuilder().addPathSegments(path).build()
        val current = account.account.value
        return Request.Builder()
            .url(url)
            .header(INSTALL_HEADER, current.installId)
            .header(VERSION_HEADER, appVersion.toString())
            .apply { account.currentSession()?.let { header("Authorization", "Bearer ${it.token}") } }
    }

    private suspend fun <T> send(request: Request, read: (Response) -> T): T = client.newCall(request).await(read)

    private fun readAnswer(response: Response): BillReply {
        val source = checkNotNull(response.body).source()
        while (true) {
            val line = source.readUtf8Line() ?: throw IOException("The answer ended before it was complete")
            if (line.isBlank()) continue
            val message = parse(line) ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
            when (message["type"]?.jsonPrimitive?.contentOrNull) {
                "result" -> {
                    val answer = message["answer"] as? JsonObject ?: throw ProxyException(response.code, BAD_RESPONSE, null, null)
                    return BillReply(answer, message["usage"]?.let(::callsLeftOf))
                }

                "error" -> throw ProxyException(
                    status = response.code,
                    code = message["code"]?.jsonPrimitive?.contentOrNull ?: BAD_RESPONSE,
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
            code = body?.get("error")?.jsonPrimitive?.contentOrNull ?: "http_${response.code}",
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

        private val JSON = "application/json".toMediaType()

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
        limit = usage["limit"]?.jsonPrimitive?.intOrNull ?: return null,
        used = usage["used"]?.jsonPrimitive?.intOrNull ?: return null,
        remaining = usage["remaining"]?.jsonPrimitive?.intOrNull ?: return null,
        period = usage["period"]?.jsonPrimitive?.contentOrNull ?: return null,
        resetsAt = usage["resetsAt"]?.jsonPrimitive?.longOrNull,
    )
}

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
