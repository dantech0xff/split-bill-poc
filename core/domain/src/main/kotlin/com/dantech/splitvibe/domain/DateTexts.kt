package com.dantech.splitvibe.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.util.Locale

// The app writes a date in the order of the phone's region and in the words of its texts: "13 Nov"
// on a phone set to Vietnam and "Nov 13" on one set to the United States, with the English names of
// the months, the digits 0 to 9 and the Gregorian calendar in every region. The region gives only the
// order, as dateOrderOf reads it; each of the three orders has fixed English patterns of its own.

/**
 * [day] short, as a list dates its rows: "13 Nov" where the day comes first, "Nov 13" elsewhere. A
 * day outside the year of [today] is written with its year, as [dateWithYearText] writes it, so that
 * a bill of another year is not taken for one of this year. [today] is the caller's to give, so a
 * test or a screenshot can fix it.
 */
fun shortDateText(day: TemporalAccessor, today: LocalDate, order: DateOrder = regionDateOrder()): String {
    if (day.get(ChronoField.YEAR) != today.year) return dateWithYearText(day, order)
    return when (order) {
        DateOrder.DayFirst -> DAY_MONTH
        DateOrder.MonthFirst, DateOrder.YearFirst -> MONTH_DAY
    }.format(day)
}

/** [day] with its year: "13 Nov 2026" where the day comes first, "Nov 13, 2026" where the month does, "2026 Nov 13" where the year does. */
fun dateWithYearText(day: TemporalAccessor, order: DateOrder = regionDateOrder()): String = when (order) {
    DateOrder.DayFirst -> DAY_MONTH_YEAR
    DateOrder.MonthFirst -> MONTH_DAY_YEAR
    DateOrder.YearFirst -> YEAR_MONTH_DAY
}.format(day)

/** [day] with the whole name of its month and no year: "13 November" where the day comes first, "November 13" elsewhere. */
fun longDateText(day: TemporalAccessor, order: DateOrder = regionDateOrder()): String = when (order) {
    DateOrder.DayFirst -> DAY_FULL_MONTH
    DateOrder.MonthFirst, DateOrder.YearFirst -> FULL_MONTH_DAY
}.format(day)

/**
 * [day] with the whole name of its month and its year: "13 November 2026" where the day comes first,
 * "November 13, 2026" where the month does, "2026 November 13" where the year does.
 */
fun longDateWithYearText(day: TemporalAccessor, order: DateOrder = regionDateOrder()): String = when (order) {
    DateOrder.DayFirst -> DAY_FULL_MONTH_YEAR
    DateOrder.MonthFirst -> FULL_MONTH_DAY_YEAR
    DateOrder.YearFirst -> YEAR_FULL_MONTH_DAY
}.format(day)

/** [month] and its year, such as "November 2026", which every region writes in this order. */
fun monthYearText(month: TemporalAccessor): String = MONTH_YEAR.format(month)

/** [month] by its name alone, such as "November", as a tile or a heading of that month has it in every region. */
fun monthText(month: TemporalAccessor): String = MONTH.format(month)

/** The order of a date in the region the phone is set to. */
private fun regionDateOrder(): DateOrder {
    val region = regionLocale()
    lastOrder?.let { (of, order) -> if (of == region) return order }
    return dateOrderOf(region).also { lastOrder = region to it }
}

// A list writes a date in each of its rows, and the order is read from the region's date patterns,
// so it is read again only when the phone is set to another region, as the region itself is. A
// thread that reads an older pair reads the order again, which is all that a race costs.
@Volatile
private var lastOrder: Pair<Locale, DateOrder>? = null

/** A date pattern in [TEXT_LOCALE], for the English names of the months; a formatter writes the digits 0 to 9 in any locale. */
private fun english(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, TEXT_LOCALE)

private val DAY_MONTH = english("d MMM")
private val MONTH_DAY = english("MMM d")
private val DAY_MONTH_YEAR = english("d MMM yyyy")
private val MONTH_DAY_YEAR = english("MMM d, yyyy")
private val YEAR_MONTH_DAY = english("yyyy MMM d")
private val DAY_FULL_MONTH = english("d MMMM")
private val FULL_MONTH_DAY = english("MMMM d")
private val DAY_FULL_MONTH_YEAR = english("d MMMM yyyy")
private val FULL_MONTH_DAY_YEAR = english("MMMM d, yyyy")
private val YEAR_FULL_MONTH_DAY = english("yyyy MMMM d")

/** The month as a name standing on its own, as a heading has it. */
private val MONTH_YEAR = english("LLLL yyyy")
private val MONTH = english("LLLL")
