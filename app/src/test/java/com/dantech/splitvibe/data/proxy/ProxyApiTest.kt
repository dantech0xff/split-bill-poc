package com.dantech.splitvibe.data.proxy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(CallsLeft(limit = 10, used = 0, remaining = 10, period = "2026-11", resetsAt = 1_796_083_200_000), callsLeft)
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

        assertEquals(10, usage.callsLeft.remaining)
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

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val MONTH = """{"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000}"""
    }
}
