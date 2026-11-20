package com.dantech.splitvibe.ui.requests

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.groups.CurrencyDto
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.domain.BillCurrency
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the payment requests add up to, and how the one holder of those totals that the whole app
 * reads keeps them: fetched when the app opens signed in and after each sign-in, quietly.
 */
@RunWith(AndroidJUnit4::class)
class RequestTotalsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `what friends owe is added up per currency`() {
        val totals = loaded(
            asked(150_000),
            asked(50_000, reported = true),
            asked(18_000, currency = THB),
        )

        assertEquals(mapOf(VND to 200_000L, THB.toBillCurrency() to 18_000L), totals.owedToYou)
    }

    @Test
    fun `a request confirmed as paid counts no more`() {
        val totals = loaded(asked(150_000), asked(70_000, reported = true, confirmed = true), owed(30_000, confirmed = true))

        assertEquals(mapOf(VND to 150_000L), totals.owedToYou)
        assertEquals(emptyMap<BillCurrency, Long>(), totals.youOwe)
    }

    @Test
    fun `what the user owes leaves out what they said they paid`() {
        val totals = loaded(owed(150_000), owed(80_000, reported = true), owed(1_250, currency = THB))

        assertEquals(mapOf(VND to 150_000L, THB.toBillCurrency() to 1_250L), totals.youOwe)
    }

    @Test
    fun `what waits for the user is what they owe that nobody spoke of, and what its payer says was paid to them`() {
        val totals = loaded(
            // Owed by the user, nobody said anything: the user is to pay.
            owed(150_000),
            owed(20_000, currency = THB),
            // The user said they paid: the other side is to confirm.
            owed(80_000, reported = true),
            // Owed to the user, its payer said they paid: the user is to confirm.
            asked(50_000, reported = true),
            // Owed to the user, nobody said anything: the payer is to pay.
            asked(70_000),
            asked(90_000, reported = true, confirmed = true),
        )

        assertEquals(3, totals.waitingForYou)
        assertEquals(3, totals.waitingCount)
    }

    @Test
    fun `the open requests on each side are counted, and the people on the other side named once each, the newest first`() {
        val totals = loaded(
            asked(150_000, payer = "Minh"),
            asked(18_000, currency = THB, payer = " Lan "),
            asked(50_000, reported = true, payer = "Minh"),
            // Confirmed as paid: neither counted nor named.
            asked(70_000, reported = true, confirmed = true, payer = "Khoa"),
            owed(150_000, creditor = "Dana"),
            owed(20_000, creditor = "Bao"),
            owed(30_000, creditor = "Dana"),
            // Paid as the user says, and one without a name: the first is not counted, the second names nobody.
            owed(80_000, reported = true, creditor = "Tuan"),
            owed(10_000, creditor = "  "),
        )

        assertEquals(3, totals.owedToYouCount)
        assertEquals(listOf("Minh", "Lan"), totals.owedBy)
        assertEquals(4, totals.youOweCount)
        assertEquals(listOf("Dana", "Bao"), totals.youOweTo)
    }

    @Test
    fun `requests never fetched are unknown, which is not the same as none`() {
        assertEquals(RequestTotals.Unknown, RequestTotals.of(signedIn = true, requests = null))
        assertEquals(
            RequestTotals.Loaded(
                emptyMap(),
                emptyMap(),
                0,
                owedToYouCount = 0,
                owedBy = emptyList(),
                youOweCount = 0,
                youOweTo = emptyList(),
            ),
            RequestTotals.of(signedIn = true, requests = emptyList()),
        )
        // The tab shows a count in neither case.
        assertEquals(0, RequestTotals.Unknown.waitingCount)
    }

    @Test
    fun `signed out there are no totals, whatever the phone still keeps`() {
        assertEquals(RequestTotals.SignedOut, RequestTotals.of(signedIn = false, requests = listOf(owed(150_000))))
        assertEquals(RequestTotals.SignedOut, RequestTotals.of(signedIn = false, requests = null))
        assertEquals(0, RequestTotals.SignedOut.waitingCount)
    }

    @Test
    fun `opened signed in, the holder fetches the requests once and adds them up`() {
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(requestList(GroupFixtures.request(id = "r1", role = "debtor"), GroupFixtures.request(id = "r2", reportedPaidAt = 1)))

        val holder = holder(proxy)
        awaitOnMain { holder.totals.value is RequestTotals.Loaded }

        assertEquals(2, holder.totals.value.waitingCount)
        assertEquals("/v1/requests", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a sign-in makes the holder fetch the requests once, with the session of the account that signed in`() {
        val proxy = TestProxy(server, context)
        server.enqueue(requestList(GroupFixtures.request(id = "r1", role = "debtor")))
        val cache = ProxyCache(storage.database.proxyCache())
        val holder = holder(proxy, cache)
        assertEquals(RequestTotals.SignedOut, holder.totals.value)

        // As a sign-in does: what was kept for whoever was signed in before goes, then the session comes.
        runBlocking { cache.clear() }
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        awaitOnMain { holder.totals.value is RequestTotals.Loaded }
        assertEquals(1, holder.totals.value.waitingCount)
        assertEquals("Bearer signed-session", server.takeRequest().getHeader("Authorization"))

        // Whatever else changes about the account, such as the calls it has left, is no new sign-in.
        proxy.account.onCallsLeft(CallsLeft(BigDecimal.TEN, BigDecimal.ONE, BigDecimal(9), "2026-11", resetsAt = null))
        proxy.account.onSessionAccepted(Session("signed-session", GroupFixtures.FAR_FUTURE))
        settle()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a fetch that fails says nothing, and leaves the totals unknown`() {
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"internal"}"""))

        val holder = holder(proxy)
        awaitOnMain { server.requestCount == 1 }
        // What the failure brings back to the main thread has run: the holder is still there, and still knows nothing.
        settle()

        assertEquals(RequestTotals.Unknown, holder.totals.value)
    }

    @Test
    fun `after another account signs in, the totals never show the requests the account before it left on the phone`() {
        val proxy = TestProxy(server, context)
        val cache = ProxyCache(storage.database.proxyCache())
        val secondAnswered = CountDownLatch(1)
        // Two requests wait for Minh; one waits for Lan, whose own list is held back until the test lets it go.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer minh-session" -> requestList(MINH_TO_PAY, MINH_TO_CONFIRM)

                else -> {
                    secondAnswered.await(TIMEOUT_S, TimeUnit.SECONDS)
                    requestList(GroupFixtures.request(id = "r3", role = "debtor"))
                }
            }
        }
        try {
            proxy.account.saveSession(Session("minh-session", GroupFixtures.FAR_FUTURE), signedInAs = "minh.tran@example.com")
            val holder = holder(proxy, cache)
            awaitOnMain { holder.totals.value is RequestTotals.Loaded }
            assertEquals(2, holder.totals.value.waitingCount)

            // Minh's sign-in runs out, which leaves his requests on the phone; then Lan signs in.
            proxy.account.expireSession()
            awaitOnMain { holder.totals.value == RequestTotals.SignedOut }
            val seen = mutableListOf<RequestTotals>()
            holder.totals.onEach { seen += it }.launchIn(holder.viewModelScope)
            runBlocking { cache.clear() }
            proxy.account.saveSession(Session("lan-session", GroupFixtures.FAR_FUTURE), signedInAs = "lan@example.com")
            awaitOnMain { server.requestCount == 2 }
            settle()

            // Until Lan's own requests come, nothing is known of them, and nothing of Minh's shows.
            assertEquals("$seen", emptyList<RequestTotals>(), seen.filterIsInstance<RequestTotals.Loaded>())
            secondAnswered.countDown()
            awaitOnMain { holder.totals.value is RequestTotals.Loaded }
            assertEquals(1, holder.totals.value.waitingCount)
            assertEquals(listOf(1), seen.filterIsInstance<RequestTotals.Loaded>().map { it.waitingForYou }.distinct())
        } finally {
            secondAnswered.countDown()
        }
    }

    @Test
    fun `a fetch still waiting for an account that signed out does not hold up the fetch of the next account`() {
        val proxy = TestProxy(server, context)
        val cache = ProxyCache(storage.database.proxyCache())
        val firstAnswered = CountDownLatch(1)
        // Minh's fetch hangs, as it may for minutes on a bad connection; Lan's answers at once.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.getHeader("Authorization")) {
                "Bearer minh-session" -> {
                    firstAnswered.await(TIMEOUT_S, TimeUnit.SECONDS)
                    MockResponse().setResponseCode(503)
                }

                else -> requestList(GroupFixtures.request(id = "r3", role = "debtor"))
            }
        }
        try {
            proxy.account.saveSession(Session("minh-session", GroupFixtures.FAR_FUTURE), signedInAs = "minh.tran@example.com")
            val holder = holder(proxy, cache)
            awaitOnMain { server.requestCount == 1 }

            // Minh signs out while his fetch still waits, and Lan signs in.
            proxy.account.clearSession()
            runBlocking { cache.clear() }
            proxy.account.saveSession(Session("lan-session", GroupFixtures.FAR_FUTURE), signedInAs = "lan@example.com")

            awaitOnMain { holder.totals.value is RequestTotals.Loaded }
            assertEquals(1, holder.totals.value.waitingCount)
        } finally {
            firstAnswered.countDown()
        }
    }

    @Test
    fun `once signed out the totals are gone`() {
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(requestList(GroupFixtures.request(id = "r1", role = "debtor")))
        val holder = holder(proxy)
        awaitOnMain { holder.totals.value is RequestTotals.Loaded }

        proxy.account.clearSession()
        awaitOnMain { holder.totals.value == RequestTotals.SignedOut }

        assertEquals(0, holder.totals.value.waitingCount)
    }

    /** Gives work started on other threads, such as a request or a read of the database, the time to report back. */
    private fun settle() {
        val until = System.currentTimeMillis() + SETTLE_MS
        awaitOnMain { System.currentTimeMillis() > until }
    }

    private fun holder(proxy: TestProxy, cache: ProxyCache = ProxyCache(storage.database.proxyCache())): RequestOverviewViewModel =
        RequestOverviewViewModel(RequestRepository(proxy.api, cache, proxy.account), proxy.account.account).also(storage::closeWith)

    private fun requestList(vararg requests: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody("""{"requests":[${requests.joinToString(",")}]}""")

    private fun loaded(vararg requests: RequestView): RequestTotals.Loaded =
        RequestTotals.of(signedIn = true, requests = requests.toList()) as RequestTotals.Loaded

    /** A request the user made, for a friend called [payer] to pay. */
    private fun asked(
        amountMinor: Long,
        currency: CurrencyDto = DONG,
        reported: Boolean = false,
        confirmed: Boolean = false,
        payer: String = "Minh",
    ) = request(RequestView.CREDITOR, amountMinor, currency, reported, confirmed, payer = payer, creditor = "Dana")

    /** A request sent to the user, to pay to [creditor]. */
    private fun owed(
        amountMinor: Long,
        currency: CurrencyDto = DONG,
        reported: Boolean = false,
        confirmed: Boolean = false,
        creditor: String? = null,
    ) = request(RequestView.DEBTOR, amountMinor, currency, reported, confirmed, payer = "Dana", creditor = creditor)

    private fun request(
        role: String,
        amountMinor: Long,
        currency: CurrencyDto,
        reported: Boolean,
        confirmed: Boolean,
        payer: String,
        creditor: String?,
    ) = RequestView(
        id = "r$amountMinor-$role",
        role = role,
        amountMinor = amountMinor,
        currency = currency,
        payerName = payer,
        creditorName = creditor,
        createdAt = 1_763_190_000_000,
        reportedPaidAt = if (reported) 1_763_200_000_000 else null,
        confirmedPaidAt = if (confirmed) 1_763_210_000_000 else null,
    )

    private companion object {
        /** Long enough for what runs off the main thread to have come back to it. */
        const val SETTLE_MS = 300L

        /** How long the fake proxy holds an answer back at most, so that no test can hang. */
        const val TIMEOUT_S = 10L

        /** A request Minh is to pay, and one Minh is to confirm: the two that wait for him. */
        val MINH_TO_PAY = GroupFixtures.request(id = "r1", role = "debtor")
        val MINH_TO_CONFIRM = GroupFixtures.request(id = "r2", reportedPaidAt = 1)
        val DONG = CurrencyDto("VND", 0)
        val VND = DONG.toBillCurrency()
        val THB = CurrencyDto("THB", 2)
    }
}
