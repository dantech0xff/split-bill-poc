package com.dantech.splitvibe.domain.payments

import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.TEXT_LOCALE
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * What a payer gets to pay one request: a [link] to open, a [qrPayload] for their banking app to
 * scan, or both, and always the [details] to pay by hand, because no link or code works everywhere.
 */
data class PaymentRequest(
    val link: String?,
    val qrPayload: String?,
    val details: String,
    /** Whether the link or the code carries the amount, so the payer does not type it. */
    val amountFilledIn: Boolean,
)

/**
 * Builds what a payer needs from the method the requester takes money with.
 *
 * Each network takes the amount only in its own currency: PromptPay in baht, VietQR in dong, UPI
 * in rupees, SEPA in euros, PayPal in the currencies it supports. In any other currency the link or
 * code is made without the amount and the payer types it in. A note is cut down to what each network
 * accepts.
 */
object PaymentRequestBuilder {

    /** The currencies PayPal takes payments in; those in [PAYPAL_WHOLE_ONLY] without decimals. */
    private val PAYPAL_CURRENCIES = setOf(
        "AUD", "BRL", "CAD", "CNY", "CZK", "DKK", "EUR", "HKD", "HUF", "ILS", "JPY", "MYR",
        "MXN", "TWD", "NZD", "NOK", "PHP", "PLN", "GBP", "SGD", "SEK", "CHF", "THB", "USD",
    )
    private val PAYPAL_WHOLE_ONLY = setOf("HUF", "JPY", "TWD")

    private const val PROMPTPAY_AID = "A000000677010111"
    private const val VIETQR_GUID = "A000000727"
    private const val VIETQR_TO_ACCOUNT = "QRIBFTTA"
    private const val EMV_AMOUNT_LENGTH = 13
    private const val EPC_PAYLOAD_BYTES = 331
    private const val EPC_TEXT_LENGTH = 140
    private const val EPC_MAX_AMOUNT = "999999999.99"

    /** A UPI note, and a VietQR purpose, are kept to what every app shows. */
    private const val UPI_NOTE_LENGTH = 50
    private const val VIETQR_PURPOSE_LENGTH = 25

    /** Letters the SEPA scheme carries everywhere, besides a to z and digits. */
    private const val SEPA_PUNCTUATION = "/-?:().,'+ "

    fun build(
        method: PaymentMethod,
        amountMinor: Long,
        currency: BillCurrency,
        note: String?,
        locale: Locale = TEXT_LOCALE,
    ): PaymentRequest {
        val shownAmount = "Amount: ${currency.format(amountMinor, locale)}"
        val shownNote = note?.trim()?.takeIf { it.isNotEmpty() }?.let { "Note: $it" }
        val unusable = PaymentRequest(null, null, lines("This payment method cannot be used.", shownAmount, shownNote), false)
        if (method.problem() != null) return unusable
        // A method that came from someone else's phone may still hold something no code can carry;
        // it is then shown as unusable rather than taking the app down.
        return try {
            requestFor(method, amountMinor, currency, note, shownAmount, shownNote)
        } catch (e: IllegalArgumentException) {
            unusable
        }
    }

    private fun requestFor(
        method: PaymentMethod,
        amountMinor: Long,
        currency: BillCurrency,
        note: String?,
        shownAmount: String,
        shownNote: String?,
    ): PaymentRequest = when (method) {
        is PaymentMethod.PayPalMe -> {
            val amount = payPalAmount(amountMinor, currency)
            val link = "https://paypal.me/${method.handle}" + (amount?.let { "/$it" } ?: "")
            PaymentRequest(link, null, lines("PayPal: paypal.me/${method.handle}", shownAmount, shownNote), amount != null)
        }

        is PaymentMethod.Upi -> {
            val amount = majorText(amountMinor, currency, "INR", decimals = 2)
            val query = buildList {
                add("pa=${method.vpa}")
                add("pn=${encode(method.name)}")
                if (amount != null) {
                    add("am=$amount")
                    add("cu=INR")
                }
                upiNote(note)?.let { add("tn=${encode(it)}") }
            }
            val link = "upi://pay?" + query.joinToString("&")
            PaymentRequest(link, link, lines("UPI ID: ${method.vpa}", "Name: ${method.name}", shownAmount, shownNote), amount != null)
        }

        is PaymentMethod.PromptPay -> {
            val amount = majorText(amountMinor, currency, "THB", decimals = 2)?.takeIf { it.length <= EMV_AMOUNT_LENGTH }
            PaymentRequest(null, promptPay(method, amount), lines("PromptPay: ${method.id}", shownAmount, shownNote), amount != null)
        }

        is PaymentMethod.VietQr -> {
            val amount = majorText(amountMinor, currency, "VND", decimals = 0)?.takeIf { it.length <= EMV_AMOUNT_LENGTH }
            val details = lines(
                "Bank: ${method.bankName} (${method.bankBin})",
                "Account: ${method.account}",
                "Name: ${method.accountName}",
                shownAmount,
                shownNote,
            )
            PaymentRequest(null, vietQr(method, amount, vietQrPurpose(note)), details, amount != null)
        }

        is PaymentMethod.SepaQr -> {
            // The guidelines take euros to the cent, up to 999,999,999.99.
            val amount = majorText(amountMinor, currency, "EUR", decimals = null)
                ?.takeIf { BigDecimal(it) <= BigDecimal(EPC_MAX_AMOUNT) && BigDecimal(it).scale() <= 2 }
            val details = lines(
                "Name: ${method.name}",
                "IBAN: ${method.groupedIban}",
                method.compactBic?.let { "BIC: $it" },
                shownAmount,
                shownNote,
            )
            val payload = epcQr(method, amount, sepaText(note))
            PaymentRequest(null, payload, details, amount != null && payload != null)
        }

        is PaymentMethod.PastedLink -> {
            val link = method.link
            PaymentRequest(link, null, lines("Pay at: $link", shownAmount, shownNote), false)
        }

        is PaymentMethod.PlainDetails ->
            PaymentRequest(null, null, lines("${method.label}: ${method.details}", shownAmount, shownNote), false)
    }

    /**
     * PromptPay as the Bank of Thailand's apps read it, in the order of the open-source promptpay-qr
     * library: a mobile number goes in as 0066 and its last nine digits, an id as it is.
     */
    private fun promptPay(method: PaymentMethod.PromptPay, amount: String?): String {
        val digits = method.digits
        val (kind, target) = when {
            digits.length >= 15 -> "03" to digits
            digits.length >= 13 -> "02" to digits
            else -> "01" to ("0000000000000" + digits.replaceFirst(Regex("^0"), "66")).takeLast(13)
        }
        return EmvQr.payload(
            listOfNotNull(
                EmvQr.field("00", "01"),
                EmvQr.field("01", if (amount != null) "12" else "11"),
                EmvQr.field("29", EmvQr.field("00", PROMPTPAY_AID) + EmvQr.field(kind, target)),
                EmvQr.field("58", "TH"),
                EmvQr.field("53", "764"),
                amount?.let { EmvQr.field("54", it) },
            ),
        )
    }

    /** VietQR for a transfer to an account, in the order NAPAS member banks write it. */
    private fun vietQr(method: PaymentMethod.VietQr, amount: String?, purpose: String?): String {
        val beneficiary = EmvQr.field("00", method.bankBin) + EmvQr.field("01", method.account)
        return EmvQr.payload(
            listOfNotNull(
                EmvQr.field("00", "01"),
                EmvQr.field("01", if (amount != null) "12" else "11"),
                EmvQr.field("38", EmvQr.field("00", VIETQR_GUID) + EmvQr.field("01", beneficiary) + EmvQr.field("02", VIETQR_TO_ACCOUNT)),
                EmvQr.field("53", "704"),
                amount?.let { EmvQr.field("54", it) },
                EmvQr.field("58", "VN"),
                purpose?.let { EmvQr.field("62", EmvQr.field("08", it)) },
            ),
        )
    }

    /**
     * The QR code of the European Payments Council's guidelines (EPC069-12), version 002 in UTF-8,
     * with lines left empty for what the request does not use, and none after the last line that
     * has something. Null when it would not fit in the 331 bytes the guidelines allow.
     */
    private fun epcQr(method: PaymentMethod.SepaQr, amount: String?, text: String?): String? {
        val lines = listOf(
            "BCD",
            "002",
            "1",
            "SCT",
            method.compactBic.orEmpty(),
            method.name.trim(),
            method.compactIban,
            amount?.let { "EUR$it" }.orEmpty(),
            // Purpose and structured reference.
            "",
            "",
            text.orEmpty(),
        )
        val payload = lines.joinToString("\n").trimEnd('\n')
        return payload.takeIf { it.toByteArray(Charsets.UTF_8).size <= EPC_PAYLOAD_BYTES }
    }

    /** [amountMinor] in major units for a network that takes [code] only, or null in another currency or for nothing to pay. */
    private fun majorText(amountMinor: Long, currency: BillCurrency, code: String, decimals: Int?): String? {
        if (currency.code != code || amountMinor <= 0) return null
        val major = currency.toMajor(amountMinor)
        return if (decimals == null) {
            major.stripTrailingZeros().toPlainString()
        } else {
            // A network that counts in whole units takes no fraction of one.
            if (major.stripTrailingZeros().scale() > decimals) return null
            major.setScale(decimals, RoundingMode.UNNECESSARY).toPlainString()
        }
    }

    /** PayPal.Me writes a whole amount without decimals, as in its own "25AUD", and anything else to the cent. */
    private fun payPalAmount(amountMinor: Long, currency: BillCurrency): String? {
        if (currency.code !in PAYPAL_CURRENCIES || amountMinor <= 0) return null
        val major = currency.toMajor(amountMinor).stripTrailingZeros()
        val text = when {
            major.scale() <= 0 -> major.toPlainString()
            currency.code in PAYPAL_WHOLE_ONLY || major.scale() > 2 -> return null
            else -> major.setScale(2).toPlainString()
        }
        return text + currency.code
    }

    private fun upiNote(note: String?): String? =
        note?.filterNot(Char::isISOControl)?.replace(Regex("\\s+"), " ")?.trim()?.take(UPI_NOTE_LENGTH)?.trim()?.ifEmpty { null }

    /** A VietQR purpose holds unaccented letters, digits and spaces only. */
    private fun vietQrPurpose(note: String?): String? = note?.let { plainLatin(it) }
        ?.filter { it.isLetterOrDigit() || it == ' ' }
        ?.replace(Regex(" +"), " ")?.trim()?.take(VIETQR_PURPOSE_LENGTH)?.trim()?.ifEmpty { null }

    /** A SEPA remittance text holds the characters the scheme carries in every country. */
    private fun sepaText(note: String?): String? = note?.let { plainLatin(it) }
        ?.filter { it.isLetterOrDigit() || it in SEPA_PUNCTUATION }
        ?.replace(Regex(" +"), " ")?.trim()?.take(EPC_TEXT_LENGTH)?.trim()?.ifEmpty { null }

    /** [text] with accents dropped and anything outside ASCII taken out, so "Phở bò" is "Pho bo". */
    private fun plainLatin(text: String): String =
        Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD).filter { it.code in 0x20..0x7E }

    /** A query value, with spaces as %20 because some UPI apps read + as a plus sign. */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun lines(vararg lines: String?): String = lines.filterNotNull().joinToString("\n")
}
