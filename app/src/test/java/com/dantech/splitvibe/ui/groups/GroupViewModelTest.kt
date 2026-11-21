package com.dantech.splitvibe.ui.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.GroupFixtures.AN
import com.dantech.splitvibe.GroupFixtures.GROUP_ID
import com.dantech.splitvibe.GroupFixtures.ME
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.Transfer
import com.dantech.splitvibe.domain.payments.PaymentMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private val received = mutableListOf<RecordedRequest>()
    private lateinit var viewModel: GroupViewModel

    /** An pays the user 35.43 THB to settle the fixture's trip. */
    private val anPaysMe = Transfer(AN, ME, 3_543)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received += request
                val body = when {
                    request.path == "/v1/requests" && request.method == "POST" -> {
                        val sent = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                        val id = sent["id"]!!.jsonPrimitive.content
                        val view = GroupFixtures.request(id = id)
                            .replace("\"groupId\":null", "\"groupId\":\"$GROUP_ID\"")
                            .replace("\"toMemberId\":null", "\"toMemberId\":\"$AN\"")
                        """{"request":$view,"url":"https://proxy.example/r/#token-$id"}"""
                    }

                    request.path!!.endsWith("/confirmed-paid") -> GroupFixtures.request(id = "unused", confirmedPaidAt = 1)

                    else -> GroupFixtures.groupState()
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        val cache = ProxyCache(storage.database.proxyCache())
        val methods = PaymentMethodRepository(storage.database)
        runBlocking { methods.add(PaymentMethod.PromptPay("000-000-0000")) }
        viewModel = GroupViewModel(
            GROUP_ID,
            GroupRepository(proxy.api, cache, proxy.account),
            RequestRepository(proxy.api, cache, proxy.account),
            methods,
            MutableStateFlow(AppSettings("THB", myName = "Dana")),
            contentWords(),
        )
        awaitOnMain { viewModel.group.value != null && !viewModel.status.value.working }
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `asking twice for the same debt makes one request`() {
        repeat(2) {
            viewModel.askFor(anPaysMe)
            awaitOnMain { viewModel.share.value != null && !viewModel.status.value.working }
            viewModel.shared()
        }

        assertEquals(1, received.count { it.path == "/v1/requests" && it.method == "POST" })
    }

    @Test
    fun `once a member was asked, getting the money confirms that request rather than recording it again`() {
        viewModel.askFor(anPaysMe)
        awaitOnMain { viewModel.openRequests.value.containsKey(AN) && !viewModel.status.value.working }

        viewModel.markReceived(anPaysMe, settlementId = "5b0c0d0e-0000-4000-8000-000000000001")
        awaitOnMain { received.any { it.path!!.endsWith("/confirmed-paid") } && !viewModel.status.value.working }

        assertTrue(received.none { it.path!!.endsWith("/settlements") })
    }

    @Test
    fun `without an open request, getting the money records it under the id the user's choice was given`() {
        viewModel.markReceived(anPaysMe, settlementId = "5b0c0d0e-0000-4000-8000-000000000002")
        awaitOnMain { received.any { it.path!!.endsWith("/settlements") } && !viewModel.status.value.working }

        val sent = Json.parseToJsonElement(received.single { it.path!!.endsWith("/settlements") }.body.clone().readUtf8()).jsonObject
        assertEquals("5b0c0d0e-0000-4000-8000-000000000002", sent["id"]!!.jsonPrimitive.content)
    }
}
