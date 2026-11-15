package com.dantech.splitvibe.domain.payments

import java.math.BigInteger
import java.net.URI
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A way the user takes money, kept on their phone. A payment request carries a copy of exactly one,
 * from which the payer's app builds the link, the QR code and the details to type in by hand.
 *
 * A new way of paying is a new subtype with its [PaymentRequestBuilder] case and tests; nothing
 * else changes.
 */
@Serializable
sealed interface PaymentMethod {

    /** What the method is called, such as "PromptPay". */
    val title: String

    /** Which account it is, such as "ACB · 257678859". */
    val summary: String

    /** What is wrong with the method as entered, or null when money can be sent to it. */
    fun problem(): String?

    /** A PayPal.Me link: the amount and currency go in the link. */
    @Serializable
    @SerialName("paypal_me")
    data class PayPalMe(val handle: String) : PaymentMethod {
        override val title: String get() = "PayPal.Me"
        override val summary: String get() = "paypal.me/$handle"

        override fun problem(): String? {
            val valid = handle.length in 1..20 && handle.all(::isAsciiLetterOrDigit)
            return "A PayPal.Me name is 1 to 20 letters and digits.".takeUnless { valid }
        }
    }

    /**
     * A UPI id in India. The payer starts the payment: since October 2025 a person cannot ask
     * another person to pay through UPI, so this is never a collect request.
     */
    @Serializable
    @SerialName("upi")
    data class Upi(val vpa: String, val name: String) : PaymentMethod {
        override val title: String get() = "UPI"
        override val summary: String get() = vpa

        override fun problem(): String? = when {
            !UPI_ID.matches(vpa) -> "A UPI ID looks like name@bank."
            name.isBlank() || name.length > 50 || name.any(Char::isISOControl) -> "Enter the name on the account, up to 50 characters."
            else -> null
        }
    }

    /**
     * A PromptPay id in Thailand: a mobile number (ten digits from 0, or +66 and nine digits), a
     * 13-digit national or tax id, or a 15-digit e-wallet id. Dashes and spaces are allowed.
     */
    @Serializable
    @SerialName("promptpay")
    data class PromptPay(val id: String) : PaymentMethod {
        override val title: String get() = "PromptPay"
        override val summary: String get() = id

        /** The id's digits; only 0 to 9 count, as only those go into a payment code. */
        val digits: String get() = id.filter(::isAsciiDigit)

        override fun problem(): String? {
            // A plus sign only starts an international mobile number.
            val international = id.trim().startsWith("+") && id.count { it == '+' } == 1
            val valid = id.all { isAsciiDigit(it) || it in "- " || (it == '+' && international) } &&
                when (digits.length) {
                    10 -> !international && digits.startsWith("0")
                    11 -> international && digits.startsWith("66")
                    13, 15 -> !international
                    else -> false
                }
            return "Enter a Thai mobile number, a 13-digit ID or a 15-digit e-wallet ID.".takeUnless { valid }
        }
    }

    /** A bank account in Vietnam that takes transfers through NAPAS. */
    @Serializable
    @SerialName("vietqr")
    data class VietQr(val bankBin: String, val account: String, val accountName: String) : PaymentMethod {
        override val title: String get() = "VietQR"
        override val summary: String get() = "${bankName ?: bankBin} · $account"

        val bankName: String? get() = VietQrBanks.byBin(bankBin)?.shortName

        override fun problem(): String? = when {
            VietQrBanks.byBin(bankBin) == null -> "Choose the bank."

            account.length !in 4..19 || !account.all(::isAsciiLetterOrDigit) -> "An account number is 4 to 19 letters and digits."

            accountName.isBlank() || accountName.length > 50 || accountName.any(Char::isISOControl) ->
                "Enter the name on the account, up to 50 characters."

            else -> null
        }
    }

    /** A bank account in the euro area, paid by the QR code of the European Payments Council. */
    @Serializable
    @SerialName("sepa")
    data class SepaQr(val name: String, val iban: String, val bic: String? = null) : PaymentMethod {
        override val title: String get() = "SEPA transfer"
        override val summary: String get() = groupedIban

        /** The IBAN as banks store it: capitals, no spaces. */
        val compactIban: String get() = iban.filterNot(Char::isWhitespace).uppercase(Locale.ROOT)

        /** The IBAN in groups of four, as it is printed. */
        val groupedIban: String get() = compactIban.chunked(4).joinToString(" ")

        val compactBic: String? get() = bic?.filterNot(Char::isWhitespace)?.uppercase(Locale.ROOT)?.ifEmpty { null }

        override fun problem(): String? = when {
            name.isBlank() || name.length > 70 || name.any(Char::isISOControl) -> "Enter the name on the account, up to 70 characters."
            !isValidIban(compactIban) -> "Check the IBAN."
            compactBic?.let { !BIC.matches(it) } == true -> "A BIC is 8 or 11 letters and digits."
            else -> null
        }
    }

    /**
     * A link the user made in a payment app, such as Revolut, Wise, Venmo or Cash App, which cannot
     * be filled in for them. Only secure links to known payment services are taken; the app shows the
     * service's address and asks before it opens one.
     */
    @Serializable
    @SerialName("link")
    data class PastedLink(val url: String) : PaymentMethod {
        override val title: String get() = "Payment link"
        override val summary: String get() = host ?: url

        /** The address of the payment service, or null when the link is not one the app opens. */
        val host: String? get() = paymentHostOf(url)

        /** The link as it is opened: trimmed, with the scheme in lower case, which Android matches exactly. */
        val link: String get() = url.trim().let { "https" + it.substring(it.indexOf(':')) }

        override fun problem(): String? =
            "Paste a secure link from a payment service, such as revolut.me or wise.com.".takeIf { host == null }
    }

    /** Details for the payer to type in themselves, such as a Zelle email or a bank account. */
    @Serializable
    @SerialName("details")
    data class PlainDetails(val label: String, val details: String) : PaymentMethod {
        override val title: String get() = label
        override val summary: String get() = details.lineSequence().first()

        override fun problem(): String? = when {
            label.isBlank() || label.length > 40 || label.any(Char::isISOControl) -> "Name the method, up to 40 characters."

            details.isBlank() || details.length > 500 || details.any { it.isISOControl() && it != '\n' } ->
                "Enter the details, up to 500 characters."

            else -> null
        }
    }

    companion object {
        /** Payment services whose links the app opens, with their subdomains. */
        val PAYMENT_DOMAINS = listOf(
            "paypal.me",
            "paypal.com",
            "venmo.com",
            "cash.app",
            "revolut.me",
            "revolut.com",
            "wise.com",
            "monzo.me",
            "bunq.me",
        )

        private val UPI_ID = Regex("[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}")
        private val BIC = Regex("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?")
        private val IBAN = Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")

        /**
         * The lower-case host of [url] when it is an https link to one of [PAYMENT_DOMAINS] on the
         * standard port, with no user name in it; null for anything else, so `javascript:`,
         * `intent:`, `http:` and look-alike addresses are never opened.
         */
        fun paymentHostOf(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.length > 512 || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return null
            if (uri.port != -1 && uri.port != 443) return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            if (!host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }) return null
            return host.takeIf { candidate -> PAYMENT_DOMAINS.any { candidate == it || candidate.endsWith(".$it") } }
        }

        /** Whether [iban], in capitals without spaces, has the shape of an IBAN and its check digits are right. */
        fun isValidIban(iban: String): Boolean {
            if (!IBAN.matches(iban)) return false
            // Letters count as two digits, A as 10 to Z as 35.
            val digits = (iban.drop(4) + iban.take(4)).map { if (it.isDigit()) it - '0' else it - 'A' + 10 }.joinToString("")
            return BigInteger(digits).mod(BigInteger.valueOf(97)) == BigInteger.ONE
        }

        private fun isAsciiLetterOrDigit(char: Char) = char in 'a'..'z' || char in 'A'..'Z' || isAsciiDigit(char)

        // Char.isDigit also takes Thai, full-width and other digits, which no payment code carries.
        private fun isAsciiDigit(char: Char) = char in '0'..'9'
    }
}
