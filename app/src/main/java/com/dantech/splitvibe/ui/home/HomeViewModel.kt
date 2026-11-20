package com.dantech.splitvibe.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.ui.insights.InsightsPeriod
import com.dantech.splitvibe.ui.insights.insightsOf
import java.time.Clock
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the home shows beside the bills and the requests the app holds already: this month's
 * spending, as the insights add it up, and the group the phone kept last. Neither asks the proxy
 * for anything. [clock] tells the month, in the zone the phone is in.
 */
class HomeViewModel(bills: BillRepository, settings: StateFlow<AppSettings>, groups: GroupRepository, clock: Clock) : ViewModel() {

    private val localCurrency = settings.map { it.localCurrency }.distinctUntilChanged()

    /**
     * The user's share of this month's finished bills, in their own currency; null until the bills
     * have been read, and when their amounts are beyond adding up.
     */
    val month: StateFlow<MonthSpending?> = combine(bills.finished(), localCurrency) { finished, local ->
        // One moment for both, so the month named is the one added up, also at midnight on its last day.
        val now = Clock.fixed(clock.instant(), clock.zone)
        try {
            MonthSpending.of(YearMonth.now(now), insightsOf(finished, local, InsightsPeriod.THIS_MONTH, now).insights)
        } catch (e: ArithmeticException) {
            // Amounts far beyond any real bills, typed by mistake, cannot be added up. The home is
            // the first screen of the app, so it leaves the spending out rather than stop the app.
            null
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    /** The group kept last on the phone, with the user's balance in it; null while none is kept or its balance is not known. */
    val group: StateFlow<GroupTile?> = groups.latestGroup
        .map(GroupTile::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    private companion object {
        /** Long enough for the home to come back from a rotation, or from a short visit to another screen, without reading again. */
        const val STOP_AFTER_MS = 5_000L
    }
}
