package com.dantech.splitvibe.ui.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Deletion
import com.dantech.splitvibe.data.account.DeletionStore
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.inEnglish
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    private val settings = SettingsStore(context)
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
        assertEquals(AccountAction.SIGN_IN, viewModel.ui.value.action)
        awaitOnMain { !viewModel.ui.value.working }

        assertEquals(BigDecimal(10), proxy.account.account.value.callsLeft?.remaining)
        // Long enough for the install's count to have arrived, had it not been stopped.
        val stillThere = System.currentTimeMillis() + 1_500
        awaitOnMain { System.currentTimeMillis() > stillThere }
        assertEquals("the account's count stays", BigDecimal(10), proxy.account.account.value.callsLeft?.remaining)
        assertNull(viewModel.ui.value.message)
    }

    @Test
    fun `deleting the account says it was done, as the outcome of the deletion started here`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val viewModel = viewModel()

        viewModel.deleteAccount(withPhoneData = false)
        assertEquals(AccountUiState(working = true, action = AccountAction.DELETE), viewModel.ui.value)
        awaitOnMain { !viewModel.ui.value.working }

        assertEquals("Your account was deleted.", viewModel.ui.value.message.inEnglish())
        assertFalse(viewModel.ui.value.isError)
        // Settings says it by the button that started it.
        assertEquals(AccountAction.DELETE, viewModel.ui.value.action)
        assertNull(proxy.account.account.value.session)
    }

    @Test
    fun `deleting the account with the phone's data says that it went too`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        settings.saveName("Minh")
        val viewModel = viewModel()

        viewModel.deleteAccount(withPhoneData = true)
        awaitOnMain { !viewModel.ui.value.working }

        val said = "Your account was deleted, and so were the bills, people and payment methods on this phone."
        assertEquals(said, viewModel.ui.value.message.inEnglish())
        assertFalse(viewModel.ui.value.isError)
        assertEquals("", settings.settings.value.myName)
    }

    @Test
    fun `an account deleted while the phone's data could not all be deleted says so, as an error`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        // A database that fails part of the way through, as a broken one on a phone would.
        val broken = TestStorage(context)
        broken.database.openHelper.writableDatabase.execSQL("DROP TABLE recurring_bills")
        val lunch = SpendingFixtures.finished("lunch", "2026-11-14", "Pho 24", BillCategory.FOOD_AND_DRINK, 120_000, listOf("Lan"))
        runBlocking { broken.bills.save(lunch) }
        val viewModel = viewModel(phone = broken)

        viewModel.deleteAccount(withPhoneData = true)
        awaitOnMain { !viewModel.ui.value.working }

        assertTrue(viewModel.ui.value.message.inEnglish().orEmpty().startsWith("Your account was deleted, but the bills"))
        assertTrue(viewModel.ui.value.isError)
        assertNull("the account is gone all the same", proxy.account.account.value.session)
        assertEquals("no table is half deleted", 1, runBlocking { broken.bills.listings().first() }.size)
        broken.close()
    }

    @Test
    fun `an action that did not work says why, as an error`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE))
        answers["/v1/account"] = { json(500, """{"error":"internal"}""") }
        val viewModel = viewModel()

        viewModel.deleteAccount(withPhoneData = true)
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

        viewModel.deleteAccount(withPhoneData = true)
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

    @Test
    fun `leaving Settings while the account is deleted does not stop it, and Settings says how it ended when it opens again`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        settings.saveName("Minh")
        answers["/v1/account"] = { MockResponse().setResponseCode(204).setHeadersDelay(500, TimeUnit.MILLISECONDS) }
        val repository = repository()
        val left = SettingsViewModel(repository)

        left.deleteAccount(withPhoneData = true)
        assertTrue(left.ui.value.working)
        // Back, the top bar's arrow or Save: the screen goes, and its view model with it.
        left.viewModelScope.cancel()
        awaitOnMain { repository.deletion.value != Deletion.Running }

        assertNull(proxy.account.account.value.session)
        assertEquals("", settings.settings.value.myName)
        val said = "Your account was deleted, and so were the bills, people and payment methods on this phone."
        assertEquals(said, SettingsViewModel(repository).ui.value.message.inEnglish())
        assertNull("it is said once", SettingsViewModel(repository).ui.value.message)
    }

    @Test
    fun `Settings opened while the deletion runs shows it working and then how it ended`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        answers["/v1/account"] = { MockResponse().setResponseCode(204).setHeadersDelay(500, TimeUnit.MILLISECONDS) }
        val repository = repository()
        SettingsViewModel(repository).apply {
            deleteAccount(withPhoneData = false)
            viewModelScope.cancel()
        }

        val again = SettingsViewModel(repository)
        assertTrue(again.ui.value.working)
        // Started on the earlier visit, so it shows in the card at the top, where the person arrives.
        assertNull(again.ui.value.action)
        awaitOnMain { !again.ui.value.working }

        assertEquals("Your account was deleted.", again.ui.value.message.inEnglish())
        assertNull(again.ui.value.action)
        assertEquals("Settings opening asked for no count meanwhile", 1, server.requestCount)
    }

    @Test
    fun `a deletion the app ended in, which the proxy no longer confirms, says so when Settings opens`() {
        proxy.account.saveSession(Session("signed-session", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        DeletionStore(context).begin(withPhoneData = true)
        answers["/v1/account"] = { json(401, """{"error":"session"}""") }
        val viewModel = viewModel()

        awaitOnMain { !viewModel.ui.value.working && viewModel.ui.value.message != null }

        assertTrue(viewModel.ui.value.message.inEnglish().orEmpty().startsWith("Deleting your account was cut off"))
        assertTrue(viewModel.ui.value.isError)
    }

    private fun repository(phone: TestStorage = storage) =
        proxy.accountRepository(FakeGoogleAccounts(), ProxyCache(storage.database.proxyCache()), phone.phoneData(context, settings))

    private fun viewModel(phone: TestStorage = storage) = SettingsViewModel(repository(phone))

    private fun json(status: Int, body: String): MockResponse =
        MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body)

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        const val TRIAL = """{"limit":3,"used":1,"remaining":2,"period":"trial","resetsAt":null}"""
        const val MONTH = """{"limit":10,"used":0,"remaining":10,"period":"2026-11","resetsAt":1796083200000}"""
    }
}
