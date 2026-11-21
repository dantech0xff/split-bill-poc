package com.dantech.splitvibe.ui.home

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.FakeGoogleAccounts
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.ui.AppNavBar
import com.dantech.splitvibe.ui.HomeKey
import com.dantech.splitvibe.ui.requests.RequestOverviewViewModel
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Once the account signs out or is deleted, or another account signs in in its place, home shows
 * nothing of it: no requests in its main block, no group among its tiles, and no count on the
 * Requests tab, also in the first frame home draws when it comes back from Settings, where all of
 * that is done. What only the phone keeps, its bills, stays, and so does this month's spending of them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class HomeAfterSignOutTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private val cache = ProxyCache(storage.database.proxyCache())
    private lateinit var proxy: TestProxy
    private lateinit var account: AccountRepository

    @Before
    fun setUp() {
        // Dana's requests and her group, whatever is asked first, and none for Minh, who signs in on
        // the same phone; Dana's account's deletion goes through.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val dana = request.getHeader("Authorization") == "Bearer dana-session"
                return when {
                    request.path == "/v1/requests" -> json(
                        if (dana) """{"requests":[$MINH_OWES_DANA,$DANA_OWES]}""" else """{"requests":[]}""",
                    )

                    request.path == "/v1/groups/${GroupFixtures.GROUP_ID}" && dana -> json(GroupFixtures.groupState())

                    request.path == "/v1/session" -> json("""{"session":"minh-session","expiresAt":${GroupFixtures.FAR_FUTURE}}""")

                    request.method == "DELETE" && request.path == "/v1/account" -> MockResponse().setResponseCode(204)

                    else -> MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
                }
            }
        }
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("dana-session", GroupFixtures.FAR_FUTURE), signedInAs = "dana@example.com")
        account = proxy.accountRepository(google = FakeGoogleAccounts(), cache = cache, phone = storage.phoneData(context))
        runBlocking { storage.bills.save(PHO) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `after signing out, home shows no request, no group and no count of the account`() {
        showHome()
        awaitTheAccount()

        runBlocking { account.signOut(context) }

        awaitNothingOfTheAccount()
    }

    @Test
    fun `after the account is deleted, home shows nothing of it either, and the phone's own bills stay`() {
        showHome()
        awaitTheAccount()

        runBlocking { account.deleteAccount(withPhoneData = false).join() }

        awaitNothingOfTheAccount()
        compose.onNodeWithText("Phở sáng").assertIsDisplayed()
    }

    @Test
    fun `back from Settings after signing out there, the first frame of home shows nothing of the group`() {
        showHome()
        awaitTheAccount()
        leaveHome()

        runBlocking { account.signOut(context) }

        comeBackHome()
        awaitNothingOfTheAccount()
    }

    @Test
    fun `back from Settings after another account signed in there, the first frame of home shows nothing of the old group`() {
        showHome()
        awaitTheAccount()
        leaveHome()

        // Dana's sign-in runs out, and Minh signs in in her place, which empties what the phone kept of her.
        proxy.account.expireSession()
        runBlocking { account.signIn(context) }
        assertEquals("minh.tran@example.com", proxy.account.account.value.signedInAs)

        comeBackHome()
        awaitNothingOfTheAccount()
    }

    /** Home as the app shows it, reading the requests from the one holder of their totals and the group from the cache. */
    private fun showHome() {
        val groups = GroupRepository(proxy.api, cache, proxy.account)
        runBlocking { groups.refresh(GroupFixtures.GROUP_ID) }
        val overview = RequestOverviewViewModel(RequestRepository(proxy.api, cache, proxy.account), groups, proxy.account.account)
        val home = HomeViewModel(
            storage.bills,
            MutableStateFlow(AppSettings(localCurrency = "VND")),
            groups,
            proxy.account.account,
            SpendingFixtures.clock,
        )
        storage.closeWith(overview)
        storage.closeWith(home)
        val listings = storage.bills.listings()
        compose.setContent {
            // Away from home, as on the Settings screen, home is not drawn and reads nothing.
            if (!homeShown) return@setContent
            val totals by overview.totals.collectAsState()
            val month by home.month.collectAsState()
            val group by home.group.collectAsState()
            val bills: List<BillListing>? by listings.collectAsState(initial = null)
            SplitVibeTheme {
                HomeScreen(
                    summary = HomeSummary.of(totals, bills, month, localCurrency = "VND"),
                    group = group,
                    bills = bills,
                    onOpenBill = {},
                    onSeeAllBills = {},
                    onOpenSettings = {},
                    onOpenRequests = {},
                    onOpenInsights = {},
                    onOpenGroup = {},
                    onSplitBill = {},
                    today = LocalDate.now(SpendingFixtures.clock),
                    bottomBar = { AppNavBar(selected = HomeKey, requests = totals, onSelect = {}, onSplitBill = {}) },
                )
            }
        }
    }

    /**
     * Takes home off the screen for longer than it goes on reading what it shows once it is not shown,
     * as when Settings is open, where the account signs out, is deleted, or another signs in.
     */
    private fun leaveHome() {
        homeShown = false
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(AWAY)
    }

    /**
     * Shows home again once what an account change set off has reported back, and stops at its first
     * frame, the one drawn from what home kept while it was away; the clock runs on as usual after it.
     */
    private fun comeBackHome() {
        settle()
        compose.mainClock.autoAdvance = false
        homeShown = true
        // A frame or more can pass before home is drawn at all; the one to check is the first that has it.
        var frames = 0
        while (!shown(RECENT_BILLS)) {
            check(++frames <= MAX_FRAMES) { "Home was not drawn again within $MAX_FRAMES frames" }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onAllNodesWithText(GROUP).assertCountEquals(0)
        compose.onAllNodesWithText("You get back").assertCountEquals(0)
        compose.mainClock.autoAdvance = true
    }

    /** Gives work started on other threads, such as a read of the database, the time to report back. */
    private fun settle() {
        val until = System.currentTimeMillis() + SETTLE_MS
        while (System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    /** What friends owe Dana, her group, and the request that waits for her, counted on the Requests tab. */
    private fun awaitTheAccount() {
        await { shown(FRIENDS_OWE_YOU) && shown(GROUP) }
        compose.onNodeWithText(FRIENDS_OWE_YOU).assertIsDisplayed()
        compose.onNodeWithText(YOU_OWE_DANA).assertIsDisplayed()
        compose.onNodeWithText(GROUP).assertIsDisplayed()
        compose.onAllNodes(tab("Requests, 1")).assertCountEquals(1)
    }

    private fun awaitNothingOfTheAccount() {
        await { !shown(FRIENDS_OWE_YOU) && !shown(GROUP) }
        for (text in listOf(FRIENDS_OWE_YOU, YOU_OWE_DANA, GROUP, "You get back")) {
            compose.onAllNodesWithText(text).assertCountEquals(0)
        }
        // The tab is there, with no count.
        compose.onAllNodes(tab("Requests")).assertCountEquals(1)
        compose.onAllNodes(tab("Requests, 1")).assertCountEquals(0)
        // This month's spending of the bill the phone keeps takes the place of the requests.
        compose.onNodeWithText("Your share of 1 bill").assertIsDisplayed()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Waits for what the database and the fake proxy answer on other threads, which reaches the main thread when its looper runs. */
    private fun await(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun tab(name: String) = hasContentDescription(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private var homeShown by mutableStateOf(true)

    private companion object {
        const val TIMEOUT_MS = 10_000L

        /** Longer than home goes on reading what it shows once it is not shown. */
        val AWAY: Duration = Duration.ofSeconds(6)

        /** Long enough for what runs off the main thread to have come back to it. */
        const val SETTLE_MS = 300L

        /** Far more frames than home takes to be drawn again, so that a home that never is fails rather than hangs. */
        const val MAX_FRAMES = 10

        /** The title above the bills, which home always shows, whatever it knows of the account. */
        const val RECENT_BILLS = "Recent bills"
        const val FRIENDS_OWE_YOU = "Friends owe you"
        const val YOU_OWE_DANA = "You owe ₫150,000 to Dana"
        const val GROUP = "Chiang Mai trip"

        /** Minh is to pay Dana 150,000 dong for Pho 24. */
        val MINH_OWES_DANA = GroupFixtures.request(id = "r1", role = "creditor")

        /** Dana is to pay 150,000 dong, which waits for her. */
        val DANA_OWES = GroupFixtures.request(id = "r2", role = "debtor")

        /** A bill of this month on the phone, which no account owns. */
        val PHO = SpendingFixtures.finished("b1", "2026-11-12", "Phở sáng", BillCategory.FOOD_AND_DRINK, 310_000, listOf("Minh", "Lan"))
    }
}
