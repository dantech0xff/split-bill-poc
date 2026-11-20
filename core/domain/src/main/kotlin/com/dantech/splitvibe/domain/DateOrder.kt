package com.dantech.splitvibe.domain

import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The order in which a region writes the day, the month and the year of a date. The app writes its
 * dates in English words, so the order is all it takes from the region.
 */
enum class DateOrder {
    /** The day first, as in Vietnam, Germany, France, Sweden, India, Egypt and Iran: "13 Nov 2026". */
    DayFirst,

    /** The month first, as in the United States and English-speaking Canada: "Nov 13, 2026". */
    MonthFirst,

    /** The year first, as in Japan: "2026 Nov 13". */
    YearFirst,
}

/**
 * The order of a date with the name of its month as [locale] writes it, read from its medium pattern,
 * such as "d MMM, y" in Vietnam or "MMM d, y" in the United States. The app writes the names of
 * months, and some regions write a date of digits alone in another order: English-speaking Canada
 * and Sweden write "y-MM-dd", but "MMM d, y" and "d MMM y" with a month name. Only the letters for
 * the day, the month and the year count: quoted words, separators and the invisible marks that some
 * regions put between them to keep the direction of the text are left out.
 */
fun dateOrderOf(locale: Locale): DateOrder {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale)
    return when (pattern.replace(QUOTED_TEXT, "").firstOrNull { it in DATE_FIELDS }) {
        'y', 'u' -> DateOrder.YearFirst
        'M', 'L' -> DateOrder.MonthFirst
        else -> DateOrder.DayFirst
    }
}

/** Words written as they are inside a date pattern, between single quotes. */
private val QUOTED_TEXT = Regex("'[^']*'")

/** The letters of a date pattern for the day of the month, the month and the year. */
private const val DATE_FIELDS = "dMLyu"
