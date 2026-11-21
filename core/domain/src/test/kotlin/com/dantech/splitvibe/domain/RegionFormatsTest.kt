package com.dantech.splitvibe.domain

import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * How the app writes amounts, numbers and dates on phones set to eight regions that differ in the
 * order of a date, in their marks for decimals and thousands, and in the digits they would use.
 *
 * The texts are checked whole for the United States, Vietnam and Germany. For the other five only
 * what must hold everywhere is checked, because the data about a region that Java has differs from
 * what each version of Android has: how the digits are grouped, the marks that keep the direction
 * of the text, and the space beside a code such as THB. English-speaking Canada and Sweden join them
 * for the order of a date, as they write a date of digits alone in another order than one with the
 * name of its month.
 */
class RegionFormatsTest {

    /** The texts below are those of a machine set to the United States, as the build sets every machine that runs the tests. */
    @Before
    fun theMachineIsSetToTheUnitedStates() {
        assertEquals("the region the build sets for the tests", Locale.US, Locale.getDefault(Locale.Category.FORMAT))
        assertEquals(Locale.US, Locale.getDefault())
    }

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
            "fa-IR" to DateOrder.DayFirst,
            // Both write a date of digits alone with the year first, "y-MM-dd", but not one with a month name.
            "en-CA" to DateOrder.MonthFirst,
            "sv-SE" to DateOrder.DayFirst,
        )

        val actual = expected.keys.associateWith { tag -> dateOrderOf(Locale.forLanguageTag(tag)) }

        assertEquals(expected, actual)
    }

    @Test
    fun `the region is the one the phone has for formats, with the digits 0 to 9 whatever digits it asks for`() {
        val egypt = Locale.forLanguageTag("ar-EG-u-nu-latn")

        assertEquals(egypt, onPhone("ar-EG") { regionLocale() })
        assertEquals(egypt, onPhone("ar-EG-u-nu-arab") { regionLocale() })
        onPhone("ar-EG") {
            // The language the phone shows its own words in does not count, only its region for formats.
            Locale.setDefault(Locale.Category.DISPLAY, Locale.FRANCE)
            assertEquals(egypt, regionLocale())
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
            assertEquals(Locale.forLanguageTag("de-DE-u-nu-latn"), regionLocale())
        }
    }

    @Test
    fun `the region is the same until the phone is set to another`() = onPhone("vi-VN") {
        val vietnam = regionLocale()

        assertSame(vietnam, regionLocale())
        Locale.setDefault(Locale.GERMANY)
        assertEquals(Locale.forLanguageTag("de-DE-u-nu-latn"), regionLocale())
    }

    @Test
    fun `a date follows the phone when it is set to another region`() = onPhone("vi-VN") {
        assertEquals("13 Nov 2026", dateWithYearText(NOV_13))
        Locale.setDefault(Locale.US)
        assertEquals("Nov 13, 2026", dateWithYearText(NOV_13))
    }

    @Test
    fun `a phone locale that is not well formed leaves amounts as the English texts write them`() {
        // Only the old constructor makes such a locale, and an app can still set one as the default.
        @Suppress("DEPRECATION")
        val broken = Locale("en", "US", "WIN")

        onPhone(broken) {
            assertEquals(TEXT_LOCALE, regionLocale())
            assertEquals("$12.50", USD.format(1_250))
        }
    }

    @Test
    fun `the United States, Vietnam and Germany write amounts, percentages, rates and dates exactly so`() {
        val vietnamAndGermany = listOf(
            "902.664$NBSP₫",
            "1.234,50$NBSP$",
            "33,33%",
            "1 USD = 25.350 VND",
            "1 VND = 0,0000394477 USD",
            "13 Nov",
            "13 Nov 2026",
            "13 November",
            "13 November 2026",
            "November 2026",
            "November",
        )
        val expected = mapOf(
            "en-US" to listOf(
                "₫902,664",
                "$1,234.50",
                "33.33%",
                "1 USD = 25,350 VND",
                "1 VND = 0.0000394477 USD",
                "Nov 13",
                "Nov 13, 2026",
                "November 13",
                "November 13, 2026",
                "November 2026",
                "November",
            ),
            "vi-VN" to vietnamAndGermany,
            "de-DE" to vietnamAndGermany,
        )

        val actual = expected.keys.associateWith { tag ->
            onPhone(tag) {
                listOf(
                    VND.format(902_664),
                    USD.format(123_450),
                    percentText(3_333),
                    ExchangeRate("USD", "VND", BigDecimal(25_350)).describe(),
                    ExchangeRate("VND", "USD", BigDecimal("0.00003944773175542406")).describe(),
                    shortDateText(NOV_13, today = NOV_20),
                    dateWithYearText(NOV_13),
                    longDateText(NOV_13),
                    longDateWithYearText(NOV_13),
                    monthYearText(YearMonth.of(2026, 11)),
                    monthText(YearMonth.of(2026, 11)),
                )
            }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `France, India, Japan, Egypt and Iran write amounts with the digits 0 to 9, the symbol and their own marks`() {
        for (tag in listOf("fr-FR", "hi-IN", "ja-JP", "ar-EG", "fa-IR")) {
            onPhone(tag) {
                // The marks of the region with the digits 0 to 9, told from the phone's locale rather than by the code under test.
                val marks = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("$tag-u-nu-latn"))
                val dollars = USD.format(123_456_789)
                val dong = VND.format(902_664)
                val percent = percentText(3_333)
                val rate = ExchangeRate("VND", "USD", BigDecimal("0.0000394477")).describe()

                listOf(dollars, dong, percent, rate).forEach { assertTrue("$tag: $it", it.hasOnlyLatinDigits()) }
                assertEquals("$tag: $dollars", "123456789", dollars.filter(Char::isDigit))
                assertTrue(
                    "$tag: $dollars",
                    "$" in dollars && "${marks.decimalSeparator}89" in dollars && marks.groupingSeparator in dollars,
                )
                assertTrue("$tag: $dong", "₫" in dong && marks.groupingSeparator in dong)
                assertTrue("$tag: $percent", "33${marks.decimalSeparator}33" in percent && percent.endsWith("%"))
                assertTrue("$tag: $rate", rate.startsWith("1 VND = 0${marks.decimalSeparator}0000394477") && rate.endsWith(" USD"))
            }
        }
    }

    @Test
    fun `each region writes a date with English month names in its own order`() {
        val expected = mapOf(
            "en-US" to "Nov 13, 2026",
            "vi-VN" to "13 Nov 2026",
            "de-DE" to "13 Nov 2026",
            "fr-FR" to "13 Nov 2026",
            "hi-IN" to "13 Nov 2026",
            "ar-EG" to "13 Nov 2026",
            "ja-JP" to "2026 Nov 13",
            "fa-IR" to "13 Nov 2026",
            "en-CA" to "Nov 13, 2026",
            "sv-SE" to "13 Nov 2026",
        )

        val actual = expected.keys.associateWith { tag -> onPhone(tag) { dateWithYearText(NOV_13) } }

        assertEquals(expected, actual)
    }

    @Test
    fun `the four forms of a date in each of the three orders, and the month alone and with its year in all of them`() {
        fun forms(order: DateOrder) = listOf(
            shortDateText(NOV_13, NOV_20, order),
            dateWithYearText(NOV_13, order),
            longDateText(NOV_13, order),
            longDateWithYearText(NOV_13, order),
            monthYearText(NOV_13),
            monthText(NOV_13),
        )

        assertEquals(
            listOf("13 Nov", "13 Nov 2026", "13 November", "13 November 2026", "November 2026", "November"),
            forms(DateOrder.DayFirst),
        )
        assertEquals(
            listOf("Nov 13", "Nov 13, 2026", "November 13", "November 13, 2026", "November 2026", "November"),
            forms(DateOrder.MonthFirst),
        )
        assertEquals(
            listOf("Nov 13", "2026 Nov 13", "November 13", "2026 November 13", "November 2026", "November"),
            forms(DateOrder.YearFirst),
        )
    }

    @Test
    fun `a short date says its year only when it is not in the current year`() {
        val newYear = LocalDate.of(2027, 1, 2)
        val lastDays = LocalDate.of(2026, 12, 30)

        assertEquals("Jan 2", shortDateText(newYear, today = newYear, DateOrder.MonthFirst))
        assertEquals("Dec 30, 2026", shortDateText(lastDays, today = newYear, DateOrder.MonthFirst))
        assertEquals("30 Dec 2026", shortDateText(lastDays, today = newYear, DateOrder.DayFirst))
        assertEquals("2026 Dec 30", shortDateText(lastDays, today = newYear, DateOrder.YearFirst))
        assertEquals("a day of the next year", "Jan 2, 2027", shortDateText(newYear, today = lastDays, DateOrder.MonthFirst))
    }

    @Test
    fun `the symbol goes before the number in the United States and after it in Vietnam`() {
        assertEquals(AmountParts("₫", "902,664", ""), onPhone("en-US") { VND.formatParts(902_664) })
        assertEquals(AmountParts("", "902.664", "$NBSP₫"), onPhone("vi-VN") { VND.formatParts(902_664) })
        assertEquals(AmountParts("-$", "12.50", ""), onPhone("en-US") { USD.formatParts(-1_250) })
        assertEquals("a code that is no currency", AmountParts("", "12,50", " ABC"), onPhone("de-DE") { ABC.formatParts(1_250) })
    }

    @Test
    fun `in every region the three parts of an amount join to its text, with the number between them`() {
        val amounts = listOf(VND to 902_664L, USD to -1_250L, KWD to 1_234_567L, ABC to 1_250L, BillCurrency("", 0) to 70_000L)
        for (tag in REGIONS) {
            onPhone(tag) {
                for ((currency, minor) in amounts) {
                    val parts = currency.formatParts(minor)

                    assertEquals("$tag: $parts", currency.format(minor), parts.prefix + parts.number + parts.suffix)
                    assertTrue("$tag: $parts", parts.number.first().isDigit() && parts.number.last().isDigit())
                    assertTrue("$tag: $parts", (parts.prefix + parts.suffix).none(Char::isDigit))
                }
                val dong = VND.formatParts(902_664)
                assertTrue("$tag: $dong", "₫" in dong.prefix + dong.suffix)
            }
        }
    }

    @Test
    fun `in every region an amount written for a field reads back as the same amount, with no decimals, two or three`() {
        val amounts = listOf(VND to 902_664L, USD to 123_456L, USD to -300L, KWD to 1_234_567L)
        for (tag in REGIONS) {
            onPhone(tag) {
                for ((currency, minor) in amounts) {
                    val typed = currency.inputText(minor)

                    assertEquals("$tag: $typed", minor, currency.parse(typed))
                    assertTrue("$tag: $typed has no grouping", typed.count { !it.isDigit() && it != '-' } <= 1)
                }
            }
        }
        assertEquals("1234.56", onPhone("en-US") { USD.inputText(123_456) })
        assertEquals("1234,56", onPhone("vi-VN") { USD.inputText(123_456) })
        assertEquals("1234,56", onPhone("de-DE") { USD.inputText(123_456) })
    }

    @Test
    fun `a field shows an amount with the decimal mark the amount itself is written with`() {
        // In French-speaking Switzerland an amount of money has a point before its decimals, and other numbers a comma.
        for (tag in REGIONS + "fr-CH") {
            onPhone(tag) {
                for (currency in listOf(USD, ABC, BillCurrency("", 2))) {
                    val written = currency.format(123_450)
                    val mark = written[written.lastIndexOf("50") - 1]

                    assertEquals("$tag: $written", "1234${mark}50", currency.inputText(123_450))
                }
            }
        }
    }

    @Test
    fun `a rate in its field has the six digits it is shown with, the region's decimal mark and no grouping`() {
        val rate = BigDecimal("25350.4321")

        assertEquals("1 USD = 25,350.4 VND", onPhone("en-US") { ExchangeRate("USD", "VND", rate).describe() })
        assertEquals("25350.4", onPhone("en-US") { ExchangeRate.rateInputText(rate) })
        assertEquals("25350,4", onPhone("vi-VN") { ExchangeRate.rateInputText(rate) })
        assertEquals("25350", onPhone("de-DE") { ExchangeRate.rateInputText(BigDecimal("25350.0000")) })
    }

    @Test
    fun `in every region a request carries the same codes and links, and its details the region's amount`() {
        val requests = listOf(
            Triple(PaymentMethod.PromptPay("0801234567"), THB, 42_250L),
            Triple(PaymentMethod.VietQr("970416", "000000000", "NGUYEN VAN A"), VND, 902_664L),
            Triple(PaymentMethod.Upi("asha.rao@okaxis", "Asha Rao"), INR, 125_050L),
            Triple(PaymentMethod.PayPalMe("DiaRusso"), USD, 1_250L),
            Triple(PaymentMethod.SepaQr("Red Cross of Belgium", "BE72000000001616"), EUR, 12_345L),
        )
        fun codes() = requests.map { (method, currency, minor) ->
            PaymentRequestBuilder.build(method, minor, currency, note = "Pho 24").let { it.link to it.qrPayload }
        }
        val inTheUnitedStates = codes()
        val withoutCode = inTheUnitedStates.filter { it.first == null && it.second == null }
        assertTrue("requests with neither a link nor a code: $withoutCode", withoutCode.isEmpty())

        for (tag in REGIONS) assertEquals(tag, inTheUnitedStates, onPhone(tag) { codes() })
        val details = onPhone("vi-VN") { PaymentRequestBuilder.build(requests[1].first, 902_664L, VND, note = null).details }
        assertTrue(details, "Amount: 902.664$NBSP₫" in details)
    }

    /** [block] on a phone set to the region of [tag], such as "vi-VN", with the machine's own region back after it. */
    private fun <T> onPhone(tag: String, block: () -> T): T = onPhone(Locale.forLanguageTag(tag), block)

    private fun <T> onPhone(phone: Locale, block: () -> T): T {
        val before = Locale.getDefault()
        Locale.setDefault(phone)
        try {
            return block()
        } finally {
            Locale.setDefault(before)
        }
    }

    private fun String.hasOnlyLatinDigits(): Boolean = all { !it.isDigit() || it in '0'..'9' }
}

private val REGIONS = listOf("en-US", "vi-VN", "de-DE", "fr-FR", "hi-IN", "ja-JP", "ar-EG", "fa-IR")

private val VND = BillCurrency("VND", 0)
private val USD = BillCurrency("USD", 2)
private val KWD = BillCurrency("KWD", 3)
private val THB = BillCurrency("THB", 2)
private val INR = BillCurrency("INR", 2)
private val EUR = BillCurrency("EUR", 2)

/** A currency the platform does not know, as one read off a bill can be. */
private val ABC = BillCurrency("ABC", 2)

private val NOV_13: LocalDate = LocalDate.of(2026, 11, 13)
private val NOV_20: LocalDate = LocalDate.of(2026, 11, 20)

/** The space that does not break, which several regions put between an amount and its symbol. */
private const val NBSP = "\u00A0"
