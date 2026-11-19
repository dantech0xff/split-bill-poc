package com.dantech.splitvibe.e2e

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * Split Vibe's AI proxy as the journeys meet it, answering from a MockWebServer inside the test on
 * the phone. Each route answers the way the Worker in proxy/ does (docs/proxy.md), with the same
 * status and error codes:
 * - `POST /v1/bill-analysis` streams a wait line, then the result line with the answer Claude gave
 *   for the sample bill when it was recorded, whatever the bill, and the calls left after it;
 * - `GET /v1/usage` gives the calls left, and `POST /v1/session` signs any ID token in as [SESSION];
 * - `GET /v1/fx-rates` gives a few of the day's rates against the euro;
 * - `POST` and `GET /v1/requests` keep and list the payment requests of [SESSION].
 *
 * Any other path is 404 `path`, as on the proxy. Every request is kept, in order, in [received].
 */
class FakeProxy : Dispatcher() {

    /** One request the app sent, with its body as text. */
    class Call(val method: String, val path: String, private val headers: Headers, val body: String) {
        fun header(name: String): String? = headers[name]

        /** The body as a JSON object, or null when it is not one. */
        val json: JsonObject? by lazy { objectOf(body) }
    }

    private val calls = CopyOnWriteArrayList<Call>()

    /** The payment requests made here, as their creditor sees them, by id, each with its link. Guarded by itself. */
    private val requests = LinkedHashMap<String, Pair<JsonObject, String>>()

    private val trialReads = AtomicInteger()
    private val accountReads = AtomicInteger()

    /** Every request the app sent, in order. */
    val received: List<Call> get() = calls.toList()

    /** The requests the app sent to [path] with [method], in order. */
    fun callsTo(method: String, path: String): List<Call> = calls.filter { it.method == method && it.path == path }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val call = Call(request.method.orEmpty(), request.requestUrl?.encodedPath.orEmpty(), request.headers, request.body.readUtf8())
        calls += call
        return when ("${call.method} ${call.path}") {
            "POST /v1/bill-analysis" -> billAnalysis(call)
            "GET /v1/usage" -> usage(call)
            "POST /v1/session" -> session(call)
            "GET /v1/fx-rates" -> fxRates()
            "POST /v1/requests" -> signedIn(call) { createRequest(call, host = request.getHeader("Host").orEmpty()) }
            "GET /v1/requests" -> signedIn(call) { listRequests() }
            else -> refusal(404, "path")
        }
    }

    /** A wait line at once, then the recorded answer, as the proxy streams a bill read. */
    private fun billAnalysis(call: Call): MockResponse {
        callerRefusal(call)?.let { return it }
        val body = call.json ?: return refusal(400, "input")
        val document = body.text("ocrDocument")
        val instruction = body.text("instruction")
        val readable = body.whole("schemaVersion") == 1L &&
            body.text("requestId")?.matches(UUID_TEXT) == true &&
            document != null &&
            document.length <= MAX_DOCUMENT_CHARS &&
            document.startsWith("<bill_ocr") &&
            document.endsWith("</bill_ocr>") &&
            instruction != null &&
            instruction.length <= MAX_INSTRUCTION_CHARS &&
            body.text("localCurrency")?.matches(CURRENCY_CODE) == true
        if (!readable) return refusal(400, "input")
        val signedIn = isSignedIn(call)
        val used = (if (signedIn) accountReads else trialReads).incrementAndGet()
        return lines(
            buildJsonObject { put("type", "wait") },
            buildJsonObject {
                put("type", "result")
                put("answer", recordedAnswer)
                put("usage", callsLeft(signedIn, used))
            },
        )
    }

    private fun usage(call: Call): MockResponse {
        callerRefusal(call)?.let { return it }
        val signedIn = isSignedIn(call)
        val body = buildJsonObject {
            put("usage", callsLeft(signedIn, used = (if (signedIn) accountReads else trialReads).get()))
            put("aiEnabled", true)
            if (signedIn) put("purchaser", PURCHASER)
        }
        return json(200, body)
    }

    /** Any ID token signs in: checking it with Google is the real proxy's part. */
    private fun session(call: Call): MockResponse {
        if (call.json?.text("idToken").isNullOrEmpty()) return refusal(400, "input")
        val body = buildJsonObject {
            put("session", SESSION)
            put("expiresAt", System.currentTimeMillis() + SESSION_DAYS * DAY_MS)
            put("usage", callsLeft(signedIn = true, used = accountReads.get()))
            put("purchaser", PURCHASER)
        }
        return json(200, body)
    }

    private fun fxRates(): MockResponse {
        val day = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()
        val rates = buildJsonObject {
            EURO_RATES.forEach { (code, rate) ->
                put(
                    code,
                    buildJsonObject {
                        put("rate", JsonPrimitive(BigDecimal(rate)))
                        put("date", day)
                    },
                )
            }
        }
        val body = buildJsonObject {
            put("base", "EUR")
            put("source", "Frankfurter")
            put("fetchedAt", System.currentTimeMillis())
            put("rates", rates)
        }
        return json(200, body).setHeader("cache-control", "public, max-age=3600")
    }

    /**
     * `POST /v1/requests`: keeps the request and answers 201 with it and its link; the same request
     * again answers as the first time did. The fake keeps no groups, so a request in one is 404, as
     * for a group the caller is not a member of.
     */
    private fun createRequest(call: Call, host: String): MockResponse {
        val body = call.json ?: return refusal(400, "input")
        if (!body.isMissing("groupId")) return refusal(404, "not_found")
        val id = body.text("id")?.takeIf { it.matches(UUID_TEXT) } ?: return refusal(400, "input")
        val asked = requestViewOf(id, body) ?: return refusal(400, "input")
        val (view, url) = synchronized(requests) { requests.getOrPut(id) { asked to "https://$host/r/#${linkToken()}" } }
        val answer = buildJsonObject {
            put("request", view)
            put("url", url)
        }
        return json(201, answer)
    }

    /** `GET /v1/requests`: the caller's requests, newest first. */
    private fun listRequests(): MockResponse {
        val views = synchronized(requests) { requests.values.map { it.first } }.sortedByDescending { it.whole("createdAt") }
        return json(200, buildJsonObject { put("requests", JsonArray(views)) })
    }

    /** A route on shared data: only [SESSION] gets in, as only a session that checks out does on the proxy. */
    private fun signedIn(call: Call, answer: () -> MockResponse): MockResponse = if (isSignedIn(call)) answer() else refusal(401, "session")

    /**
     * Why the proxy turns away the caller of a route that counts calls, or null when it takes them:
     * a session is [SESSION] or refused, never read as the install, and without one the install has
     * to name itself.
     */
    private fun callerRefusal(call: Call): MockResponse? = when {
        call.header("Authorization") != null -> if (isSignedIn(call)) null else refusal(401, "session")
        call.header(INSTALL_HEADER)?.lowercase()?.matches(UUID_TEXT) != true -> refusal(401, "install")
        else -> null
    }

    private fun isSignedIn(call: Call): Boolean = call.header("Authorization")?.trim() == "Bearer $SESSION"

    companion object {
        /** The only session the fake takes. A test keeps it in the app's own store to be signed in. */
        const val SESSION = "e2e-session-0123456789abcdef"

        /** The header with the id an install made for itself. */
        const val INSTALL_HEADER = "X-SplitVibe-Install"

        /** The signed-in account's id at RevenueCat, 64 hex digits, as the proxy hands it out. */
        private val PURCHASER = "5e".repeat(32)

        private const val TRIAL_CALLS = 3
        private const val MONTHLY_CALLS = 10
        private const val MAX_DOCUMENT_CHARS = 20_000
        private const val MAX_INSTRUCTION_CHARS = 1_000
        private const val SESSION_DAYS = 30
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** How much of each currency one euro buys, near the rates of November 2026. */
        private val EURO_RATES = mapOf(
            "EUR" to "1",
            "USD" to "1.16",
            "GBP" to "0.88",
            "JPY" to "179.5",
            "INR" to "102.4",
            "THB" to "37.6",
            "VND" to "30510",
        )

        /** What Claude answered for the bundled sample bill, recorded once and shared with the local tests. */
        private val recordedAnswer: JsonObject by lazy {
            val answer = checkNotNull(FakeProxy::class.java.classLoader?.getResource("fixtures/sample_bill_answer.json")) {
                "Missing the recorded answer in src/sharedTest/resources/fixtures"
            }
            Json.parseToJsonElement(answer.readText()).jsonObject
        }

        /**
         * The calls left after [used] bill reads, as the proxy's `usage` says it: an install's three,
         * once, or a Google account's ten of the calendar month in UTC.
         */
        private fun callsLeft(signedIn: Boolean, used: Int): JsonObject {
            val limit = if (signedIn) MONTHLY_CALLS else TRIAL_CALLS
            val month = YearMonth.now(ZoneOffset.UTC)
            return buildJsonObject {
                put("limit", limit)
                put("used", used)
                put("remaining", (limit - used).coerceAtLeast(0))
                if (signedIn) {
                    put("period", month.toString())
                    put("resetsAt", month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                    put("plan", "free")
                } else {
                    put("period", "trial")
                    put("resetsAt", JsonNull)
                    put("plan", "trial")
                }
            }
        }

        /** 128 random bits in base64url, as the proxy's link tokens are. */
        private fun linkToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Random.nextBytes(16))
    }
}

/**
 * The payment request [body] asks for under [id], as its creditor sees it, or null when the proxy
 * would not take it: an amount above 0, a currency, the two names of 1 to 60 characters, a title
 * of at most 80, one payment method, and reminders every 0, 1, 3 or 7 days.
 */
private fun requestViewOf(id: String, body: JsonObject): JsonObject? {
    val amount = body.whole("amountMinor")?.takeIf { it in 1..MAX_AMOUNT_MINOR } ?: return null
    val currency = (body["currency"] as? JsonObject)?.takeIf {
        it.text("code")?.matches(CURRENCY_CODE) == true && it.whole("fractionDigits") in 0L..MAX_FRACTION_DIGITS
    } ?: return null
    val payer = body.name("payerName") ?: return null
    val creditor = body.name("creditorName") ?: return null
    val method = body["method"] as? JsonObject ?: return null
    val title = when {
        body.isMissing("title") -> ""
        else -> body.text("title")?.trim()?.takeIf { it.length <= MAX_TITLE } ?: return null
    }
    val billId = when {
        body.isMissing("billId") -> null
        else -> body.text("billId")?.takeIf { it.matches(UUID_TEXT) } ?: return null
    }
    val reminders = when {
        body.isMissing("remindEveryDays") -> 0L
        else -> body.whole("remindEveryDays")?.takeIf(REMIND_DAYS::contains) ?: return null
    }
    return buildJsonObject {
        put("id", id)
        put("role", "creditor")
        put("amountMinor", amount)
        put("currency", currency)
        put("payerName", payer)
        put("creditorName", creditor)
        put("title", title.ifEmpty { null })
        put("method", method)
        put("groupId", JsonNull)
        put("toMemberId", JsonNull)
        put("creditorMemberId", JsonNull)
        put("billId", billId)
        put("createdAt", System.currentTimeMillis())
        put("reportedPaidAt", JsonNull)
        put("confirmedPaidAt", JsonNull)
        put("remindEveryDays", reminders)
        put("debtorBound", false)
    }
}

private const val MAX_AMOUNT_MINOR = 1_000_000_000_000_000L
private const val MAX_FRACTION_DIGITS = 3L
private const val MAX_NAME = 60
private const val MAX_TITLE = 80
private val REMIND_DAYS = listOf(0L, 1L, 3L, 7L)
private val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val CURRENCY_CODE = Regex("[A-Z]{3}")

private fun json(status: Int, body: JsonObject): MockResponse = MockResponse()
    .setResponseCode(status)
    .setHeader("content-type", "application/json")
    .setHeader("cache-control", "no-store")
    .setBody(body.toString())

/** The proxy's refusal: `{"error": code}`. */
private fun refusal(status: Int, code: String): MockResponse = json(status, buildJsonObject { put("error", code) })

/** A 200 answer of JSON lines, the way the proxy streams the answers of the model. */
private fun lines(vararg lines: JsonObject): MockResponse = MockResponse()
    .setHeader("content-type", "application/x-ndjson; charset=utf-8")
    .setHeader("cache-control", "no-store, no-transform")
    .setBody(lines.joinToString(separator = "") { "$it\n" })

private fun objectOf(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (e: SerializationException) {
    null
}

private fun JsonObject.isMissing(key: String): Boolean = this[key] == null || this[key] == JsonNull

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A name as the proxy takes it: text of 1 to 60 characters once trimmed. */
private fun JsonObject.name(key: String): String? = text(key)?.trim()?.takeIf { it.codePointCount(0, it.length) in 1..MAX_NAME }

/** A whole number written as a JSON number, never as text. */
private fun JsonObject.whole(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
