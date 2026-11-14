package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * The currency a bill is denominated in.
 *
 * Every amount in the app is a whole number of the currency's smallest unit ("minor units": cents
 * for USD, dong for VND). Splitting integers means no money is lost or invented by rounding.
 */
data class BillCurrency(val code: String, val fractionDigits: Int) {

    /** Converts a decimal amount in major units (12.50) to minor units (1250), rounding half up. */
    fun toMinor(amount: BigDecimal): Long = amount.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun toMajor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, fractionDigits)

    // Building a formatter is slow, and an amount that runs through the numbers in between is
    // formatted on every frame. A formatter must not be shared between threads, so each thread
    // keeps its own, for the last locale it was asked for.
    private val formatters = ThreadLocal<Pair<Locale, NumberFormat>>()

    fun format(minor: Long, locale: Locale = Locale.getDefault()): String {
        val number = formatterFor(locale).format(toMajor(minor))
        return if (isoCurrency(code) != null || code.isBlank()) number else "$number $code"
    }

    private fun formatterFor(locale: Locale): NumberFormat {
        formatters.get()?.let { (cachedLocale, formatter) -> if (cachedLocale == locale) return formatter }
        val iso = isoCurrency(code)
        val formatter = if (iso != null) {
            NumberFormat.getCurrencyInstance(locale).apply { currency = iso }
        } else {
            NumberFormat.getNumberInstance(locale)
        }
        formatter.minimumFractionDigits = fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        formatters.set(locale to formatter)
        return formatter
    }

    companion object {
        /** ISO 4217 code for "no currency". */
        const val NO_CURRENCY = "XXX"
        private const val MAX_FRACTION_DIGITS = 3

        /** True for a three-letter currency code the platform knows, such as VND or USD. */
        fun isKnownCode(code: String): Boolean = isoCurrency(code.trim().uppercase(Locale.ROOT)) != null

        /**
         * Builds the currency for a bill.
         *
         * How finely a bill can be split is decided by the bill itself: never finer than the prices
         * are printed ([printedDecimals]), and never finer than the currency allows. This is what
         * keeps a rupiah or forint bill in whole units even though ISO 4217 gives those currencies
         * two decimals that nobody pays with, while a dollar bill printed with cents splits to the
         * cent.
         *
         * @param printedDecimals decimal places the prices are printed with, or null when unknown.
         * @param amounts the amounts read from the bill. Decimals they actually carry are kept even
         *   if [printedDecimals] says fewer.
         */
        fun resolve(code: String, printedDecimals: Int?, amounts: Collection<BigDecimal>): BillCurrency {
            val normalized = code.trim().uppercase(Locale.ROOT)
            val inAmounts = (amounts.maxOfOrNull { it.stripTrailingZeros().scale() } ?: 0).coerceIn(0, MAX_FRACTION_DIGITS)
            val onBill = printedDecimals?.let { maxOf(it, inAmounts).coerceIn(0, MAX_FRACTION_DIGITS) }

            val iso = isoCurrency(normalized)
            if (iso != null) {
                val allowed = iso.defaultFractionDigits
                return BillCurrency(normalized, if (onBill == null) allowed else minOf(allowed, onBill))
            }

            // Unknown currency: go by what the bill shows. A single decimal is shown as two.
            val digits = onBill ?: inAmounts
            return BillCurrency(if (normalized == NO_CURRENCY) "" else normalized, if (digits == 1) 2 else digits)
        }

        private fun isoCurrency(code: String): Currency? {
            if (code.length != 3 || code == NO_CURRENCY) return null
            val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return null
            return currency.takeIf { it.defaultFractionDigits >= 0 }
        }
    }
}
