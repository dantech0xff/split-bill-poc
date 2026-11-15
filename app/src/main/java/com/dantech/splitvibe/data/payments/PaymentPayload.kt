package com.dantech.splitvibe.data.payments

import com.dantech.splitvibe.domain.payments.PaymentRequest
import java.net.URI
import kotlinx.serialization.Serializable

/**
 * How to pay one amount, as the web pages show it to someone without the app: the [PaymentRequest]
 * the app built, sent to the proxy along with a request or a bill's amounts, because the pages
 * cannot build payment codes themselves.
 */
@Serializable
data class PaymentPayload(val link: String?, val qrPayload: String?, val details: String, val amountFilledIn: Boolean) {

    companion object {
        /** What the proxy keeps of a payment code, and of the text to pay by hand. */
        private const val MAX_QR = 512
        private const val MAX_DETAILS = 1000

        /**
         * [request] as the proxy takes it: a link only when a browser may open it, a code only when
         * the proxy keeps one that long. The amount is filled in only if the link or the code is kept.
         */
        fun of(request: PaymentRequest): PaymentPayload {
            val link = request.link?.takeIf(::opensOnTheWeb)
            val qrPayload = request.qrPayload?.takeIf { it.length <= MAX_QR }
            return PaymentPayload(
                link = link,
                qrPayload = qrPayload,
                details = request.details.take(MAX_DETAILS),
                amountFilledIn = request.amountFilledIn && (link != null || qrPayload != null),
            )
        }

        /** A secure web address, or a UPI payment that a payer's own app opens. */
        fun opensOnTheWeb(link: String): Boolean {
            if (link.startsWith("upi://pay?")) return true
            val uri = runCatching { URI(link) }.getOrNull() ?: return false
            return uri.scheme == "https" && !uri.host.isNullOrEmpty()
        }
    }
}
