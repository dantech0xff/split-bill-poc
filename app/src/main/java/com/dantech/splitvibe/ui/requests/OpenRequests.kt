package com.dantech.splitvibe.ui.requests

import com.dantech.splitvibe.domain.BillCurrency

/**
 * The open requests of one side, as the home and the requests screen show them. [main] is their
 * total in the user's own currency, or when there is none in it, in the currency of the newest
 * request; [others] are the totals in the other currencies, which nothing converts. [count] is how
 * many requests there are, and [people] the people on the other side, each once, the newest first.
 */
data class OpenRequests(val main: Money, val others: List<Money>, val count: Int, val people: List<String>) {
    companion object {
        /** The requests that add up to [totals], in the order of the requests; null when there are none. */
        internal fun of(totals: Map<BillCurrency, Long>, count: Int, people: List<String>, localCurrency: String): OpenRequests? {
            val amounts = totals.map { (currency, minor) -> Money(currency, minor) }
            val main = amounts.firstOrNull { it.currency.code == localCurrency } ?: amounts.firstOrNull() ?: return null
            return OpenRequests(main = main, others = amounts.filter { it !== main }, count = count, people = people)
        }
    }
}

/** An amount of money: [minor] units of [currency]. */
data class Money(val currency: BillCurrency, val minor: Long) {
    /** The amount as the phone's region writes it, such as "₫651,671", read when it is shown. */
    val text: String get() = currency.format(minor)
}
