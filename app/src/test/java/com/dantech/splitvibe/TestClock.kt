package com.dantech.splitvibe

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock that a test moves from day to day itself, in UTC, so a year of days takes no time. */
class TestClock(today: LocalDate) : Clock() {

    @Volatile
    private var now: Instant = morningOf(today)

    val today: LocalDate get() = now.atZone(ZoneOffset.UTC).toLocalDate()

    /** Moves the clock to the morning of [day]. */
    fun moveTo(day: LocalDate) {
        now = morningOf(day)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = fixed(now, zone)

    override fun instant(): Instant = now

    private fun morningOf(day: LocalDate): Instant = day.atTime(MORNING_HOUR, 0).toInstant(ZoneOffset.UTC)

    private companion object {
        const val MORNING_HOUR = 9
    }
}
