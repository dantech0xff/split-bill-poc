package com.dantech.splitvibe.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecurrenceTest {

    private fun monthly(start: String) = RecurrenceSchedule(Frequency.MONTHLY, LocalDate.parse(start))

    private fun weekly(start: String) = RecurrenceSchedule(Frequency.WEEKLY, LocalDate.parse(start))

    private fun day(text: String): LocalDate = LocalDate.parse(text)

    private fun days(vararg texts: String): List<LocalDate> = texts.map(LocalDate::parse)

    @Test
    fun `a monthly bill on the 31st falls on the last day of shorter months and comes back to the 31st`() {
        // A year of months of 31, 28, 30 and 31 days, each occurrence worked out from the start.
        assertEquals(
            days(
                "2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30",
                "2026-07-31", "2026-08-31", "2026-09-30", "2026-10-31", "2026-11-30", "2026-12-31",
            ),
            Recurrence.occurrences(monthly("2026-01-31"), day("2026-12-31")),
        )
    }

    @Test
    fun `a bill on the 29th or the 30th falls on the 28th of a short February and comes back after it`() {
        assertEquals(days("2027-01-29", "2027-02-28", "2027-03-29"), Recurrence.occurrences(monthly("2027-01-29"), day("2027-03-29")))
        assertEquals(
            days("2026-01-30", "2026-02-28", "2026-03-30", "2026-04-30", "2026-05-30"),
            Recurrence.occurrences(monthly("2026-01-30"), day("2026-05-30")),
        )
    }

    @Test
    fun `in a leap year a bill on the 29th, 30th or 31st falls on 29 February`() {
        assertEquals(days("2028-01-29", "2028-02-29", "2028-03-29"), Recurrence.occurrences(monthly("2028-01-29"), day("2028-03-29")))
        assertEquals(days("2028-01-30", "2028-02-29", "2028-03-30"), Recurrence.occurrences(monthly("2028-01-30"), day("2028-03-30")))
        assertEquals(days("2028-01-31", "2028-02-29", "2028-03-31"), Recurrence.occurrences(monthly("2028-01-31"), day("2028-03-31")))
    }

    @Test
    fun `a bill that starts on 29 February comes back on the 29th, and on 29 February only in leap years`() {
        val schedule = monthly("2028-02-29")

        assertEquals(day("2028-03-29"), Recurrence.occurrence(schedule, 1))
        assertEquals(day("2029-02-28"), Recurrence.occurrence(schedule, 12))
        assertEquals(day("2029-03-29"), Recurrence.occurrence(schedule, 13))
        assertEquals(day("2032-02-29"), Recurrence.occurrence(schedule, 48))
    }

    @Test
    fun `a weekly bill comes back on the same day of the week, across months and years`() {
        val schedule = weekly("2026-12-14")

        assertEquals(
            days("2026-12-14", "2026-12-21", "2026-12-28", "2027-01-04", "2027-01-11"),
            Recurrence.occurrences(schedule, day("2027-01-17")),
        )
        assertEquals(day("2026-12-21"), Recurrence.next(schedule, day("2026-12-14")))
        assertEquals(day("2027-01-04"), Recurrence.next(schedule, day("2027-01-03")))
        assertEquals(day("2029-02-26"), Recurrence.next(schedule, day("2029-02-20")))
    }

    @Test
    fun `before the start there is no occurrence yet, and the next one is the start`() {
        val schedule = monthly("2026-11-30")

        assertEquals(emptyList<LocalDate>(), Recurrence.occurrences(schedule, day("2026-11-29")))
        assertEquals(days("2026-11-30"), Recurrence.occurrences(schedule, day("2026-11-30")))
        assertEquals(day("2026-11-30"), Recurrence.next(schedule, day("2026-01-01")))
        assertEquals(day("2026-11-30"), Recurrence.next(schedule, day("2026-11-29")))
        assertEquals(day("2026-12-30"), Recurrence.next(schedule, day("2026-11-30")))
        assertEquals(emptyList<LocalDate>(), Recurrence.occurrences(weekly("2026-11-16"), day("2026-11-15")))
    }

    @Test
    fun `the next occurrence of a bill on the 31st is after the day given, never on it`() {
        val rent = monthly("2026-01-31")

        assertEquals(day("2026-02-28"), Recurrence.next(rent, day("2026-01-31")))
        assertEquals(day("2026-02-28"), Recurrence.next(rent, day("2026-02-27")))
        assertEquals(day("2026-03-31"), Recurrence.next(rent, day("2026-02-28")))
        assertEquals(day("2026-05-31"), Recurrence.next(rent, day("2026-04-30")))
        assertEquals(day("2036-01-31"), Recurrence.next(rent, day("2035-12-31")))
    }

    @Test
    fun `each period has its own key and draft id, which every device works out the same`() {
        val rent = monthly("2026-01-31")
        val periods = Recurrence.occurrences(rent, day("2026-12-31"))
        val ids = periods.map { Recurrence.draftId("rent-flat", it) }

        assertEquals("2026-02-28", Recurrence.periodKey(periods[1]))
        assertEquals("rent-flat_2026-02-28", ids[1])
        assertEquals(periods.size, ids.toSet().size)
        // A second phone of the same account, catching up on the periods it missed, makes the same ids.
        val later = Recurrence.occurrences(RecurrenceSchedule(Frequency.MONTHLY, day("2026-01-31")), day("2026-12-31"))
        assertEquals(ids, later.map { Recurrence.draftId("rent-flat", it) })
    }

    @Test
    fun `for any start, each occurrence is one period after the last, on the start's day or the month's last`() {
        var start = day("2027-01-01")
        while (!start.isAfter(day("2029-12-31"))) {
            for (frequency in Frequency.entries) {
                val schedule = RecurrenceSchedule(frequency, start)
                val periods = (0L..36L).map { Recurrence.occurrence(schedule, it) }
                val case = "$schedule"

                assertEquals(case, periods, Recurrence.occurrences(schedule, periods.last()))
                assertEquals(case, start, Recurrence.next(schedule, start.minusDays(1)))
                periods.forEachIndexed { index, occurrence ->
                    when (frequency) {
                        Frequency.WEEKLY -> assertEquals(case, 7L * index, ChronoUnit.DAYS.between(start, occurrence))

                        Frequency.MONTHLY -> {
                            val month = YearMonth.from(start).plusMonths(index.toLong())
                            assertEquals(case, month.atDay(minOf(start.dayOfMonth, month.lengthOfMonth())), occurrence)
                        }
                    }
                    if (index > 0) assertEquals(case, occurrence, Recurrence.next(schedule, occurrence.minusDays(1)))
                    if (index < periods.lastIndex) assertEquals(case, periods[index + 1], Recurrence.next(schedule, occurrence))
                }
            }
            start = start.plusDays(1)
        }
    }

    @Test
    fun `a schedule is stored as its frequency and its first day`() {
        val stored = Json.encodeToString(RecurrenceSchedule.serializer(), monthly("2026-01-31"))

        assertEquals("""{"frequency":"monthly","start":"2026-01-31"}""", stored)
        assertEquals(monthly("2026-01-31"), Json.decodeFromString(RecurrenceSchedule.serializer(), stored))
        val weeklyStored = """{"frequency": "weekly", "start": "2026-11-16"}"""
        assertEquals(weekly("2026-11-16"), Json.decodeFromString(RecurrenceSchedule.serializer(), weeklyStored))
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(RecurrenceSchedule.serializer(), """{"frequency":"monthly","start":"31/01/2026"}""")
        }
    }
}
