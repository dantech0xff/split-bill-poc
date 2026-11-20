package com.dantech.splitvibe.ui.requests

import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.domain.BillCurrency

/**
 * What the user's payment requests add up to, as the whole app shows it: the count on the Requests
 * tab of the navigation bar, and the totals of the home and the requests screens, which all read
 * one [RequestOverviewViewModel] so that they never disagree.
 *
 * Nothing known is not the same as nothing owed: before the requests are fetched the totals are
 * [Unknown], never zero.
 */
sealed interface RequestTotals {

    /** Nobody is signed in, so there are no requests to add up; the screens ask to sign in instead. */
    data object SignedOut : RequestTotals

    /** Someone is signed in, and their requests have not been fetched yet. */
    data object Unknown : RequestTotals

    /**
     * The requests as the proxy last listed them, added up per currency, as nothing converts the
     * currency of a request.
     */
    data class Loaded(
        /** What friends owe the user: the requests the user made and has not confirmed as paid yet. */
        val owedToYou: Map<BillCurrency, Long>,
        /** What the user owes: the requests sent to the user that the user has not said were paid yet. */
        val youOwe: Map<BillCurrency, Long>,
        /**
         * How many requests wait for the user to act: one the user owes that nobody has said anything
         * about yet, or one owed to the user that its payer says was paid, for the user to confirm.
         */
        val waitingForYou: Int,
    ) : RequestTotals

    companion object {
        /** The totals of [requests], the list kept on the phone, which is null before it was ever fetched. */
        fun of(signedIn: Boolean, requests: List<RequestView>?): RequestTotals = when {
            !signedIn -> SignedOut

            requests == null -> Unknown

            else -> Loaded(
                owedToYou = requests.filter { it.isMine && it.status != RequestStatus.PAID }.totals(),
                youOwe = requests.filter { !it.isMine && it.status == RequestStatus.WAITING }.totals(),
                waitingForYou = requests.count {
                    if (it.isMine) it.status == RequestStatus.REPORTED_PAID else it.status == RequestStatus.WAITING
                },
            )
        }

        private fun List<RequestView>.totals(): Map<BillCurrency, Long> = groupBy { it.bill }.mapValues { (_, inOneCurrency) ->
            inOneCurrency.sumOf { it.amountMinor }
        }
    }
}

/**
 * The count on the Requests tab: the requests that wait for the user, and none while nobody is
 * signed in or the requests are not fetched yet.
 */
val RequestTotals.waitingCount: Int get() = (this as? RequestTotals.Loaded)?.waitingForYou ?: 0
