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
    /** The day first, as in Vietnam, Germany, France, India and Egypt: "13 Nov 2026". */
    DayFirst,

    /** The month first, as in the United States: "Nov 13, 2026". */
    MonthFirst,

    /** The year first, as in Japan and Iran: "2026 Nov 13". */
    YearFirst,
}

/**
 * The order of the short date that [locale] writes, such as "dd/MM/y" in Vietnam. Only the letters
 * for the day, the month and the year count: quoted words, separators and the invisible marks that
 * some regions put between them to keep the direction of the text are left out.
 */
fun dateOrderOf(locale: Locale): DateOrder {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
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
