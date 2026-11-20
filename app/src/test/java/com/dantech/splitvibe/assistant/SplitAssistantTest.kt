package com.dantech.splitvibe.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.PlainCipher
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.SplitEdit
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.english
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.llm.text
import java.math.BigDecimal
import java.util.Locale
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The app's side of `POST /v1/split-revision`, against a local server that answers as the proxy does. */
@RunWith(AndroidJUnit4::class)
class SplitAssistantTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var proxy: TestProxy
    private lateinit var assistant: SplitAssistant

    /** The bill of the contract's example: three beers for 90,000 and a service charge of 14,000, on a printed total of 154,000. */
    private val bill = Bill(
        merchant = "Pho 24",
        date = "2026-11-16",
        currency = BillCurrency("VND", 0),
        items = listOf(BillItem("i1", "Bia", BigDecimal(3), 30_000, 90_000)),
        charges = listOf(BillCharge("c1", "Service charge", ChargeKind.SERVICE_CHARGE, 14_000)),
        printedTotalMinor = 154_000,
    )

    /** The user and Lan; the beer is the user's alone, and the user paid. */
    private val plan = SplitPlan()
        .withParticipantAdded("Lan")
        .withMode(SplitMode.BY_ITEM, bill)
        .withItemSharers("i1", setOf(ME_ID))
        .withBill(bill)

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true, sellsPro = false)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `sends the request the contract describes and reads the edits from the streamed answer`() = runBlocking {
        server.enqueue(ndjson("""{"type":"wait"}""", "", """{"type":"progress","share":0.5}""", result(ANSWER, usage = ONE_CHANGE_USED)))

        val revision = assistant.revise("  Lan không uống bia ", bill, plan, meName = "Dana")

        assertEquals(listOf<SplitEdit>(SplitEdit.ItemSharers("i1", listOf(ME_ID))), revision.edits)
        assertEquals("Lan no longer shares the beer.", revision.explanation)
        assertNull(revision.question)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/split-revision", request.requestUrl?.encodedPath)
        assertEquals(proxy.account.account.value.installId, request.getHeader(ProxyApi.INSTALL_HEADER))
        assertEquals(TestProxy.APP_VERSION.toString(), request.getHeader(ProxyApi.VERSION_HEADER))
        assertNull(request.getHeader("Authorization"))
        // The contract's example, with the id this request made for itself.
        val sent = bodyOf(request)
        assertEquals(JsonObject(CONTRACT_BODY + ("requestId" to sent.getValue("requestId"))), sent)
        assertTrue(UUID.matches(sent.getValue("requestId").jsonPrimitive.content))
    }

    @Test
    fun `the calls left come with decimals, and a change takes a fifth of one`() = runBlocking {
        server.enqueue(ndjson(result(ANSWER, usage = ONE_CHANGE_USED)))

        assistant.revise("Lan không uống bia", bill, plan, meName = "Dana")

        val calls = checkNotNull(proxy.account.account.value.callsLeft)
        assertEquals(CallsLeft(BigDecimal(3), BigDecimal("0.2"), BigDecimal("2.8"), "trial", resetsAt = null), calls)
        assertEquals("2.8", CallsLeft.text(calls.remaining))
        assertEquals(AiStatus.AVAILABLE, proxy.account.account.value.ai)
    }

    @Test
    fun `turns each refusal of the proxy into a kind and a message the person can act on`() = runBlocking {
        val cases = listOf(
            Case(refusal(429, """{"error":"quota_exhausted","usage":$TRIAL_USED_UP}"""), Kind.QUOTA_EXHAUSTED, "used your 3 free calls."),
            Case(refusal(429, """{"error":"quota_exhausted","usage":$MONTH_USED_UP}"""), Kind.QUOTA_EXHAUSTED, "come back on December 1"),
            // Should a change ever weigh more than what a person has left.
            Case(
                refusal(429, """{"error":"quota_exhausted","usage":$TRIAL_PART_LEFT}"""),
                Kind.QUOTA_EXHAUSTED,
                "Changing a split takes 0.2 of a call, and 0.1 of your 3 free calls are left.",
            ),
            Case(refusal(401, """{"error":"session"}"""), Kind.SIGN_IN_EXPIRED, "Sign in again"),
            Case(refusal(503, """{"error":"ai_off"}"""), Kind.AI_OFF, SplitAssistant.SWITCHED_OFF.english()),
            Case(refusal(426, """{"error":"update_required"}"""), Kind.UPDATE_REQUIRED, "keep changing splits"),
            Case(
                refusal(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "120"),
                Kind.RATE_LIMITED,
                "Too many changes were asked for just now. Try again in about 2 minutes.",
            ),
            Case(refusal(429, """{"error":"spend_limited"}""").setHeader("Retry-After", "7200"), Kind.RATE_LIMITED, "about 2 hours"),
            Case(refusal(429, """{"error":"busy"}""").setHeader("Retry-After", "30"), Kind.BUSY, "in a minute"),
            Case(refusal(429, """{"error":"retry_limit"}"""), Kind.SERVICE_ERROR, "did not work after several tries"),
            // The proxy does not take this bill as it is, whatever it calls the reason.
            Case(refusal(400, """{"error":"input"}"""), Kind.BAD_REQUEST, "This bill cannot be changed this way"),
            Case(refusal(400, """{"error":"too_many_items"}"""), Kind.BAD_REQUEST, "This bill cannot be changed this way"),
            Case(refusal(401, """{"error":"install"}"""), Kind.BAD_REQUEST, "cannot be changed this way"),
            Case(refusal(503, """{"error":"not_configured"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(refusal(500, """{"error":"internal"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"), Kind.SERVICE_ERROR, "server had a problem"),
            Case(ndjson("""{"type":"wait"}""", """{"type":"error","code":"refused","usage":$ONE_CHANGE_USED}"""), Kind.REFUSED, "declined"),
            Case(ndjson("""{"type":"error","code":"too_long","usage":$ONE_CHANGE_USED}"""), Kind.BAD_RESPONSE, "fewer changes"),
            Case(ndjson("""{"type":"error","code":"bad_response","usage":$ONE_CHANGE_USED}"""), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"error","code":"timeout"}"""), Kind.TIMEOUT, "took too long"),
            Case(ndjson("""{"type":"error","code":"busy"}"""), Kind.BUSY, "busy"),
            Case(ndjson("""{"type":"error","code":"upstream"}"""), Kind.SERVICE_ERROR, "server had a problem"),
            Case(ndjson("""{"type":"wait"}""", "not json"), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"result","answer":"Lan no longer shares the beer."}"""), Kind.BAD_RESPONSE, "could not be read"),
            // Not one edit could be read, and nothing else was said.
            Case(ndjson(result(NOTHING_READABLE)), Kind.BAD_RESPONSE, "could not be read"),
            Case(ndjson("""{"type":"wait"}"""), Kind.NETWORK, "Could not reach Split Vibe's server"),
        )

        for (case in cases) {
            server.enqueue(case.reply)
            val failure = failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }
            assertEquals(case.toString(), case.kind, failure.kind)
            assertTrue("${failure.text.english()} should say \"${case.says}\"", failure.text.english().contains(case.says))
        }
    }

    @Test
    fun `what a change takes is said with the marks of the region the phone is set to when it is said`() = runBlocking {
        val before = Locale.getDefault()
        // The assistant was made on a phone set to the United States, which is then set to Vietnam.
        Locale.setDefault(Locale.forLanguageTag("vi-VN"))
        try {
            server.enqueue(refusal(429, """{"error":"quota_exhausted","usage":$TRIAL_PART_LEFT}"""))

            val failure = failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }

            val said = failure.text.english()
            assertTrue(said, said.contains("Changing a split takes 0,2 of a call, and 0,1 of your 3 free calls are left."))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `an answer that falls silent after it started is a timeout`() = runBlocking {
        val impatient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val api = ProxyApi(server.url("/").toString(), proxy.account, appVersion = 1, client = impatient)
        server.enqueue(ndjson("""{"type":"wait"}""").setBodyDelay(2, TimeUnit.SECONDS))

        val failure = failureOf {
            SplitAssistant(api, proxy.account, canSignIn = true, sellsPro = false).revise("Lan không uống bia", bill, plan, "Dana")
        }

        assertEquals(Kind.TIMEOUT, failure.kind)
    }

    @Test
    fun `the calls left that come with a refusal are kept, decimals and all`() = runBlocking {
        server.enqueue(ndjson("""{"type":"error","code":"refused","usage":$ONE_CHANGE_USED}"""))

        failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }

        assertEquals(BigDecimal("2.8"), proxy.account.account.value.callsLeft?.remaining)
    }

    @Test
    fun `asking again after a failure or a cancel is the same request, and is not charged twice`() = runBlocking {
        val failures = listOf(
            refusal(500, """{"error":"internal"}"""),
            // The answer was cut off after the model had started.
            ndjson("""{"type":"wait"}"""),
            // The model answered, so the call was charged, but the answer could not be used.
            ndjson("""{"type":"error","code":"bad_response","usage":$ONE_CHANGE_USED}"""),
            ndjson(result(NOTHING_READABLE)),
        )
        val ids = mutableListOf<String>()
        for (failure in failures) {
            server.enqueue(failure)
            server.enqueue(ndjson(result(ANSWER)))

            failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }
            assistant.revise("Lan không uống bia", bill, plan, "Dana")

            val failed = requestIdOf(server.takeRequest())
            assertEquals("the request after ${failure.status} is the same request", failed, requestIdOf(server.takeRequest()))
            ids += failed
        }
        // The model is still working when the person cancels.
        server.enqueue(ndjson("""{"type":"wait"}""").setBodyDelay(3, TimeUnit.SECONDS))
        server.enqueue(ndjson(result(ANSWER)))
        val asking = launch(Dispatchers.IO) { assistant.revise("Add a 10% tip", bill, plan, "Dana") }
        val cancelled = requestIdOf(server.takeRequest())
        asking.cancelAndJoin()
        assistant.revise("Add a 10% tip", bill, plan, "Dana")
        assertEquals(cancelled, requestIdOf(server.takeRequest()))
        ids += cancelled
        // The same sentence about a split that has changed since is another request.
        server.enqueue(ndjson(result(ANSWER)))
        assistant.revise("Add a 10% tip", bill, plan.withSharing("p1", sharing = false), "Dana")
        ids += requestIdOf(server.takeRequest())

        assertEquals("an answer that worked is not run again, and another change is another request", ids.size, ids.toSet().size)
    }

    @Test
    fun `a request the proxy cannot take again under its id gets a new one`() = runBlocking {
        val refusals = listOf(refusal(429, """{"error":"retry_limit"}"""), refusal(400, """{"error":"input"}"""))
        for (refused in refusals) {
            server.enqueue(refused)
            server.enqueue(ndjson(result(ANSWER)))

            failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }
            assistant.revise("Lan không uống bia", bill, plan, "Dana")

            assertNotEquals(refused.status, requestIdOf(server.takeRequest()), requestIdOf(server.takeRequest()))
        }
    }

    @Test
    fun `a session the proxy refuses ends the sign-in, and the next change is not sent as the install`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        server.enqueue(refusal(401, """{"error":"session"}"""))

        assertEquals(Kind.SIGN_IN_EXPIRED, failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }.kind)
        assertEquals(Kind.SIGN_IN_EXPIRED, failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }.kind)

        assertEquals("Bearer signed-session", server.takeRequest().getHeader("Authorization"))
        assertEquals("the second change stops on the phone", 1, server.requestCount)
        assertTrue(proxy.account.account.value.signInExpired)
    }

    @Test
    fun `AI is shown as off when the proxy says so, and on again when it answers`() = runBlocking {
        server.enqueue(refusal(503, """{"error":"ai_off"}"""))
        server.enqueue(ndjson(result(ANSWER)))

        failureOf { assistant.revise("Lan không uống bia", bill, plan, "Dana") }
        assertEquals(AiStatus.OFF, proxy.account.account.value.ai)
        assistant.revise("Lan không uống bia", bill, plan, "Dana")
        assertEquals(AiStatus.AVAILABLE, proxy.account.account.value.ai)
    }

    @Test
    fun `an empty or too long instruction and a build without the proxy's address send nothing`() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { assistant.revise(" \n ", bill, plan, "Dana") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { assistant.revise("a".repeat(501), bill, plan, "Dana") } }
        val account = AccountStore(context, proxySetUp = false, cipher = PlainCipher)
        val noProxy = SplitAssistant(ProxyApi("", account, appVersion = 1), account, canSignIn = false, sellsPro = false)

        val failure = failureOf { noProxy.revise("Lan không uống bia", bill, plan, "Dana") }

        assertEquals(Kind.AI_OFF, failure.kind)
        assertEquals(SplitAssistant.NOT_SET_UP, failure.text)
        assertEquals(0, server.requestCount)
    }

    private data class Case(val reply: MockResponse, val kind: Kind, val says: String) {
        override fun toString() = "${reply.status} ${reply.getBody()?.clone()?.readUtf8()} -> $kind"
    }

    private fun refusal(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private fun ndjson(vararg lines: String): MockResponse =
        MockResponse().setHeader("content-type", "application/x-ndjson; charset=utf-8").setBody(lines.joinToString("\n", postfix = "\n"))

    private fun result(answer: String, usage: String = ONE_CHANGE_USED) = """{"type":"result","answer":$answer,"usage":$usage}"""

    private fun bodyOf(request: RecordedRequest): JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private fun requestIdOf(request: RecordedRequest): String = (bodyOf(request).getValue("requestId") as JsonPrimitive).content

    private suspend fun failureOf(block: suspend () -> Unit): BillAnalyzerException {
        try {
            block()
        } catch (e: BillAnalyzerException) {
            return e
        }
        fail("Expected the change to fail")
        error("unreachable")
    }

    private companion object {
        const val ANSWER = """{"edits":[{"op":"item_sharers","itemId":"i1","participantIds":["me"]}],""" +
            """"explanation":"Lan no longer shares the beer.","question":null}"""
        const val ONE_CHANGE_USED = """{"limit":3,"used":0.2,"remaining":2.8,"period":"trial","resetsAt":null}"""
        const val NOTHING_READABLE = """{"edits":[{"op":"pay_everything"}],"explanation":"","question":null}"""
        const val TRIAL_USED_UP = """{"limit":3,"used":3,"remaining":0,"period":"trial","resetsAt":null}"""
        const val TRIAL_PART_LEFT = """{"limit":3,"used":2.9,"remaining":0.1,"period":"trial","resetsAt":null}"""
        const val MONTH_USED_UP = """{"limit":10,"used":10,"remaining":0,"period":"2026-11","resetsAt":1796083200000}"""
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        /** The body of the contract's example of `POST /v1/split-revision`, without its request id. */
        val CONTRACT_BODY = Json.parseToJsonElement(
            """
            {
              "instruction": "Lan không uống bia",
              "bill": {
                "merchant": "Pho 24",
                "currency": { "code": "VND", "fractionDigits": 0 },
                "items": [{ "id": "i1", "name": "Bia", "quantity": "3", "amountMinor": 90000 }],
                "charges": [{ "id": "c1", "label": "Service charge", "kind": "service_charge", "amountMinor": 14000 }],
                "totalMinor": 154000
              },
              "split": {
                "participants": [{ "id": "me", "name": "Dana" }, { "id": "p1", "name": "Lan" }],
                "whole": null,
                "items": { "i1": { "type": "equal", "among": ["me"], "except": [] } },
                "notSharing": [],
                "payments": [{ "participantId": "me", "amountMinor": 154000 }]
              }
            }
            """.trimIndent(),
        ).jsonObject
    }
}
