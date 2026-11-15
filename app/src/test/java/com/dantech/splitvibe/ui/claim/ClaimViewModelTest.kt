package com.dantech.splitvibe.ui.claim

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.ShareFixtures
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.payments.PaymentMethod
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClaimViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private val received: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val host = FakeHost(ShareFixtures.saved)
    private lateinit var proxy: TestProxy
    private lateinit var methods: PaymentMethodRepository

    /** What the link's live connection and `open` answer, as people pick on the page. */
    @Volatile
    private var current = ShareFixtures.view(revision = 2, claims = ShareFixtures.CLAIMS)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received += request
                val path = request.path.orEmpty()
                return when {
                    path == "/v1/shares/live" -> MockResponse().withWebSocketUpgrade(answeringClose)

                    path == "/v1/shares/open" -> json(ShareFixtures.share(current))

                    path.endsWith(
                        "/lock",
                    ) -> json(ShareFixtures.share(ShareFixtures.view(ShareView.LOCKING, revision = 3, claims = ShareFixtures.CLAIMS)))

                    path.endsWith(
                        "/finalize",
                    ) -> json(ShareFixtures.share(ShareFixtures.view(ShareView.LOCKED, revision = 4, claims = ShareFixtures.CLAIMS)))

                    request.method == "DELETE" -> MockResponse().setResponseCode(204)

                    else -> json(ShareFixtures.owned())
                }
            }
        }
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        methods = PaymentMethodRepository(storage.database)
    }

    @After
    fun tearDown() {
        // Clearing the view model closes its live connection before the server stops.
        store.clear()
        server.shutdown()
        storage.close()
    }

    private val store = ViewModelStore()

    /** The server's side of the live connection, which answers the app's close as a server does. */
    private val answeringClose = object : WebSocketListener() {
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }
    }

    private fun viewModel(): ClaimViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer {
                ClaimViewModel(
                    host = host,
                    shares = ShareRepository(proxy.api, retryDelayMs = { 60_000 }),
                    paymentMethods = methods,
                    settings = MutableStateFlow(AppSettings(localCurrency = "VND", myName = "Dana")),
                    account = proxy.account.account,
                    newId = { ShareFixtures.SHARE_ID },
                )
            }
        },
    )[ClaimViewModel::class.java]

    @Test
    fun `a bill without a link gets one, which the bill then keeps`() {
        val viewModel = viewModel()

        awaitOnMain { viewModel.state.value.share != null }

        val put = received.first { it.method == "PUT" }
        assertEquals("/v1/shares/${ShareFixtures.SHARE_ID}", put.path)
        assertEquals(ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL), host.bill.value?.share)
        assertEquals(ShareFixtures.URL, viewModel.state.value.url)
    }

    @Test
    fun `each claim is suggested as the person of the same name, and a stranger as someone new`() {
        val viewModel = viewModel()

        awaitOnMain { viewModel.state.value.share?.claims?.size == 2 }

        assertEquals(mapOf("claim-minh" to "p1", "claim-x" to ClaimState.NEW_PERSON), viewModel.state.value.matches)
    }

    @Test
    fun `locking splits the bill by what each person picked, adding who is new to it`() {
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.matches.size == 2 }

        viewModel.lock()
        awaitOnMain { host.applied != null }

        val plan = checkNotNull(host.applied)
        assertEquals(listOf("me", "p1", "p2", "p3"), plan.participantIds)
        assertEquals("Stranger", plan.participants.last().name)
        assertNull(plan.setup.whole)
        assertEquals(Division.Equally(among = setOf("p1")), plan.setup.items["i1"])
        assertEquals(Division.Equally(among = setOf("p3")), plan.setup.items["i2"])
        assertEquals(ShareView.LOCKING, viewModel.state.value.share?.status)
    }

    @Test
    fun `the amounts sent to the page are the calculator's and the saved bill's, with how to pay for the others`() {
        runBlocking { methods.add(PaymentMethod.VietQr("970416", "257678859", "DANA NGUYEN")) }
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.matches.size == 2 }
        viewModel.lock()
        awaitOnMain { host.applied != null }

        viewModel.send()
        awaitOnMain { host.finished }

        val stored = checkNotNull(host.bill.value)
        val calculated = SplitCalculator.split(stored.bill, stored.plan.participantIds, stored.plan.setup).shares.associate {
            it.personId to
                it.totalMinor
        }
        val saved = stored.withSharesWorkedOut().shares.associate { it.personId to it.totalMinor }
        val sent = Json.parseToJsonElement(received.first { it.path!!.endsWith("/finalize") }.body.readUtf8()).jsonObject
        val amounts = sent["amounts"]!!.jsonObject
        val onPage = amounts.mapValues { (_, amount) -> amount.jsonObject["amountMinor"]!!.jsonPrimitive.long }
        assertEquals(calculated, onPage)
        assertEquals(saved, onPage)
        assertEquals(stored.bill.totalMinor, onPage.values.sum())
        assertEquals("null", amounts["me"]!!.jsonObject["payment"].toString())
        assertTrue(amounts["p1"]!!.jsonObject["payment"]!!.jsonObject["qrPayload"]!!.jsonPrimitive.content.startsWith("000201"))
        assertEquals(ShareView.LOCKED, viewModel.state.value.share?.status)
    }

    @Test
    fun `taking the link down forgets it and closes the screen`() {
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share != null }

        viewModel.revoke()
        awaitOnMain { viewModel.state.value.closed }

        assertTrue(received.any { it.method == "DELETE" && it.path == "/v1/shares/${ShareFixtures.SHARE_ID}" })
        assertNull(host.bill.value?.share)
    }

    @Test
    fun `a link needs a sign-in, and nothing is sent without one`() {
        proxy.account.clearSession()

        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.needsSignIn }

        assertEquals(0, server.requestCount)
    }

    /** The split screen, holding the one copy of the bill that is written. */
    private class FakeHost(start: SavedBill) : BillOnScreen {
        val bill = MutableStateFlow<SavedBill?>(start)
        override val billOnScreen: StateFlow<SavedBill?> = bill

        @Volatile
        var applied: SplitPlan? = null

        @Volatile
        var finished = false

        override suspend fun storedBill(): SavedBill? = bill.value

        override fun linkToShare(link: ShareLink?) {
            bill.value = bill.value?.copy(share = link)
        }

        override fun applyPlan(plan: SplitPlan) {
            applied = plan
            bill.value = bill.value?.copy(plan = plan, revision = (bill.value?.revision ?: 0) + 1)
        }

        override fun finish() {
            finished = true
            bill.value = bill.value?.copy(status = BillStatus.FINAL)
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
