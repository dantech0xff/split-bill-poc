package com.dantech.splitvibe.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.DateOrder
import com.dantech.splitvibe.domain.dateOrderOf
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The order in which the app writes the day, the month and the year, and the marks and the side of
 * the symbol in an amount, come from the phone's own data about its region, which Android takes from
 * ICU and which differs from the Java that the local tests run on, and between versions of Android.
 * ICU also writes the amount, so it decides whether the English symbol the app gives it is kept. This
 * checks them on a real Android.
 */
@RunWith(AndroidJUnit4::class)
class DateOrderOnDeviceTest {

    @Test
    fun eachOfTheThreeOrdersComesFromTheRegionThatWritesIt() {
        assertEquals("Vietnam", DateOrder.DayFirst, dateOrderOf(Locale.forLanguageTag("vi-VN")))
        assertEquals("United States", DateOrder.MonthFirst, dateOrderOf(Locale.forLanguageTag("en-US")))
        assertEquals("Japan", DateOrder.YearFirst, dateOrderOf(Locale.forLanguageTag("ja-JP")))
    }

    @Test
    fun anAmountTakesTheMarksAndTheSideOfItsRegionWithTheDigitsZeroToNine() {
        val dong = BillCurrency("VND", 0)

        val vietnam = onPhone("vi-VN") { dong.format(902_664) }
        assertTrue("Vietnam: $vietnam", "902.664" in vietnam && "₫" in vietnam)
        // Recent versions of Android write the dong after the number in Vietnam, and Android 8.0, whose
        // data about regions is older, writes it before. The app writes it on the side the phone does.
        val phonesOwn = phonesOwnAmount("vi-VN", "VND", 902_664)
        assertEquals("Vietnam: $vietnam, where the phone writes $phonesOwn", phonesOwn.symbolFirst("₫"), vietnam.symbolFirst("₫"))
        val unitedStates = onPhone("en-US") { dong.format(902_664) }
        assertTrue("United States: $unitedStates", unitedStates.indexOf("₫") in 0 until unitedStates.indexOf("902,664"))
        val egypt = onPhone("ar-EG") { dong.format(902_664) }
        assertEquals("Egypt: $egypt", "902664", egypt.filter(Char::isDigit))
        // The symbol of the app's English texts, which ICU writes only if it keeps the one the app gives it.
        val dollars = onPhone("vi-VN") { BillCurrency("USD", 2).format(123_450) }
        assertTrue("US dollars in Vietnam: $dollars", "$" in dollars && "US$" !in dollars)
    }

    /** How the phone itself writes [whole] units of the currency [code] in the region of [tag], with its own symbol. */
    private fun phonesOwnAmount(tag: String, code: String, whole: Long): String =
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag(tag)).apply {
            currency = Currency.getInstance(code)
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }.format(whole)

    /** Whether [symbol] comes before the first digit. */
    private fun String.symbolFirst(symbol: String): Boolean = indexOf(symbol) in 0 until indexOfFirst(Char::isDigit)

    /** [block] with the phone set to the region of [tag], as the app reads it, and its own region back after it. */
    private fun <T> onPhone(tag: String, block: () -> T): T {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(tag))
        try {
            return block()
        } finally {
            Locale.setDefault(before)
        }
    }
}
