package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * What one unit of a bill's currency [from] is worth in the currency [to] the user counts in, so a
 * bill in dollars can also be read in dong. The bill and its shares stay in [from]: a converted
 * amount is only ever shown, never saved or split.
 */
@Serializable
data class ExchangeRate(
    val from: String,
    val to: String,
    /** How many units of [to] one unit of [from] buys. */
    @Serializable(with = BigDecimalAsString::class) val rate: BigDecimal,
    /** The day the rate is for, as yyyy-mm-dd; null for a rate the user typed in. */
    val date: String? = null,
    /** Where the rate comes from, such as "Frankfurter", or [BY_HAND]. */
    val source: String = BY_HAND,
) {
    val isByHand: Boolean get() = source == BY_HAND

    /** Whether this rate converts [bill] into [localCurrency]. */
    fun converts(bill: Bill, localCurrency: String): Boolean = from == bill.currency.code && to == localCurrency

    /**
     * [amountMinor] in [fromCurrency] as minor units of [toCurrency], to the nearest unit, half up
     * unless [rounding] says otherwise; null when the result is too large to hold, which only a
     * mistyped rate can cause.
     */
    fun convert(
        amountMinor: Long,
        fromCurrency: BillCurrency,
        toCurrency: BillCurrency,
        rounding: RoundingMode = RoundingMode.HALF_UP,
    ): Long? = try {
        toCurrency.toMinor(fromCurrency.toMajor(amountMinor).multiply(rate), rounding)
    } catch (e: ArithmeticException) {
        null
    }

    /** The rate as people write it, such as "1 USD = 25,350 VND". */
    fun describe(locale: Locale = TEXT_LOCALE): String = "1 $from = ${rateText(rate, locale)} $to"

    companion object {
        /** The [source] of a rate the user typed in. */
        const val BY_HAND = "you"

        /** Enough digits for any rate between two currencies, and few enough to read. */
        private val SHOWN = MathContext(6)

        /**
         * The rate from [from] to [to], worked out from rates that each say what one unit of the
         * same base currency buys, with the day of the older of the two; null when either currency
         * has no usable rate.
         */
        fun between(from: String, to: String, perBaseUnit: Map<String, Pair<BigDecimal, String>>, source: String): ExchangeRate? {
            val (fromRate, fromDay) = perBaseUnit[from] ?: return null
            val (toRate, toDay) = perBaseUnit[to] ?: return null
            if (fromRate.signum() <= 0 || toRate.signum() <= 0) return null
            return ExchangeRate(from, to, toRate.divide(fromRate, MathContext.DECIMAL64), minOf(fromDay, toDay), source)
        }

        /** A rate the way it is shown and typed: six significant digits, grouped as [locale] does. */
        fun rateText(rate: BigDecimal, locale: Locale = TEXT_LOCALE): String {
            val shown = rate.round(SHOWN).stripTrailingZeros()
            return NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = shown.scale().coerceAtLeast(0)
            }.format(shown)
        }
    }
}
