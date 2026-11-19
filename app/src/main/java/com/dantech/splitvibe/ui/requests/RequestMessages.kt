package com.dantech.splitvibe.ui.requests

import com.dantech.splitvibe.R
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.domain.payments.PaymentRequest
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.sentences
import com.dantech.splitvibe.text.text

/** The text of a request as it goes into a chat: who owes what for what, and how to pay it. */
object RequestMessages {

    /**
     * The request to [name] for [amountMinor] of what [title] names, with what to pay it with. [link]
     * is the request's own link on the proxy, with which the payer can say they paid; null when the
     * request is not kept there.
     */
    fun request(
        name: UiText,
        amountMinor: Long,
        currency: BillCurrency,
        title: String?,
        breakdown: UiText?,
        method: PaymentMethod?,
        paying: PaymentRequest?,
        link: String?,
    ): UiText = UiText.Lines(
        buildList {
            val what = title?.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: text(R.string.requests_message_the_bill)
            add(text(R.string.requests_message_share, name, what, currency.format(amountMinor)))
            breakdown?.let(::add)
            if (method != null && paying != null) {
                add(BLANK)
                add(howToPay(method, paying))
                add(UiText.Raw(paying.details))
            }
            link?.let {
                add(BLANK)
                add(text(R.string.requests_message_link, it))
            }
        },
    )

    /** A friendly nudge about a request that is still open. */
    fun reminder(name: String, amountMinor: Long, currency: BillCurrency, title: String?, paying: PaymentRequest?, link: String?): UiText =
        UiText.Lines(
            buildList {
                val amount = currency.format(amountMinor)
                val about = title?.takeIf { it.isNotBlank() }
                add(
                    if (about == null) {
                        text(R.string.requests_reminder, name, amount)
                    } else {
                        text(R.string.requests_reminder_for, name, amount, about)
                    },
                )
                paying?.let {
                    add(BLANK)
                    add(UiText.Raw(it.details))
                }
                link?.let {
                    add(BLANK)
                    add(UiText.Raw(it))
                }
            },
        )

    private fun howToPay(method: PaymentMethod, paying: PaymentRequest): UiText {
        val code = paying.qrPayload != null
        val link = paying.link?.takeIf { it != paying.qrPayload || !code }
        val how = when {
            code && link != null -> text(R.string.requests_pay_code_or_link, method.title, link)
            code -> text(R.string.requests_pay_code, method.title)
            link != null -> text(R.string.requests_pay_link, method.title, link)
            else -> text(R.string.requests_pay_details, method.title)
        }
        return if (paying.amountFilledIn || (!code && link == null)) how else sentences(how, text(R.string.requests_type_amount))
    }

    private val BLANK = UiText.Raw("")
}
