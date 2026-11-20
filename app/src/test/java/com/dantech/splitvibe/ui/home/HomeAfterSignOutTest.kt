package com.dantech.splitvibe.ui.home

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Once the account signs out or is deleted, home shows nothing of it: no requests in its main
 * block, no group among its tiles, and no count on the Requests tab. What only the phone keeps, its
 * bills, stays, and so does this month's spending of them.
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
        // Dana's requests and her group, whatever is asked first; her account's deletion goes through.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/v1/requests" -> json("""{"requests":[$MINH_OWES_DANA,$DANA_OWES]}""")
                request.path == "/v1/groups/${GroupFixtures.GROUP_ID}" -> json(GroupFixtures.groupState())
                request.method == "DELETE" && request.path == "/v1/account" -> MockResponse().setResponseCode(204)
                else -> MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}""")
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
        compose.onNodeWithText("Phở Thìn").assertIsDisplayed()
    }

    /** Home as the app shows it, reading the requests from the one holder of their totals and the group from the cache. */
    private fun showHome() {
        val groups = GroupRepository(proxy.api, cache, proxy.account)
        runBlocking { groups.refresh(GroupFixtures.GROUP_ID) }
        val overview = RequestOverviewViewModel(RequestRepository(proxy.api, cache, proxy.account), proxy.account.account)
        val home = HomeViewModel(storage.bills, MutableStateFlow(AppSettings(localCurrency = "VND")), groups, SpendingFixtures.clock)
        storage.closeWith(overview)
        storage.closeWith(home)
        val listings = storage.bills.listings()
        compose.setContent {
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

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val FRIENDS_OWE_YOU = "Friends owe you"
        const val YOU_OWE_DANA = "You owe ₫150,000 to Dana"
        const val GROUP = "Chiang Mai trip"

        /** Minh is to pay Dana 150,000 dong for Pho 24. */
        val MINH_OWES_DANA = GroupFixtures.request(id = "r1", role = "creditor")

        /** Dana is to pay 150,000 dong, which waits for her. */
        val DANA_OWES = GroupFixtures.request(id = "r2", role = "debtor")

        /** A bill of this month on the phone, which no account owns. */
        val PHO = SpendingFixtures.finished("b1", "2026-11-12", "Phở Thìn", BillCategory.FOOD_AND_DRINK, 310_000, listOf("Minh", "Lan"))
    }
}
