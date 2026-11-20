package com.dantech.splitvibe.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the app writes amounts, numbers and dates on phones set to eight regions that differ in the
 * order of a date, in their marks for decimals and thousands, and in the digits they would use.
 */
class RegionFormatsTest {

    @Test
    fun `each region gets the order in which it writes the day, the month and the year`() {
        val expected = mapOf(
            "en-US" to DateOrder.MonthFirst,
            "vi-VN" to DateOrder.DayFirst,
            "de-DE" to DateOrder.DayFirst,
            "fr-FR" to DateOrder.DayFirst,
            "hi-IN" to DateOrder.DayFirst,
            "ar-EG" to DateOrder.DayFirst,
            "ja-JP" to DateOrder.YearFirst,
            "fa-IR" to DateOrder.YearFirst,
        )

        val actual = expected.keys.associateWith { tag -> dateOrderOf(Locale.forLanguageTag(tag)) }

        assertEquals(expected, actual)
    }
}
