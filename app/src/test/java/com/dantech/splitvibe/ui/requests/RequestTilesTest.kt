package com.dantech.splitvibe.ui.requests

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.RequestFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.AppNavBar
import com.dantech.splitvibe.ui.HomeKey
import com.dantech.splitvibe.ui.RequestsKey
import com.dantech.splitvibe.ui.common.ProxyWork
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.home.HomeSummary
import com.dantech.splitvibe.ui.home.Money
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The two tiles of the Requests screen show what the home shows, from the same holder of the totals
 * that the whole app reads: they add nothing up themselves, so the two screens never disagree, also
 * right after a sign-in, before the Requests tab was ever opened. Nothing known is not nothing owed,
 * so the tiles wait until the totals are known and there is a request to list.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class RequestTilesTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `right after a sign-in, before the Requests tab was ever opened, its tiles show what home shows`() {
        val lists = AtomicInteger()
        // The fetch of the Requests tab is held back, so that only the holder's fetch can be what its tiles show.
        val tabFetch = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/v1/requests") return MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
                if (lists.incrementAndGet() > 1) tabFetch.await(TIMEOUT_S, TimeUnit.SECONDS)
                return MockResponse().setHeader(
                    "Content-Type",
                    "application/json",
                ).setBody("""{"requests":[${ON_THE_PROXY.joinToString(",")}]}""")
            }
        }
        server.start()
        try {
            val proxy = TestProxy(server, context)
            val cache = ProxyCache(storage.database.proxyCache())
            val repository = RequestRepository(proxy.api, cache, proxy.account)
            val groups = GroupRepository(proxy.api, cache, proxy.account)
            // The holder lives as long as the activity, from before the sign-in.
            val holder = RequestOverviewViewModel(repository, groups, proxy.account.account).also(storage::closeWith)
            var onRequests by mutableStateOf(false)
            compose.setContent {
                val totals by holder.totals.collectAsState()
                SplitVibeTheme {
                    if (!onRequests) {
                        Home(totals)
                    } else {
                        // Made when the tab is first opened, as the app makes it.
                        val tab = remember { RequestsViewModel(repository, groups, proxy.account.account).also(storage::closeWith) }
                        val requests by tab.requests.collectAsState()
                        RequestsScreen(
                            requests = requests,
                            signedIn = true,
                            status = ProxyWork(),
                            actions = NO_ACTIONS,
                            totals = totals,
                            localCurrency = LOCAL_CURRENCY,
                            bottomBar = { AppNavBar(selected = RequestsKey, requests = totals, onSelect = {}, onSplitBill = {}) },
                        )
                    }
                }
            }
            assertEquals(RequestTotals.SignedOut, holder.totals.value)

            // As a sign-in does: what was kept for whoever was signed in before goes, then the session comes.
            runBlocking { cache.clear() }
            proxy.account.saveSession(Session("dana-session", GroupFixtures.FAR_FUTURE), signedInAs = "dana@example.com")
            await { holder.totals.value is RequestTotals.Loaded && shown(FRIENDS_OWE_YOU) }

            // The figures of the one holder, as home shows them.
            val totals = holder.totals.value as RequestTotals.Loaded
            val owed = VND.format(checkNotNull(totals.owedToYou[VND]))
            val owing = VND.format(checkNotNull(totals.youOwe[VND]))
            val baht = THB.format(checkNotNull(totals.owedToYou[THB]))
            assertEquals(listOf("₫651,671", "₫150,000"), listOf(owed, owing))
            compose.onNode(hasText(FRIENDS_OWE_YOU) and hasText(owed) and hasText(baht)).assertIsDisplayed()
            compose.onNode(hasText("You owe $owing to Dana")).assertIsDisplayed()
            // Only the holder asked for the requests.
            assertEquals(1, lists.get())

            onRequests = true
            await { shown(YOU_OWE) }

            // The tab's own fetch has not come back: what the tiles show is what the holder fetched.
            assertEquals(2, lists.get())
            compose.onNode(hasText(YOU_OWE) and hasText(owing)).assertIsDisplayed()
            compose.onNode(hasText(FRIENDS_OWE_YOU) and hasText(owed) and hasText(baht)).assertIsDisplayed()
            assertListEndsAboveTheBar()
        } finally {
            tabFetch.countDown()
        }
    }

    @Test
    fun `the tiles take their totals from the holder's totals, whatever the list holds`() {
        // The list on the screen and the totals are the holder's; the tiles read the totals alone.
        val tiles =
            checkNotNull(
                RequestTiles.of(
                    RequestTotals.of(signedIn = true, ON_THE_PROXY.map(RequestFixtures::view)),
                    RequestFixtures.board,
                    LOCAL_CURRENCY,
                ),
            )

        // Lan's ₫250,991 and Minh's ₫400,680, which he says he paid; not Minh's ₫150,000, which is confirmed, nor the baht first.
        assertEquals(RequestTiles.Side(Money(VND, 651_671), listOf(Money(THB, 18_700))), tiles.owedToYou)
        // Dana's ₫150,000, and not the ₫80,000 the user says they paid.
        assertEquals(RequestTiles.Side(Money(VND, 150_000), emptyList()), tiles.youOwe)
    }

    @Test
    fun `the user's own currency leads a tile, and one without a request in it is led by the newest`() {
        val bahtOnly = RequestTotals.of(signedIn = true, listOf(RequestFixtures.view(IN_BAHT)))

        val tiles = checkNotNull(RequestTiles.of(bahtOnly, listOf(RequestFixtures.view(IN_BAHT)), LOCAL_CURRENCY))

        assertEquals(RequestTiles.Side(Money(THB, 18_700), emptyList()), tiles.owedToYou)
        // Known, and nothing owed: no request to pay comes to nothing in the user's own currency.
        assertEquals(RequestTiles.Side(Money(VND, 0), emptyList()), tiles.youOwe)
    }

    @Test
    fun `no tile shows while nobody is signed in, before the totals are known, or while there is no request`() {
        val listed = RequestFixtures.board

        assertNull("signed out", RequestTiles.of(RequestTotals.SignedOut, listed, LOCAL_CURRENCY))
        // Not known yet is not nothing owed, even beside a list the phone still keeps.
        assertNull("not known yet", RequestTiles.of(RequestTotals.Unknown, listed, LOCAL_CURRENCY))
        assertNull("not listed yet", RequestTiles.of(RequestTotals.of(signedIn = true, listed), null, LOCAL_CURRENCY))
        assertNull("no request", RequestTiles.of(RequestTotals.of(signedIn = true, emptyList()), emptyList(), LOCAL_CURRENCY))
    }

    @Test
    fun `on the screen, totals not known yet show no tile and no zero`() {
        compose.setContent {
            SplitVibeTheme {
                RequestsScreen(
                    requests = RequestFixtures.board,
                    signedIn = true,
                    status = ProxyWork(),
                    actions = NO_ACTIONS,
                    totals = RequestTotals.Unknown,
                    localCurrency = LOCAL_CURRENCY,
                )
            }
        }

        compose.onAllNodesWithText(YOU_OWE).assertCountEquals(0)
        compose.onAllNodesWithText(FRIENDS_OWE_YOU).assertCountEquals(0)
        compose.onAllNodesWithText("₫0").assertCountEquals(0)
    }

    /** Home as the app shows it, with its navigation bar, reading [totals]. */
    @androidx.compose.runtime.Composable
    private fun Home(totals: RequestTotals) = HomeScreen(
        summary = HomeSummary.of(totals, bills = emptyList(), month = null, localCurrency = LOCAL_CURRENCY),
        group = null,
        bills = emptyList(),
        onOpenBill = {},
        onSeeAllBills = {},
        onOpenSettings = {},
        onOpenRequests = {},
        onOpenInsights = {},
        onOpenGroup = {},
        onSplitBill = {},
        bottomBar = { AppNavBar(selected = HomeKey, requests = totals, onSelect = {}, onSplitBill = {}) },
    )

    /**
     * Fails unless the list of requests ends above the navigation bar, as its own frame must, and the
     * last request still shows whole above the bar once the list is scrolled to it.
     */
    private fun assertListEndsAboveTheBar() {
        // The bar is the pill that holds the tabs, so the parent of a tab.
        val bar = compose.onNode(tab("Home")).onParent().getBoundsInRoot()
        val list = compose.onNode(hasScrollToIndexAction())
        val frame = list.getBoundsInRoot()
        assertTrue("The requests run on to ${frame.bottom}, under the top of the bar at ${bar.top}", frame.bottom <= bar.top)
        list.performScrollToNode(hasText(RequestFixtures.PAID))
        val last = compose.onNode(hasText(RequestFixtures.PAID)).getBoundsInRoot()
        assertTrue("The last request ends at ${last.bottom}, below the top of the bar at ${bar.top}", last.bottom <= bar.top)
    }

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Waits for what the database and the fake proxy answer on other threads, which reaches the main thread when its looper runs. */
    private fun await(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_S * 1_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun tab(name: String) = hasContentDescription(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private companion object {
        const val TIMEOUT_S = 10L
        const val LOCAL_CURRENCY = "VND"
        const val YOU_OWE = "You owe"
        const val FRIENDS_OWE_YOU = "Friends owe you"
        val VND = BillCurrency("VND", 0)
        val THB = BillCurrency("THB", 2)

        /** Lan's ride in baht, which she has not paid yet. */
        val IN_BAHT = RequestFixtures.request(
            "r5",
            RequestView.CREDITOR,
            18_700,
            payer = "Lan",
            title = "Grab",
            currency = """{"code":"THB","fractionDigits":2}""",
        )

        /** A request the user says they paid, which waits for Dana to confirm and so is no longer owed. */
        val SAID_PAID = RequestFixtures.request(
            "r6",
            RequestView.DEBTOR,
            80_000,
            payer = "Dana",
            title = "Phở Thìn",
            reportedPaidAt = 1_763_200_000_000,
        )

        /** What the proxy lists: the design's requests, the one in baht, and the one the user says they paid, the paid one last. */
        val ON_THE_PROXY = listOf(RequestFixtures.toPay, SAID_PAID, IN_BAHT) + RequestFixtures.asked

        val NO_ACTIONS = RequestActions(
            onReportPaid = {},
            onConfirmPaid = {},
            onRemind = {},
            onSetReminders = { _, _ -> },
            onDelete = {},
            onRefresh = {},
            onOpenSettings = {},
            onDismissMessage = {},
        )
    }
}
