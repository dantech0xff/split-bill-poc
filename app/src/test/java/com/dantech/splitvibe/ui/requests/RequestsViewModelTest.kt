package com.dantech.splitvibe.ui.requests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.english
import com.dantech.splitvibe.inEnglish
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private lateinit var proxy: TestProxy
    private lateinit var repository: RequestRepository
    private lateinit var groups: GroupRepository

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        repository = RequestRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account)
        groups = GroupRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `a reminder carries the payment details and the request's own link`() {
        server.enqueue(json("""{"requests":[${GroupFixtures.request()}]}"""))
        val viewModel = RequestsViewModel(repository, groups, proxy.account.account)
        awaitOnMain { viewModel.requests.value?.isNotEmpty() == true }

        viewModel.remind(viewModel.requests.value!!.single())
        awaitOnMain { viewModel.share.value != null }

        val text = viewModel.share.value!!.text.english()
        assertTrue(text, text.startsWith("Hi Minh, a reminder about the ₫150,000 for Pho 24."))
        assertTrue(text, text.lines().contains("Account: 000000000"))
        assertTrue(viewModel.share.value!!.paying!!.qrPayload!!.startsWith("000201"))
    }

    @Test
    fun `opening a request link shows it, and saying it is paid takes it on first`() {
        server.enqueue(json(GroupFixtures.request(role = "debtor")))
        server.enqueue(json(GroupFixtures.request(role = "debtor")))
        server.enqueue(json(GroupFixtures.request(role = "debtor", reportedPaidAt = 1_763_200_000_000)))
        val viewModel = RequestLinkViewModel("token-1234567890abcdef", repository, groups, proxy.account.account)
        awaitOnMain { viewModel.state.value.request != null }

        viewModel.reportPaid()
        awaitOnMain { viewModel.state.value.request?.status == RequestStatus.REPORTED_PAID }

        val open = server.takeRequest()
        assertEquals("/v1/request-links/open", open.path)
        assertEquals("""{"token":"token-1234567890abcdef"}""", open.body.readUtf8())
        assertEquals("/v1/request-links/accept", server.takeRequest().path)
        assertEquals("/v1/requests/r1/reported-paid", server.takeRequest().path)
        assertTrue(viewModel.state.value.accepted)
    }

    @Test
    fun `a link someone else took on says so`() {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"conflict"}"""))

        val viewModel = RequestLinkViewModel("token-1234567890abcdef", repository, groups, proxy.account.account)
        awaitOnMain { viewModel.state.value.message != null }

        assertTrue(viewModel.state.value.message.inEnglish()!!.startsWith("Someone else already took this request on."))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
