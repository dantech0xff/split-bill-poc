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
        /** What the proxy keeps of a link, a payment code and the text to pay by hand, in code points. */
        private const val MAX_LINK = 2048
        private const val MAX_QR = 512
        private const val MAX_DETAILS = 1000

        /**
         * [request] as the proxy takes it, by the same rules: a link only when a browser may open it,
         * a code only when the proxy keeps it whole, and the details with no control character but
         * line breaks, such as a tab read from a photo into the merchant's name. The amount counts as
         * filled in only when the link or the code that carries it is kept.
         */
        fun of(request: PaymentRequest): PaymentPayload {
            val link = request.link?.takeIf(::opensOnTheWeb)
            val qrPayload = request.qrPayload?.takeIf { it.codePointLength() <= MAX_QR && it.none(::breaksText) }
            val details = request.details.map {
                if (breaksText(it)) ' ' else it
            }.joinToString("").takeCodePoints(MAX_DETAILS).ifBlank { "Pay by hand." }
            return PaymentPayload(
                link = link,
                qrPayload = qrPayload,
                details = details,
                amountFilledIn = request.amountFilledIn && (link != null || qrPayload != null),
            )
        }

        /**
         * A link a page opens: `https://` with a host, or a UPI payment that the payer's own app opens,
         * with no whitespace or control character in it and at most as long as the proxy keeps.
         */
        fun opensOnTheWeb(link: String): Boolean {
            val unsafe = link.any { it.isWhitespace() || it.isISOControl() || it == BYTE_ORDER_MARK }
            if (unsafe || link.codePointLength() > MAX_LINK) return false
            if (link.startsWith("upi://pay?")) return true
            if (!link.startsWith("https://")) return false
            val uri = runCatching { URI(link) }.getOrNull() ?: return false
            return !uri.host.isNullOrEmpty()
        }

        /** Every control character but the two that break lines, which payment details and codes may hold. */
        private fun breaksText(char: Char): Boolean = char.isISOControl() && char != '\n' && char != '\r'

        private fun String.codePointLength(): Int = codePointCount(0, length)

        /** Taken by a browser for whitespace, though Unicode does not class it as such. */
        private const val BYTE_ORDER_MARK = '\uFEFF'

        /** The first [count] code points, never cutting one in two. */
        private fun String.takeCodePoints(count: Int): String =
            if (codePointLength() <= count) this else substring(0, offsetByCodePoints(0, count))
    }
}
