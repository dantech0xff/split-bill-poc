package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.PlainCipher
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import kotlinx.coroutines.runBlocking
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

@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val google = FakeGoogleAccounts()
    private lateinit var proxy: TestProxy
    private lateinit var repository: AccountRepository

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        repository = AccountRepository(proxy.api, proxy.account, google)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `signing in trades Google's ID token for the proxy's session and keeps the account's address`() = runBlocking {
        server.enqueue(json(200, """{"session":"signed-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))

        repository.signIn(context)

        assertEquals("""{"idToken":"google-id-token"}""", server.takeRequest().body.readUtf8())
        val account = proxy.account.account.value
        assertEquals(Session("signed-session", FAR_FUTURE), account.session)
        assertEquals("minh.tran@example.com", account.signedInAs)
        assertEquals(10, account.callsLeft?.remaining)
    }

    @Test
    fun `nothing changes when the person closes Google's picker`() = runBlocking {
        google.failure = GoogleSignInException(GoogleSignInException.Reason.CANCELLED)

        try {
            repository.signIn(context)
            fail("A closed picker must not sign anyone in")
        } catch (e: GoogleSignInException) {
            assertEquals(GoogleSignInException.Reason.CANCELLED, e.reason)
        }

        assertNull(proxy.account.account.value.session)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a sign-in the proxy refuses keeps the phone signed out`() = runBlocking {
        server.enqueue(json(401, """{"error":"id_token"}"""))

        try {
            repository.signIn(context)
            fail("A refused token must not sign anyone in")
        } catch (e: ProxyException) {
            assertEquals("id_token", e.code)
        }

        assertNull(proxy.account.account.value.session)
    }

    @Test
    fun `signing out forgets the session here and on the phone's account picker`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")

        repository.signOut(context)

        assertNull(proxy.account.account.value.session)
        assertNull(proxy.account.account.value.signedInAs)
        assertTrue(google.forgotten)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `deleting the account asks the proxy first and forgets the session once it is done`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(MockResponse().setResponseCode(204))

        repository.deleteAccount(context)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        assertNull(proxy.account.account.value.session)
        assertTrue(google.forgotten)
    }

    @Test
    fun `an account the proxy could not delete stays signed in, unless its session ran out`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(json(500, """{"error":"internal"}"""))
        server.enqueue(json(401, """{"error":"session"}"""))

        runCatching { repository.deleteAccount(context) }
        assertEquals("signed-session", proxy.account.account.value.session?.token)
        assertFalse(google.forgotten)

        runCatching { repository.deleteAccount(context) }
        assertNull(proxy.account.account.value.session)
        assertTrue("signing in again can delete it", proxy.account.account.value.signInExpired)
        assertEquals("minh.tran@example.com", proxy.account.account.value.signedInAs)
    }

    @Test
    fun `a sign-in that has run out is not asked about as the install, and signing in again ends it`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        server.enqueue(json(401, """{"error":"session"}"""))
        server.enqueue(json(200, """{"session":"new-session","expiresAt":${FAR_FUTURE},"usage":$MONTH}"""))

        repository.refresh()
        assertTrue(proxy.account.account.value.signInExpired)
        repository.refresh()
        assertEquals("the second count is not asked for at all", 1, server.requestCount)

        repository.signIn(context)

        assertEquals("Bearer signed-session", server.takeRequest().getHeader("Authorization"))
        val signIn = server.takeRequest()
        assertEquals("/v1/session", signIn.requestUrl?.encodedPath)
        assertNull("signing in carries Google's token, not the old session", signIn.getHeader("Authorization"))
        assertFalse(proxy.account.account.value.signInExpired)
        assertEquals("new-session", proxy.account.account.value.session?.token)
    }

    @Test
    fun `signing out after the sign-in ran out goes back to this phone's free reads`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE))
        proxy.account.expireSession()
        server.enqueue(json(200, """{"usage":{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null},"aiEnabled":true}"""))

        repository.signOut(context)
        repository.refresh()

        assertFalse(proxy.account.account.value.signInExpired)
        assertNull("the count is the install's", server.takeRequest().getHeader("Authorization"))
        assertEquals(2, proxy.account.account.value.callsLeft?.remaining)
    }

    @Test
    fun `asks the proxy for the calls left and whether AI is on`() = runBlocking {
        server.enqueue(json(200, """{"usage":{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null},"aiEnabled":false}"""))

        repository.refresh()

        assertEquals(2, proxy.account.account.value.callsLeft?.remaining)
        assertEquals(AiStatus.OFF, proxy.account.account.value.ai)
    }

    @Test
    fun `cannot sign in without the proxy's address or Google's client ID`() {
        val withoutProxy = AccountRepository(ProxyApi("", proxy.account, appVersion = 1), proxy.account, google)
        val withoutClientId = AccountRepository(proxy.api, AccountStore(context, proxySetUp = true, cipher = PlainCipher), google = null)

        assertTrue(repository.canSignIn)
        assertFalse(withoutProxy.canSignIn)
        assertFalse(withoutClientId.canSignIn)
    }

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        const val MONTH = """{"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000}"""
    }
}
