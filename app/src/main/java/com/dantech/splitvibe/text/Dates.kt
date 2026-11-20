package com.dantech.splitvibe.text

import com.dantech.splitvibe.domain.dateWithYearText
import java.time.temporal.TemporalAccessor

/**
 * [day] as the app writes a date with its year: "Nov 19, 2026" on a phone set to the United States,
 * "19 Nov 2026" on one set to Vietnam. The order is the phone's region's, and the month names are
 * English, as the texts around them are.
 */
fun dateText(day: TemporalAccessor): String = dateWithYearText(day)
