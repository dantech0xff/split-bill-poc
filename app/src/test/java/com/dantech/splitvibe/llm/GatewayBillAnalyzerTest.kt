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
import com.dantech.splitvibe.ocr.OcrResult
import java.util.concurrent.TimeUnit
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

        val analysis = proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") as BillAnalysis.Ready

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

        val analysis = proxy.analyzer.analyze(ocr, INSTRUCTION, "VND")

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
            Case(refusal(400, """{"error":"input"}"""), Kind.BAD_REQUEST, "could not be sent"),
            Case(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"), Kind.SERVICE_ERROR, "server had a problem"),
            Case(ndjson("""{"type":"wait"}""", """{"type":"error","code":"refused","usage":$ONE_USED}"""), Kind.REFUSED, "declined"),
            Case(ndjson("""{"type":"error","code":"too_long","usage":$ONE_USED}"""), Kind.BAD_RESPONSE, "too long"),
            Case(ndjson("""{"type":"error","code":"bad_response","usage":$ONE_USED}"""), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"error","code":"timeout"}"""), Kind.TIMEOUT, "took too long"),
            Case(ndjson("""{"type":"error","code":"busy"}"""), Kind.BUSY, "busy"),
            Case(ndjson("""{"type":"error","code":"upstream"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(ndjson("""{"type":"wait"}""", "not json"), Kind.BAD_RESPONSE, "could not be read"),
        )

        for (case in cases) {
            server.enqueue(case.reply)
            val failure = failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }
            assertEquals(case.toString(), case.kind, failure.kind)
            assertTrue("${failure.message} should say \"${case.says}\"", failure.message.orEmpty().contains(case.says))
        }
    }

    @Test
    fun `keeps the calls left that come with an answer the model gave but could not finish`() = runBlocking {
        server.enqueue(ndjson("""{"type":"error","code":"refused","usage":$ONE_USED}"""))

        failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }

        assertEquals(2, proxy.account.account.value.callsLeft?.remaining)
    }

    @Test
    fun `an answer that stops before its last line is a network failure`() = runBlocking {
        server.enqueue(ndjson("""{"type":"wait"}"""))

        assertEquals(Kind.NETWORK, failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }.kind)
    }

    @Test
    fun `a connection that falls silent is a timeout`() = runBlocking {
        val impatient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val api = ProxyApi(server.url("/").toString(), proxy.account, appVersion = 1, client = impatient)
        server.enqueue(ndjson("""{"type":"wait"}""").setBodyDelay(2, TimeUnit.SECONDS))

        assertEquals(Kind.TIMEOUT, failureOf { GatewayBillAnalyzer(api, proxy.account).analyze(ocr, INSTRUCTION, "VND") }.kind)
    }

    @Test
    fun `sends the session once someone has signed in, and forgets it when the proxy refuses it`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        server.enqueue(refusal(401, """{"error":"session"}"""))
        server.enqueue(Fixtures.proxyAnswer())

        failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }
        proxy.analyzer.analyze(ocr, INSTRUCTION, "VND")

        assertEquals("Bearer signed-session", server.takeRequest().getHeader("Authorization"))
        assertNull(proxy.account.account.value.session)
        assertNull("the next read goes out as the install", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `reading the same bill again after a failure keeps the request id, so it is not charged twice`() = runBlocking {
        server.enqueue(refusal(500, """{"error":"internal"}"""))
        server.enqueue(Fixtures.proxyAnswer())
        server.enqueue(Fixtures.proxyAnswer())
        server.enqueue(Fixtures.proxyAnswer())

        failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }
        proxy.analyzer.analyze(ocr, INSTRUCTION, "VND")
        proxy.analyzer.analyze(ocr, INSTRUCTION, "VND")
        proxy.analyzer.analyze(ocr, "Split it four ways", "VND")

        val ids = List(4) { requestIdOf(server.takeRequest()) }
        assertEquals("the read after the failure is the same request", ids[0], ids[1])
        assertNotEquals("a read that worked is not run again", ids[1], ids[2])
        assertNotEquals(ids[2], ids[3])
    }

    @Test
    fun `a request that has run as often as the proxy allows gets a new id`() = runBlocking {
        server.enqueue(refusal(429, """{"error":"retry_limit"}"""))
        server.enqueue(Fixtures.proxyAnswer())

        failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }
        proxy.analyzer.analyze(ocr, INSTRUCTION, "VND")

        assertNotEquals(requestIdOf(server.takeRequest()), requestIdOf(server.takeRequest()))
    }

    @Test
    fun `AI is shown as off when the proxy says so, and on again when it answers`() = runBlocking {
        server.enqueue(refusal(503, """{"error":"ai_off"}"""))
        server.enqueue(Fixtures.proxyAnswer())

        failureOf { proxy.analyzer.analyze(ocr, INSTRUCTION, "VND") }
        assertEquals(AiStatus.OFF, proxy.account.account.value.ai)
        proxy.analyzer.analyze(ocr, INSTRUCTION, "VND")
        assertEquals(AiStatus.AVAILABLE, proxy.account.account.value.ai)
    }

    @Test
    fun `a build without the proxy's address sends nothing`() = runBlocking {
        val account = AccountStore(context, proxySetUp = false, cipher = PlainCipher)
        val analyzer = GatewayBillAnalyzer(ProxyApi("", account, appVersion = 1), account)

        val failure = failureOf { analyzer.analyze(ocr, INSTRUCTION, "VND") }

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
