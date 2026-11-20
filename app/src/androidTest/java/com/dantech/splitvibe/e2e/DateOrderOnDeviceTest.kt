package com.dantech.splitvibe.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.domain.DateOrder
import com.dantech.splitvibe.domain.dateOrderOf
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The order in which the app writes the day, the month and the year comes from the phone's own data
 * about its region, which Android takes from ICU and which differs from the Java that the local
 * tests run on, and between versions of Android. This checks the three orders on a real Android.
 */
@RunWith(AndroidJUnit4::class)
class DateOrderOnDeviceTest {

    @Test
    fun eachOfTheThreeOrdersComesFromTheRegionThatWritesIt() {
        assertEquals("Vietnam", DateOrder.DayFirst, dateOrderOf(Locale.forLanguageTag("vi-VN")))
        assertEquals("United States", DateOrder.MonthFirst, dateOrderOf(Locale.forLanguageTag("en-US")))
        assertEquals("Japan", DateOrder.YearFirst, dateOrderOf(Locale.forLanguageTag("ja-JP")))
    }
}
