package com.dantech.splitvibe.data.proxy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The account calls of the AI proxy's contract: sign-in, calls left and deleting the account. */
@RunWith(AndroidJUnit4::class)
class ProxyApiTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var proxy: TestProxy

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `trades Google's ID token for the proxy's session`() = runBlocking {
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":1798761600000,"usage":$MONTH}"""))

        val (session, callsLeft) = proxy.api.startSession("google-id-token")

        assertEquals(Session("signed-session", 1_798_761_600_000), session)
        assertEquals(CallsLeft(BigDecimal(10), BigDecimal(0), BigDecimal(10), period = "2026-11", resetsAt = 1_796_083_200_000), callsLeft)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/session", request.requestUrl?.encodedPath)
        assertEquals("""{"idToken":"google-id-token"}""", request.body.readUtf8())
    }

    @Test
    fun `reports the refusal of a token the proxy does not accept`() = runBlocking {
        server.enqueue(json(401, """{"error":"id_token"}"""))

        try {
            proxy.api.startSession("google-id-token")
            fail("A refused token must not give a session")
        } catch (e: ProxyException) {
            assertEquals(401, e.status)
            assertEquals("id_token", e.code)
        }
    }

    @Test
    fun `asks for the calls left as the signed-in account`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        server.enqueue(json(200, """{"usage":$MONTH,"aiEnabled":false}"""))

        val usage = proxy.api.usage()

        assertEquals(BigDecimal(10), usage.callsLeft.remaining)
        assertFalse(usage.aiEnabled)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/usage", request.requestUrl?.encodedPath)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        assertEquals(proxy.account.account.value.installId, request.getHeader(ProxyApi.INSTALL_HEADER))
    }

    @Test
    fun `deletes the signed-in account`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        server.enqueue(MockResponse().setResponseCode(204))

        proxy.api.deleteAccount()

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/v1/account", request.requestUrl?.encodedPath)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
    }

    @Test
    fun `calls are read with their decimals, from the number as written`() = runBlocking {
        val usages = listOf(
            """{"limit":3,"used":0.2,"remaining":2.8,"period":"trial","resetsAt":null}""" to listOf("3", "0.2", "2.8"),
            // A proxy from before calls could weigh less than one wrote whole numbers.
            """{"limit":10,"used":1,"remaining":9,"period":"2026-11","resetsAt":1796083200000}""" to listOf("10", "1", "9"),
            // The same number written another way is the same count.
            """{"limit":10.00,"used":0.20,"remaining":9.80,"period":"2026-11","resetsAt":1796083200000}""" to listOf("10", "0.2", "9.8"),
            """{"limit":1e1,"used":2E-1,"remaining":0.098e2,"period":"2026-11","resetsAt":1796083200000}""" to listOf("10", "0.2", "9.8"),
        )
        for ((usage, expected) in usages) {
            server.enqueue(json(200, """{"usage":$usage,"aiEnabled":true}"""))

            val calls = proxy.api.usage().callsLeft

            assertEquals(usage, expected.map(::BigDecimal), listOf(calls.limit, calls.used, calls.remaining))
            assertEquals(usage, expected, listOf(calls.limit, calls.used, calls.remaining).map(CallsLeft::text))
        }
    }

    @Test
    fun `the plan comes with the calls, and an answer without it is the install's trial or a month's free calls`() = runBlocking {
        val month = """"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000"""
        val trial = """"limit":3,"used":0,"remaining":3,"period":"trial","resetsAt":null"""
        val usages = listOf(
            """{$month,"plan":"pro"}""" to Plan.PRO,
            """{$month,"plan":"free"}""" to Plan.FREE,
            """{$trial,"plan":"trial"}""" to Plan.TRIAL,
            // A proxy from before Pro.
            """{$trial}""" to Plan.TRIAL,
            """{$month}""" to Plan.FREE,
            // A plan this version does not know reads as missing, and a plan of the wrong shape too.
            """{$month,"plan":"team"}""" to Plan.FREE,
            """{$trial,"plan":7}""" to Plan.TRIAL,
        )
        for ((usage, plan) in usages) {
            server.enqueue(json(200, """{"usage":$usage,"aiEnabled":true}"""))

            assertEquals(usage, plan, proxy.api.usage().callsLeft.plan)
        }
    }

    @Test
    fun `the purchaser is read only as the proxy makes it, from the sign-in and the calls left`() = runBlocking {
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":1798761600000,"usage":$MONTH,"purchaser":"$PURCHASER"}"""))
        assertEquals(PURCHASER, proxy.api.startSession("google-id-token").purchaser)

        for (wrong in listOf(PURCHASER.uppercase(), PURCHASER.drop(1), "$PURCHASER ", "../$PURCHASER", "")) {
            server.enqueue(json(200, """{"usage":$MONTH,"aiEnabled":true,"purchaser":"$wrong"}"""))
            assertNull(wrong, proxy.api.usage().purchaser)
        }
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":1798761600000,"usage":$MONTH}"""))
        assertNull("a proxy from before Pro", proxy.api.startSession("google-id-token").purchaser)
    }

    @Test
    fun `asks the proxy to check the plan as the signed-in account, with an empty body`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        val pro = """{"limit":300,"used":12,"remaining":288,"period":"2026-11","resetsAt":1796083200000,"plan":"pro"}"""
        server.enqueue(json(200, """{"purchaser":"$PURCHASER","usage":$pro,"aiEnabled":true}"""))

        val usage = proxy.api.refreshPlan()

        assertEquals(PURCHASER, usage.purchaser)
        assertEquals(Plan.PRO, usage.callsLeft.plan)
        assertEquals(BigDecimal(288), usage.callsLeft.remaining)
        assertTrue(usage.aiEnabled)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/plan", request.requestUrl?.encodedPath)
        assertEquals("{}", request.body.readUtf8())
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
    }

    @Test
    fun `checking the plan needs a sign-in, and keeps the proxy's refusals as they are`() = runBlocking {
        try {
            proxy.api.refreshPlan()
            fail("Nobody is signed in")
        } catch (e: ProxyException) {
            assertEquals(ProxyApi.SESSION_REFUSED, e.code)
        }
        assertEquals("nothing went out", 0, server.requestCount)

        proxy.account.saveSession(Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000))
        val refusals = listOf(
            json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "1200") to (429 to "rate_limited"),
            json(503, """{"error":"not_configured"}""") to (503 to "not_configured"),
            json(502, """{"error":"subscription_unavailable"}""") to (502 to "subscription_unavailable"),
        )
        for ((refusal, expected) in refusals) {
            server.enqueue(refusal)
            try {
                proxy.api.refreshPlan()
                fail("${expected.second} is no plan")
            } catch (e: ProxyException) {
                assertEquals(expected, e.status to e.code)
            }
        }
    }

    @Test
    fun `an answer of the wrong shape is a bad response, not a crash`() = runBlocking {
        val answers = listOf(
            """{"usage":{"limit":"ten","used":0,"remaining":10,"period":"2026-11"},"aiEnabled":true}""",
            """{"usage":[10],"aiEnabled":true}""",
            """["usage"]""",
            // A count is a number of calls from zero, in hundredths at most, and never a string of one.
            """{"usage":{"limit":3,"used":0.2,"remaining":"2.8","period":"trial"},"aiEnabled":true}""",
            """{"usage":{"limit":3,"used":3.2,"remaining":-0.2,"period":"trial"},"aiEnabled":true}""",
            """{"usage":{"limit":3,"used":0.195,"remaining":2.805,"period":"trial"},"aiEnabled":true}""",
            """{"usage":{"limit":3,"used":0,"remaining":true,"period":"trial"},"aiEnabled":true}""",
            // Far too large to be a count, and far too large to write out, which must not be tried.
            """{"usage":{"limit":1e999999999,"used":0,"remaining":3,"period":"trial"},"aiEnabled":true}""",
            """{"usage":{"limit":3,"used":1e-999999999,"remaining":3,"period":"trial"},"aiEnabled":true}""",
        )
        for (answer in answers) {
            server.enqueue(json(200, answer))
            try {
                proxy.api.usage()
                fail("$answer must not read as calls left")
            } catch (e: ProxyException) {
                assertEquals(answer, ProxyApi.BAD_RESPONSE, e.code)
            }
        }
    }

    @Test
    fun `a line nested far deeper than the proxy writes is a bad response, not a crash`() = runBlocking {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        server.enqueue(
            MockResponse()
                .setHeader("content-type", "application/x-ndjson")
                .setBody("{\"type\":\"wait\"}\n{\"type\":\"result\",\"answer\":{\"edits\":$deep}}\n"),
        )
        server.enqueue(json(200, """{"usage":$deep}"""))

        val asks: List<suspend () -> Unit> = listOf(
            { proxy.api.reviseSplit(buildJsonObject { put("instruction", "x") }) },
            { proxy.api.usage() },
        )
        for (ask in asks) {
            try {
                ask()
                fail("an answer nested that deep must not be read")
            } catch (e: ProxyException) {
                assertEquals(ProxyApi.BAD_RESPONSE, e.code)
            }
        }
    }

    @Test
    fun `the client waits long enough for the proxy's wait lines and its model`() {
        val client = ProxyApi.defaultClient()

        // The proxy sends a wait line every 10 seconds and gives the model 120 seconds.
        assertTrue("two wait lines may be lost", client.readTimeoutMillis >= 3 * WAIT_LINE_MS)
        assertTrue("the model's time and the network", client.callTimeoutMillis > MODEL_DEADLINE_MS + client.connectTimeoutMillis)
        assertEquals(15_000, client.connectTimeoutMillis)
    }

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val WAIT_LINE_MS = 10_000
        const val MODEL_DEADLINE_MS = 120_000
        const val MONTH = """{"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000}"""
        val PURCHASER = "5e".repeat(32)
    }
}
