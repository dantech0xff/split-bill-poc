package com.dantech.splitvibe.ui.home

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.PlainCipher
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillCurrency
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** What home reads beside the requests: this month's spending of the finished bills, and the group kept last. */
@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val cache = ProxyCache(storage.database.proxyCache())

    // Nothing here asks the proxy: the group comes from what the phone keeps.
    private val account = AccountStore(context, proxySetUp = false, cipher = PlainCipher)
    private val groups = GroupRepository(ProxyApi("", account, appVersion = TestProxy.APP_VERSION), cache, account)
    private val trip = ProxyCache.json.decodeFromString(GroupState.serializer(), GroupFixtures.groupState())

    @After
    fun tearDown() = storage.close()

    @Test
    fun `this month's spending is the user's share of the month's finished bills, as the insights add them up`() {
        runBlocking { SpendingFixtures.year.forEach { storage.bills.save(it) } }
        val home = home()

        home.month.launchIn(home.viewModelScope)
        awaitOnMain { home.month.value != null }

        // Six of November's eight finished bills count; the two without a rate to dong are left out.
        assertEquals(MonthSpending(YearMonth.of(2026, 11), Money(BillCurrency("VND", 0), 3_823_038), bills = 6), home.month.value)
    }

    @Test
    fun `the group tile is that of the group the phone kept last, for the account signed in`() {
        account.saveSession(Session("dana-session", GroupFixtures.FAR_FUTURE), signedInAs = "dana@example.com")
        val home = home()

        keepTheTrip()
        awaitOnMain { home.group.value != null }

        assertEquals(GroupTile.of(trip), home.group.value)
    }

    @Test
    fun `with nobody signed in there is no group tile, also while the phone still keeps the group`() {
        account.saveSession(Session("dana-session", GroupFixtures.FAR_FUTURE), signedInAs = "dana@example.com")
        val home = home()
        keepTheTrip()
        awaitOnMain { home.group.value != null }

        // As when a deletion could not empty what the phone keeps: the session is gone, the group still kept.
        account.clearSession()
        awaitOnMain { home.group.value == null }

        assertNotNull("the phone still keeps the group", runBlocking { groups.cached(GroupFixtures.GROUP_ID) })
    }

    @Test
    fun `a sign-in that ran out still holds its place, and its group stays until another account signs in`() {
        account.saveSession(Session("dana-session", GroupFixtures.FAR_FUTURE), signedInAs = "dana@example.com")
        val home = home()
        keepTheTrip()
        awaitOnMain { home.group.value != null }

        account.expireSession()
        settle()
        assertEquals(GroupTile.of(trip), home.group.value)

        // As a sign-in does: what was kept for whoever was signed in before goes, then the session comes.
        runBlocking { cache.clear() }
        account.saveSession(Session("minh-session", GroupFixtures.FAR_FUTURE), signedInAs = "minh.tran@example.com")
        awaitOnMain { home.group.value == null }
    }

    private fun keepTheTrip() = runBlocking {
        cache.put("group:${GroupFixtures.GROUP_ID}", GroupState.serializer(), trip, cache.generation)
    }

    /** Gives work started on other threads the time to report back, where nothing is to change. */
    private fun settle() {
        val until = System.currentTimeMillis() + SETTLE_MS
        awaitOnMain { System.currentTimeMillis() > until }
    }

    private fun home() = HomeViewModel(
        storage.bills,
        MutableStateFlow(AppSettings(localCurrency = "VND")),
        groups,
        account.account,
        SpendingFixtures.clock,
    ).also(storage::closeWith)

    private companion object {
        const val SETTLE_MS = 300L
    }
}
