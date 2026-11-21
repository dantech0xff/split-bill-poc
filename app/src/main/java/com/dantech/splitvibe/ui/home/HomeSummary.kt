package com.dantech.splitvibe.ui.home

import androidx.annotation.StringRes
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.domain.Insights
import com.dantech.splitvibe.ui.groups.GroupStanding
import com.dantech.splitvibe.ui.requests.Money
import com.dantech.splitvibe.ui.requests.OpenRequests
import com.dantech.splitvibe.ui.requests.RequestTotals
import java.time.YearMonth

/**
 * What the home shows above its recent bills: its [main] block, and beside a block about the
 * requests the tile of this month's spending. [of] works it out from what the app already holds:
 * the requests as the one holder of their totals adds them up for the whole app, the bills kept on
 * the phone, and this month's spending. The home never adds up a request itself, so it cannot
 * disagree with the Requests tab.
 */
data class HomeSummary(
    /** The main block, or null while what it depends on is still being read. */
    val main: HomeBlock?,
    /** This month's spending as a tile below the main block, or null when the main block shows it or there is none to show. */
    val month: MonthSpending?,
) {
    companion object {
        /**
         * What the home shows for [requests], for the [bills] the phone keeps, null until they are
         * read, and for [month], this month's spending, null until it is worked out, to a user who
         * counts in [localCurrency], a currency code.
         *
         * Open requests come first: what friends owe the user, or else what the user owes, also on
         * a phone that keeps no bill. Without an open request, as when nobody is signed in or the
         * requests are not fetched yet, the home shows this month's spending, or on a phone that
         * keeps no bill the invitation to split a first one. Requests that come in later turn the
         * block into theirs.
         */
        fun of(requests: RequestTotals, bills: List<BillListing>?, month: MonthSpending?, localCurrency: String): HomeSummary {
            val loaded = requests as? RequestTotals.Loaded
            val owed = loaded?.let { OpenRequests.of(it.owedToYou, it.owedToYouCount, it.owedBy, localCurrency) }
            val owing = loaded?.let { OpenRequests.of(it.youOwe, it.youOweCount, it.youOweTo, localCurrency) }
            val main = when {
                owed != null -> HomeBlock.FriendsOweYou(owed = owed, youOwe = owing)
                owing != null -> HomeBlock.YouOwe(owing)
                bills == null -> null
                bills.isEmpty() -> HomeBlock.Invitation
                else -> month?.let(HomeBlock::ThisMonth)
            }
            // Beside the requests only, and only on a phone with bills to spend on.
            val aboutRequests = main is HomeBlock.FriendsOweYou || main is HomeBlock.YouOwe
            return HomeSummary(main = main, month = month.takeIf { aboutRequests && !bills.isNullOrEmpty() })
        }
    }
}

/** The block at the top of the home, which says the one thing the user most needs to know there. */
sealed interface HomeBlock {

    /** What friends owe the user, and below it [youOwe], what the user owes them, when anything. */
    data class FriendsOweYou(val owed: OpenRequests, val youOwe: OpenRequests?) : HomeBlock

    /** What the user owes, when nobody owes them anything. */
    data class YouOwe(val owing: OpenRequests) : HomeBlock

    /** What the user's own share of this month's finished bills comes to, when no request is open. */
    data class ThisMonth(val spending: MonthSpending) : HomeBlock

    /** The invitation to split a first bill, on a phone that keeps none, when no request is open either. */
    data object Invitation : HomeBlock
}

/** What the user's own share of the finished bills of [month] comes to, in their own currency, over [bills] bills. */
data class MonthSpending(val month: YearMonth, val myShare: Money, val bills: Int) {
    companion object {
        /** The spending of [month] as [insights] of its finished bills add it up. */
        fun of(month: YearMonth, insights: Insights): MonthSpending =
            MonthSpending(month = month, myShare = Money(insights.currency, insights.overall.myShareMinor), bills = insights.overall.bills)
    }
}

/**
 * The group whose state the phone kept last, by a refresh or by a change, with the user's own
 * [balance] in it: above zero what the group owes the user, below zero what the user owes it.
 */
data class GroupTile(val id: String, val name: String, val balance: Money) {

    /**
     * What the tile calls the balance: what the user gets back, what they owe, or that they are
     * settled up, in the words the group's own screen says it in.
     */
    @get:StringRes
    val standing: Int get() = GroupStanding(balance.minor).label

    /** The balance without its sign, which [standing] says. */
    val amount: Money get() = balance.copy(minor = GroupStanding(balance.minor).amountMinor)

    companion object {
        /**
         * The tile of [group]; null without a group, or where the group's own screen shows no
         * standing either, as when its numbers are beyond adding up, so the balance is not known.
         */
        fun of(group: GroupState?): GroupTile? {
            if (group == null) return null
            val standing = GroupStanding.of(group) ?: return null
            return GroupTile(id = group.group.id, name = group.group.name, balance = Money(group.currency, standing.balanceMinor))
        }
    }
}
