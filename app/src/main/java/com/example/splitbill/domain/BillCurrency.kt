package com.example.splitbill.domain

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
    fun toMinor(amount: BigDecimal): Long =
        amount.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun toMajor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, fractionDigits)

    fun format(minor: Long, locale: Locale = Locale.getDefault()): String {
        val major = toMajor(minor)
        val iso = isoCurrency(code)
        if (iso != null) {
            val formatter = NumberFormat.getCurrencyInstance(locale)
            formatter.currency = iso
            formatter.minimumFractionDigits = fractionDigits
            formatter.maximumFractionDigits = fractionDigits
            return formatter.format(major)
        }
        val formatter = NumberFormat.getNumberInstance(locale)
        formatter.minimumFractionDigits = fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        val number = formatter.format(major)
        return if (code.isBlank()) number else "$number $code"
    }

    companion object {
        private const val NO_CURRENCY = "XXX"
        private const val MAX_FRACTION_DIGITS = 3

        /**
         * Builds the currency for a bill from the ISO 4217 code the model reported.
         *
         * When the code is unknown, the number of decimals is inferred from the [amounts] on the bill
         * so that "12.50" still splits to the cent.
         */
        fun resolve(code: String, amounts: Collection<BigDecimal>): BillCurrency {
            val normalized = code.trim().uppercase(Locale.ROOT)
            val iso = isoCurrency(normalized)
            if (iso != null) return BillCurrency(normalized, iso.defaultFractionDigits)

            val observedDigits = amounts.maxOfOrNull { it.stripTrailingZeros().scale() } ?: 0
            val digits = when {
                observedDigits <= 0 -> 0
                observedDigits <= 2 -> 2
                else -> MAX_FRACTION_DIGITS
            }
            return BillCurrency(if (normalized == NO_CURRENCY) "" else normalized, digits)
        }

        private fun isoCurrency(code: String): Currency? {
            if (code.length != 3 || code == NO_CURRENCY) return null
            val currency = runCatching { Currency.getInstance(code) }.getOrNull() ?: return null
            return currency.takeIf { it.defaultFractionDigits >= 0 }
        }
    }
}
