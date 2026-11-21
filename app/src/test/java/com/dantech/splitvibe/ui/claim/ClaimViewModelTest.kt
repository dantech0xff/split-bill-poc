package com.dantech.splitvibe.ui.claim

import android.content.Context
import android.os.Looper
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
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.bills.ShareLink.Companion.NEW_PERSON
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.requests.fingerprintOf
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.ShareClaim
import com.dantech.splitvibe.data.share.ShareFixtures
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payments
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.payments.PaymentMethod
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ClaimViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)

    /** The requests the server got. Its thread adds to them while the test looks through them, so each look goes over a copy of its own. */
    private val received: MutableList<RecordedRequest> = CopyOnWriteArrayList()
    private val host = FakeHost(ShareFixtures.saved)
    private val store = ViewModelStore()
    private lateinit var proxy: TestProxy
    private lateinit var methods: PaymentMethodRepository

    /** What reading the link answers: its status code, and the link as people picked on it. */
    @Volatile
    private var linkStatus = 200

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received += request
                val path = request.path.orEmpty()
                return when {
                    path == "/v1/shares/live" -> MockResponse().withWebSocketUpgrade(answeringClose)

                    path == "/v1/shares/open" -> json(ShareFixtures.share(ShareFixtures.view(revision = 2, claims = ShareFixtures.CLAIMS)))

                    path.endsWith(
                        "/lock",
                    ) -> json(ShareFixtures.share(ShareFixtures.view(ShareView.LOCKING, revision = 3, claims = ShareFixtures.CLAIMS)))

                    path.endsWith(
                        "/finalize",
                    ) -> json(ShareFixtures.share(ShareFixtures.view(ShareView.LOCKED, revision = 4, claims = ShareFixtures.CLAIMS)))

                    request.method == "DELETE" -> MockResponse().setResponseCode(204)

                    request.method == "GET" && linkStatus != 200 -> MockResponse().setResponseCode(
                        linkStatus,
                    ).setBody("""{"error":"gone"}""")

                    else -> json(ShareFixtures.owned(ShareFixtures.view(revision = 2, claims = ShareFixtures.CLAIMS)))
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

    /** The server's side of the live connection, which answers the app's close as a server does. */
    private val answeringClose = object : WebSocketListener() {
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }
    }

    private fun viewModel(billWaitMs: Long = 10_000): ClaimViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer {
                ClaimViewModel(
                    host = host,
                    shares = ShareRepository(proxy.api, contentWords(), retryDelayMs = { 60_000 }),
                    paymentMethods = methods,
                    settings = MutableStateFlow(AppSettings(localCurrency = "VND", myName = "Dana")),
                    account = proxy.account.account,
                    newId = { ShareFixtures.SHARE_ID },
                    clock = { NOW },
                    billWaitMs = billWaitMs,
                )
            }
        },
    )[ClaimViewModel::class.java]

    private fun linked(
        link: ShareLink = ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL, expiresAt = EXPIRES_AT, createdAt = CREATED_AT),
    ) {
        host.bill.value = host.bill.value?.copy(share = link)
    }

    @Test
    fun `a bill without a link gets one, which the bill keeps with when it ends`() {
        val viewModel = viewModel()

        awaitOnMain { viewModel.state.value.share != null }

        assertEquals("/v1/shares/${ShareFixtures.SHARE_ID}", received.first { it.method == "PUT" }.path)
        val link = checkNotNull(host.bill.value?.share)
        assertEquals(ShareFixtures.URL, link.url)
        assertEquals(EXPIRES_AT, link.expiresAt)
        assertEquals(ShareRepository.fingerprint(ShareFixtures.saved, "Dana", contentWords()), link.shown)
    }

    @Test
    fun `the screen waits for the bill the split screen reads back after the system ended the app`() {
        host.bill.value = null
        val viewModel = viewModel()

        awaitOnMain { received.isEmpty() && !viewModel.state.value.closed }
        host.bill.value = ShareFixtures.saved
        awaitOnMain { viewModel.state.value.share != null }

        assertEquals(ShareFixtures.SHARE_ID, viewModel.state.value.shareId)
    }

    @Test
    fun `a screen whose bill never comes back closes`() {
        host.bill.value = null

        val viewModel = viewModel(billWaitMs = 50)
        // The wait runs on the main thread, whose clock moves only when the test moves it.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        awaitOnMain { viewModel.state.value.closed }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `each claim is suggested as the person of the same name, and a stranger as someone new`() {
        val viewModel = viewModel()

        awaitOnMain { viewModel.state.value.share?.claims?.size == 2 }

        assertEquals(mapOf("claim-minh" to "p1", "claim-x" to NEW_PERSON), viewModel.state.value.matches)
    }

    @Test
    fun `who the user says a claim is stays with the bill, for the next time the screen opens`() {
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share != null }

        viewModel.match("claim-x", "p2")

        assertEquals("p2", host.bill.value?.share?.matches?.get("claim-x"))
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
        awaitOnMain { host.bill.value?.share?.added == mapOf("claim-x" to "p3") }
        assertEquals(ShareView.LOCKING, viewModel.state.value.share?.status)
    }

    @Test
    fun `a lock that would put more than 30 people on the bill is not made`() {
        val crowd = (1..29).map { Participant("p$it", "Person $it") }
        host.bill.value = ShareFixtures.saved.copy(plan = ShareFixtures.saved.plan.copy(participants = listOf(Participant.ME) + crowd))
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share?.claims?.size == 2 }

        viewModel.lock()

        awaitOnMain { viewModel.state.value.message == ClaimViewModel.TOO_MANY_PEOPLE }
        assertFalse(received.any { it.path.orEmpty().endsWith("/lock") })
    }

    @Test
    fun `sending marks the bill finished first, then sends what the calculator gives, asking for the user's method`() {
        runBlocking { methods.add(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN")) }
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share != null && viewModel.method.value != null }

        viewModel.send()
        awaitOnMain { received.any { it.path.orEmpty().endsWith("/finalize") } }

        assertTrue("The bill was marked finished before its copy was read", host.finishedBeforeRead)
        val sent = finalizeBody()
        val stored = checkNotNull(host.bill.value)
        val calculated = stored.plan.result(stored.bill).shares.associate { it.personId to it.totalMinor }
        val amounts = sent["amounts"]!!.jsonObject
        assertEquals(calculated, amounts.mapValues { (_, it) -> it.jsonObject["amountMinor"]!!.jsonPrimitive.content.toLong() })
        assertEquals("null", amounts["me"]!!.jsonObject["payment"].toString())
        assertTrue(amounts["p1"]!!.jsonObject["payment"]!!.jsonObject["qrPayload"]!!.jsonPrimitive.content.startsWith("000201"))
        assertEquals("\"me\"", sent["paidBy"].toString())
        assertEquals("No claim was matched before picking closed", "null", sent["matches"].toString())
        awaitOnMain { host.bill.value?.share?.settled == true }
        assertEquals(fingerprintOf(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN")), host.bill.value?.share?.method)
    }

    @Test
    fun `when someone else paid the whole bill, the page asks nobody to pay the user and names who paid`() {
        runBlocking { methods.add(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN")) }
        host.bill.value = ShareFixtures.saved.copy(plan = ShareFixtures.saved.plan.withPayments(Payments.single("p1", 154_000)))
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share != null && viewModel.method.value != null }

        viewModel.send()
        awaitOnMain { received.any { it.path.orEmpty().endsWith("/finalize") } }

        val sent = finalizeBody()
        assertTrue(sent["amounts"]!!.jsonObject.values.all { it.jsonObject["payment"].toString() == "null" })
        assertEquals("\"p1\"", sent["paidBy"].toString())
    }

    @Test
    fun `a bill in a group asks nobody to pay on its page, as people settle up in the group`() {
        runBlocking { methods.add(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN")) }
        host.bill.value = ShareFixtures.saved.copy(group = GroupLink("g1", "Chiang Mai trip", 1, emptyMap()))
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share != null && viewModel.method.value != null }

        viewModel.send()
        awaitOnMain { received.any { it.path.orEmpty().endsWith("/finalize") } }

        val sent = finalizeBody()
        assertTrue(sent["amounts"]!!.jsonObject.values.all { it.jsonObject["payment"].toString() == "null" })
        assertEquals("null", sent["paidBy"].toString())
    }

    @Test
    fun `a payment method changed since the amounts went is noticed`() {
        runBlocking { methods.add(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN")) }
        linked(
            ShareLink(
                ShareFixtures.SHARE_ID,
                ShareFixtures.URL,
                EXPIRES_AT,
                CREATED_AT,
                settled = true,
                method = fingerprintOf(PaymentMethod.VietQr("970416", "0000000000", "DANA NGUYEN")),
            ),
        )

        val viewModel = viewModel()

        awaitOnMain { viewModel.methodChanged.value }
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
    fun `a link another account made is left alone until the user asks for a new one`() {
        linkStatus = 404
        linked()
        val viewModel = viewModel()

        awaitOnMain { viewModel.state.value.otherAccount }
        assertFalse(received.any { it.method == "PUT" })

        viewModel.makeNewLink()
        awaitOnMain { received.any { it.method == "PUT" } }
    }

    @Test
    fun `a link that ended is replaced by a new one`() {
        linkStatus = 410
        linked()
        val viewModel = viewModel()

        awaitOnMain { received.any { it.method == "PUT" } && viewModel.state.value.share != null }

        assertFalse(viewModel.state.value.otherAccount)
    }

    @Test
    fun `signing in from the card starts the screen`() {
        proxy.account.clearSession()
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.needsSignIn }
        assertEquals(0, server.requestCount)

        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))

        awaitOnMain { viewModel.state.value.share != null }
        assertFalse(viewModel.state.value.needsSignIn)
    }

    @Test
    fun `the link is followed live only while the screen shows`() {
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.share != null }
        assertFalse(received.any { it.path == "/v1/shares/live" })

        viewModel.resume()
        awaitOnMain { viewModel.state.value.connected }
        viewModel.pause()

        awaitOnMain { !viewModel.state.value.connected }
    }

    @Test
    fun `a split by claims uses who each one is, adds each new person once, and drops people whose claims are gone`() {
        val saved = ShareFixtures.saved
        val claims = listOf(
            ShareClaim("minh", "Minh", listOf("i1")),
            ShareClaim("x", "Stranger", listOf("i2")),
            ShareClaim("empty", "Nobody", emptyList()),
        )

        val first = ClaimViewModel.planFor(saved, claims, mapOf("minh" to "p1"), emptyMap())

        assertEquals(listOf("me", "p1", "p2", "p3"), first.plan.participantIds)
        assertEquals(mapOf("x" to "p3"), first.added)
        val again = ClaimViewModel.planFor(saved.copy(plan = first.plan), claims, mapOf("minh" to "p1"), first.added)
        assertEquals("The person added before is used again", listOf("me", "p1", "p2", "p3"), again.plan.participantIds)
        val withoutStranger = ClaimViewModel.planFor(saved.copy(plan = first.plan), claims.take(1), mapOf("minh" to "p1"), first.added)
        assertEquals("Someone whose claim is gone leaves the bill", listOf("me", "p1", "p2"), withoutStranger.plan.participantIds)
        assertEquals(emptyMap<String, String>(), withoutStranger.added)
    }

    private fun finalizeBody() = Json.parseToJsonElement(
        received.first {
            it.path.orEmpty().endsWith("/finalize")
        }.body.readUtf8(),
    ).jsonObject

    /** The split screen, holding the one copy of the bill that is written; a save moves the revision on. */
    private class FakeHost(start: SavedBill?) : BillOnScreen {
        val bill = MutableStateFlow(start)
        override val billOnScreen: StateFlow<SavedBill?> = bill

        @Volatile
        var applied: SplitPlan? = null

        @Volatile
        private var finished = false

        /** Whether the bill was marked finished before the copy that sending reads. */
        @Volatile
        var finishedBeforeRead = false

        override suspend fun storedBill(): SavedBill? {
            if (finished) finishedBeforeRead = true
            return bill.value
        }

        override fun linkToShare(link: ShareLink?) {
            bill.value = bill.value?.copy(share = link)
        }

        override fun updateLink(shareId: String, transform: (ShareLink) -> ShareLink) {
            val current = bill.value ?: return
            if (current.share?.shareId == shareId) bill.value = current.copy(share = transform(current.share))
        }

        override fun applyPlan(plan: SplitPlan) {
            applied = plan
            bill.value = bill.value?.copy(plan = plan, revision = (bill.value?.revision ?: 0) + 1)
        }

        override fun finish() {
            finished = true
            bill.value = bill.value?.copy(status = BillStatus.FINAL, revision = (bill.value?.revision ?: 0) + 1)
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        const val NOW = 1_763_200_000_000L
        const val CREATED_AT = 1_763_190_000_000L
        const val EXPIRES_AT = 4_102_444_800_000L
    }
}
