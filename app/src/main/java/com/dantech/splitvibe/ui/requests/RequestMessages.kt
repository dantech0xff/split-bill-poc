package com.dantech.splitvibe.ui.requests

import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.domain.payments.PaymentRequest

/** The text of a request as it goes into a chat: who owes what for what, and how to pay it. */
object RequestMessages {

    /**
     * The request to [name] for [amountMinor] of what [title] names, with what to pay it with. [link]
     * is the request's own link on the proxy, with which the payer can say they paid; null when the
     * request is not kept there.
     */
    fun request(
        name: String,
        amountMinor: Long,
        currency: BillCurrency,
        title: String?,
        breakdown: String?,
        method: PaymentMethod?,
        paying: PaymentRequest?,
        link: String?,
    ): String = buildList {
        val what = title?.takeIf { it.isNotBlank() } ?: "the bill"
        add("Hi $name! Your share of $what is ${currency.format(amountMinor)}.")
        breakdown?.let(::add)
        if (method != null && paying != null) {
            add("")
            add(howToPay(method, paying))
            add(paying.details)
        }
        link?.let {
            add("")
            add("Open it in Split Vibe to say when you have paid: $it")
        }
    }.joinToString("\n")

    /** A friendly nudge about a request that is still open. */
    fun reminder(name: String, amountMinor: Long, currency: BillCurrency, title: String?, paying: PaymentRequest?, link: String?): String =
        buildList {
            val what = title?.takeIf { it.isNotBlank() }?.let { " for $it" }.orEmpty()
            add("Hi $name, a reminder about the ${currency.format(amountMinor)}$what.")
            paying?.let {
                add("")
                add(it.details)
            }
            link?.let {
                add("")
                add(it)
            }
        }.joinToString("\n")

    private fun howToPay(method: PaymentMethod, paying: PaymentRequest): String {
        val code = paying.qrPayload != null
        val link = paying.link?.takeIf { it != paying.qrPayload || !code }
        return when {
            code && link != null -> "Pay with ${method.title}: scan the code or open $link"
            code -> "Pay with ${method.title}: scan the code in your banking app, or use these details."
            link != null -> "Pay with ${method.title}: $link"
            else -> "Pay with ${method.title}:"
        } + if (paying.amountFilledIn || (!code && link == null)) "" else " Type in the amount yourself."
    }
}
