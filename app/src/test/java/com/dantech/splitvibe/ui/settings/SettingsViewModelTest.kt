package com.dantech.splitvibe.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.inEnglish
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The account part of Settings, against a local server that answers as the proxy does. */
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private lateinit var proxy: TestProxy

    /** What the proxy answers on each path; the count it gives is the install's trial. */
    private val answers = mutableMapOf(
        "/v1/usage" to { json(200, """{"usage":$TRIAL,"aiEnabled":true}""") },
        "/v1/session" to { json(200, """{"session":"signed-session","expiresAt":$FAR_FUTURE,"usage":$MONTH}""") },
        "/v1/account" to { MockResponse().setResponseCode(204) },
    )

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers[request.requestUrl?.encodedPath]?.invoke() ?: MockResponse().setResponseCode(404)
        }
        server.start()
        proxy = TestProxy(server, context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `signing in while the count is still being asked for is not undone by the count`() {
        // The install's count is slow to come, and arrives after the account's.
        answers["/v1/usage"] = { json(200, """{"usage":$TRIAL,"aiEnabled":true}""").setHeadersDelay(1, TimeUnit.SECONDS) }
        val viewModel = viewModel()
        awaitOnMain { server.requestCount == 1 }

        viewModel.signIn(context)
        assertTrue(viewModel.ui.value.working)
        awaitOnMain { !viewModel.ui.value.working }

        assertEquals(BigDecimal(10), proxy.account.account.value.callsLeft?.remaining)
        // Long enough for the install's count to have arrived, had it not been stopped.
        val stillThere = System.currentTimeMillis() + 1_500
        awaitOnMain { System.currentTimeMillis() > stillThere }
        assertEquals("the account's count stays", BigDecimal(10), proxy.account.account.value.callsLeft?.remaining)
        assertNull(viewModel.ui.value.message)
    }

    @Test
    fun `deleting the account says it was done`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val viewModel = viewModel()

        viewModel.deleteAccount(context)
        awaitOnMain { !viewModel.ui.value.working }

        assertEquals("Your account was deleted.", viewModel.ui.value.message.inEnglish())
        assertFalse(viewModel.ui.value.isError)
        assertNull(proxy.account.account.value.session)
    }

    @Test
    fun `an action that did not work says why, as an error`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE))
        answers["/v1/account"] = { json(500, """{"error":"internal"}""") }
        val viewModel = viewModel()

        viewModel.deleteAccount(context)
        awaitOnMain { !viewModel.ui.value.working }

        assertTrue(viewModel.ui.value.message.inEnglish().orEmpty().contains("server had a problem"))
        assertTrue(viewModel.ui.value.isError)
        assertEquals("signed-session", proxy.account.account.value.session?.token)
    }

    @Test
    fun `a deletion the proxy limits keeps the account signed in, and says when to try again`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        answers["/v1/account"] = { json(429, """{"error":"rate_limited"}""").setHeader("Retry-After", "2400") }
        val viewModel = viewModel()

        viewModel.deleteAccount(context)
        awaitOnMain { !viewModel.ui.value.working }

        val said = "That was tried too many times just now, so nothing was changed. Try again in about 40 minutes."
        assertEquals(said, viewModel.ui.value.message.inEnglish())
        assertTrue(viewModel.ui.value.isError)
        assertEquals("signed-session", proxy.account.account.value.session?.token)
    }

    @Test
    fun `opening Settings without a connection says nothing`() {
        server.shutdown()
        val viewModel = viewModel()

        val waited = System.currentTimeMillis() + 500
        awaitOnMain { System.currentTimeMillis() > waited }

        assertNull(viewModel.ui.value.message)
        assertFalse(viewModel.ui.value.working)
    }

    private fun viewModel() = SettingsViewModel(proxy.accountRepository(FakeGoogleAccounts(), ProxyCache(storage.database.proxyCache())))

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        const val TRIAL = """{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null}"""
        const val MONTH = """{"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000}"""
    }
}
