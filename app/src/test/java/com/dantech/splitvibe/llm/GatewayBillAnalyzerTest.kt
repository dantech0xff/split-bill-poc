package com.dantech.splitvibe.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.PlainCipher
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import com.dantech.splitvibe.ocr.OcrLine
import com.dantech.splitvibe.ocr.OcrResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The app's side of the AI proxy's contract, against a local server that answers as the proxy does. */
@RunWith(AndroidJUnit4::class)
class GatewayBillAnalyzerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var proxy: TestProxy
    private val ocr: OcrResult by lazy { Fixtures.ocr("sample_bill_ocr.json") }

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `sends the request the proxy's contract describes and reads the sample bill from its answer`() = runBlocking {
        server.enqueue(Fixtures.proxyAnswer())

        val analysis = proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") as BillAnalysis.Ready

        assertEquals(902_664L, analysis.bill.totalMinor)
        assertEquals(7, analysis.bill.items.size)
        assertEquals(
            CallsLeft(limit = 3, used = 1, remaining = 2, period = "trial", resetsAt = null),
            proxy.account.account.value.callsLeft,
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/bill-analysis", request.requestUrl?.encodedPath)
        assertEquals(proxy.account.account.value.installId, request.getHeader(ProxyApi.INSTALL_HEADER))
        assertEquals(TestProxy.APP_VERSION.toString(), request.getHeader(ProxyApi.VERSION_HEADER))
        assertNull(request.getHeader("Authorization"))
        // The same body the proxy's own tests send, apart from the id each read makes for itself.
        val sent = bodyOf(request)
        val expected = Json.parseToJsonElement(Fixtures.text("sample_bill_request.json")).jsonObject
        assertEquals(JsonObject(expected + ("requestId" to sent.getValue("requestId"))), sent)
        assertTrue(UUID.matches(sent.getValue("requestId").jsonPrimitive.content))
    }

    @Test
    fun `skips wait lines, blank lines and lines it does not know`() = runBlocking {
        val answer = Fixtures.proxyAnswer().getBody()!!.readUtf8().lines().first { it.contains("\"result\"") }
        server.enqueue(ndjson("""{"type":"wait"}""", "", """{"type":"progress","share":0.5}""", """{"type":"wait"}""", answer))

        val analysis = proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND")

        assertTrue(analysis is BillAnalysis.Ready)
    }

    @Test
    fun `turns each refusal of the proxy into a kind and a message the person can act on`() = runBlocking {
        val cases = listOf(
            Case(
                refusal(429, """{"error":"quota_exhausted","usage":$TRIAL_USED_UP}"""),
                Kind.QUOTA_EXHAUSTED,
                "You have used your 3 free reads",
            ),
            Case(
                refusal(429, """{"error":"quota_exhausted","usage":$MONTH_USED_UP}"""),
                Kind.QUOTA_EXHAUSTED,
                "They come back on 1 December",
            ),
            Case(refusal(401, """{"error":"session"}"""), Kind.SIGN_IN_EXPIRED, "Sign in again"),
            Case(refusal(503, """{"error":"ai_off"}"""), Kind.AI_OFF, "switched off"),
            Case(refusal(426, """{"error":"update_required"}"""), Kind.UPDATE_REQUIRED, "Update Split Vibe"),
            Case(refusal(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "120"), Kind.RATE_LIMITED, "about 2 minutes"),
            Case(refusal(429, """{"error":"spend_limited"}""").setHeader("Retry-After", "7200"), Kind.RATE_LIMITED, "about 2 hours"),
            Case(refusal(429, """{"error":"busy"}""").setHeader("Retry-After", "30"), Kind.BUSY, "in a minute"),
            Case(refusal(429, """{"error":"retry_limit"}"""), Kind.SERVICE_ERROR, "did not work after several tries"),
            Case(refusal(400, """{"error":"input"}"""), Kind.BAD_REQUEST, "could not be sent"),
            Case(refusal(401, """{"error":"install"}"""), Kind.BAD_REQUEST, "could not be sent"),
            Case(refusal(503, """{"error":"not_configured"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(refusal(500, """{"error":"internal"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(refusal(500, """{"error":{"code":"internal"}}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"), Kind.SERVICE_ERROR, "server had a problem"),
            Case(ndjson("""{"type":"wait"}""", """{"type":"error","code":"refused","usage":$ONE_USED}"""), Kind.REFUSED, "declined"),
            Case(ndjson("""{"type":"error","code":"too_long","usage":$ONE_USED}"""), Kind.BAD_RESPONSE, "too much text"),
            Case(ndjson("""{"type":"error","code":"bad_response","usage":$ONE_USED}"""), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"error","code":"timeout"}"""), Kind.TIMEOUT, "took too long"),
            Case(ndjson("""{"type":"error","code":"busy"}"""), Kind.BUSY, "busy"),
            Case(ndjson("""{"type":"error","code":"upstream"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(ndjson("""{"type":"wait"}""", "not json"), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"error","code":{"name":"refused"}}"""), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"result","answer":"Read 7 items.","usage":$ONE_USED}"""), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"result","answer":{"status":"ok","items":"seven"}}"""), Kind.BAD_RESPONSE, "not in the expected format"),
        )

        for (case in cases) {
            server.enqueue(case.reply)
            val failure = failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }
            assertEquals(case.toString(), case.kind, failure.kind)
            assertTrue("${failure.message} should say \"${case.says}\"", failure.message.orEmpty().contains(case.says))
        }
    }

    @Test
    fun `keeps the calls left that come with an answer the model gave but could not finish`() = runBlocking {
        server.enqueue(ndjson("""{"type":"error","code":"refused","usage":$ONE_USED}"""))

        failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }

        assertEquals(2, proxy.account.account.value.callsLeft?.remaining)
    }

    @Test
    fun `an answer that stops before its last line is a network failure`() = runBlocking {
        server.enqueue(ndjson("""{"type":"wait"}"""))

        assertEquals(Kind.NETWORK, failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }.kind)
    }

    @Test
    fun `an answer that falls silent after it started is a timeout`() = runBlocking {
        val impatient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val api = ProxyApi(server.url("/").toString(), proxy.account, appVersion = 1, client = impatient)
        server.enqueue(ndjson("""{"type":"wait"}""").setBodyDelay(2, TimeUnit.SECONDS))

        val failure = failureOf { GatewayBillAnalyzer(api, proxy.account, canSignIn = true).analyze(listOf(ocr), INSTRUCTION, "VND") }

        assertEquals(Kind.TIMEOUT, failure.kind)
    }

    @Test
    fun `a server that never starts to answer is a network failure, not a slow read`() = runBlocking {
        val impatient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val api = ProxyApi(server.url("/").toString(), proxy.account, appVersion = 1, client = impatient)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val failure = failureOf { GatewayBillAnalyzer(api, proxy.account, canSignIn = true).analyze(listOf(ocr), INSTRUCTION, "VND") }

        assertEquals(Kind.NETWORK, failure.kind)
    }

    @Test
    fun `a session the proxy refuses ends the sign-in, and the next read is not sent as the install`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        server.enqueue(refusal(401, """{"error":"session"}"""))

        assertEquals(Kind.SIGN_IN_EXPIRED, failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }.kind)
        assertEquals(Kind.SIGN_IN_EXPIRED, failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }.kind)

        assertEquals("Bearer signed-session", server.takeRequest().getHeader("Authorization"))
        assertEquals("the second read stops on the phone", 1, server.requestCount)
        assertTrue(proxy.account.account.value.signInExpired)
    }

    @Test
    fun `a session that has run out stops the read on the phone`() = runBlocking {
        var now = 1_000_000L
        val expiring = TestProxy(server, context, clock = { now })
        expiring.account.saveSession(Session("signed-session", expiresAt = now + 60_000))
        now += 60_000

        val failure = failureOf { expiring.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }

        assertEquals(Kind.SIGN_IN_EXPIRED, failure.kind)
        assertTrue(failure.message.orEmpty().contains("Sign in again"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `reading the same bill again after a failure keeps the request id, so it is not charged twice`() = runBlocking {
        val failures = listOf(
            refusal(500, """{"error":"internal"}"""),
            // The answer was cut off after the model had started.
            ndjson("""{"type":"wait"}"""),
            // The model answered, so the call was charged, but the answer could not be used.
            ndjson("""{"type":"wait"}""", """{"type":"error","code":"bad_response","usage":$ONE_USED}"""),
            ndjson("""{"type":"result","answer":{"status":"ok","items":"seven"},"usage":$ONE_USED}"""),
        )
        val ids = mutableListOf<String>()
        for (failure in failures) {
            server.enqueue(failure)
            server.enqueue(Fixtures.proxyAnswer())

            failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }
            proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND")

            val failed = requestIdOf(server.takeRequest())
            assertEquals("the read after ${failure.status} is the same request", failed, requestIdOf(server.takeRequest()))
            ids += failed
        }
        server.enqueue(Fixtures.proxyAnswer())
        proxy.analyzer.analyze(listOf(ocr), "Split it four ways", "VND")
        ids += requestIdOf(server.takeRequest())

        assertEquals("a read that worked is not run again, and another instruction is another request", ids.size, ids.toSet().size)
    }

    @Test
    fun `a read that was cancelled keeps its request id for the next try of the same bill`() = runBlocking {
        // The model is still working when the person cancels.
        server.enqueue(ndjson("""{"type":"wait"}""").setBodyDelay(3, TimeUnit.SECONDS))
        server.enqueue(Fixtures.proxyAnswer())

        val read = launch(Dispatchers.IO) { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }
        val cancelled = server.takeRequest()
        read.cancelAndJoin()
        proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND")

        assertEquals(requestIdOf(cancelled), requestIdOf(server.takeRequest()))
    }

    @Test
    fun `a request the proxy cannot take again under its id gets a new one`() = runBlocking {
        val refusals = listOf(
            refusal(429, """{"error":"retry_limit"}"""),
            // Also what the proxy says when the id was first used with a prompt that has changed since.
            refusal(400, """{"error":"input"}"""),
        )
        for (refused in refusals) {
            server.enqueue(refused)
            server.enqueue(Fixtures.proxyAnswer())

            failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }
            proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND")

            assertNotEquals(refused.status, requestIdOf(server.takeRequest()), requestIdOf(server.takeRequest()))
        }
    }

    @Test
    fun `a bill with more text than the proxy takes is not sent`() = runBlocking {
        val rows = List(300) { row -> OcrLine.upright("Item $row ${"x".repeat(80)}", 10f, row * 40f, 990f, row * 40f + 30f) }
        val long = OcrResult(imageWidth = 1000, imageHeight = 12_000, lines = rows)
        assertTrue(OcrLayoutEncoder.encode(long).length > 20_000)

        val failure = failureOf { proxy.analyzer.analyze(listOf(long), INSTRUCTION, "VND") }

        assertEquals(Kind.BAD_REQUEST, failure.kind)
        assertTrue(failure.message.orEmpty().contains("too much text"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the pages of one bill are sent as one document, each page numbered`() = runBlocking {
        server.enqueue(Fixtures.proxyAnswer())

        proxy.analyzer.analyze(listOf(ocr, ocr), INSTRUCTION, "VND")

        val sent = (bodyOf(server.takeRequest()).getValue("ocrDocument") as JsonPrimitive).content
        assertEquals(OcrLayoutEncoder.encode(listOf(ocr, ocr)), sent)
        assertTrue(sent, sent.startsWith("<bill_ocr pages=\"2\">"))
    }

    @Test
    fun `pages that hold more text together than the proxy takes ask for fewer pages`() = runBlocking {
        val rows = List(150) { row -> OcrLine.upright("Item $row ${"x".repeat(80)}", 10f, row * 40f, 990f, row * 40f + 30f) }
        val page = OcrResult(imageWidth = 1000, imageHeight = 6_000, lines = rows)
        assertTrue(OcrLayoutEncoder.encode(page).length < 20_000)

        val failure = failureOf { proxy.analyzer.analyze(listOf(page, page), INSTRUCTION, "VND") }

        assertEquals(Kind.BAD_REQUEST, failure.kind)
        assertTrue(failure.message.orEmpty().contains("fewer pages"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a build that cannot sign in does not tell the person to sign in`() = runBlocking {
        val analyzer = GatewayBillAnalyzer(proxy.api, proxy.account, canSignIn = false)
        server.enqueue(refusal(429, """{"error":"quota_exhausted","usage":$TRIAL_USED_UP}"""))

        val failure = failureOf { analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }

        assertEquals(Kind.QUOTA_EXHAUSTED, failure.kind)
        assertEquals("You have used your 3 free reads.", failure.message)
    }

    @Test
    fun `AI is shown as off when the proxy says so, and on again when it answers`() = runBlocking {
        server.enqueue(refusal(503, """{"error":"ai_off"}"""))
        server.enqueue(Fixtures.proxyAnswer())

        failureOf { proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }
        assertEquals(AiStatus.OFF, proxy.account.account.value.ai)
        proxy.analyzer.analyze(listOf(ocr), INSTRUCTION, "VND")
        assertEquals(AiStatus.AVAILABLE, proxy.account.account.value.ai)
    }

    @Test
    fun `a build without the proxy's address sends nothing`() = runBlocking {
        val account = AccountStore(context, proxySetUp = false, cipher = PlainCipher)
        val analyzer = GatewayBillAnalyzer(ProxyApi("", account, appVersion = 1), account, canSignIn = false)

        val failure = failureOf { analyzer.analyze(listOf(ocr), INSTRUCTION, "VND") }

        assertEquals(Kind.AI_OFF, failure.kind)
        assertEquals(AiStatus.NOT_SET_UP.notice, failure.message)
        assertEquals(AiStatus.NOT_SET_UP, account.account.value.ai)
        assertEquals(0, server.requestCount)
    }

    private data class Case(val reply: MockResponse, val kind: Kind, val says: String) {
        override fun toString() = "${reply.status} ${reply.getBody()?.clone()?.readUtf8()} -> $kind"
    }

    private fun refusal(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private fun ndjson(vararg lines: String): MockResponse =
        MockResponse().setHeader("content-type", "application/x-ndjson; charset=utf-8").setBody(lines.joinToString("\n", postfix = "\n"))

    private fun bodyOf(request: RecordedRequest): JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private fun requestIdOf(request: RecordedRequest): String = (bodyOf(request).getValue("requestId") as JsonPrimitive).content

    private suspend fun failureOf(block: suspend () -> Unit): BillAnalyzerException {
        try {
            block()
        } catch (e: BillAnalyzerException) {
            return e
        }
        fail("Expected the read to fail")
        error("unreachable")
    }

    private companion object {
        const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
        const val TRIAL_USED_UP = """{"limit":3,"used":3,"remaining":0,"period":"trial","resetsAt":null}"""
        const val MONTH_USED_UP = """{"limit":10,"used":10,"remaining":0,"period":"2026-11","resetsAt":1796083200000}"""
        const val ONE_USED = """{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null}"""
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
