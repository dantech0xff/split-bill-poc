package com.dantech.splitvibe.text

import com.dantech.splitvibe.domain.TEXT_LOCALE
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAccessor

/** [day] as the app writes a date, such as "Nov 19, 2026": in [TEXT_LOCALE], as the texts around it are. */
fun dateText(day: TemporalAccessor): String = DATES.format(day)

private val DATES: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(TEXT_LOCALE)
