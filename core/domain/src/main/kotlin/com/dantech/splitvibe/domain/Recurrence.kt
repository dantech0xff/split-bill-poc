package com.dantech.splitvibe.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** How often a recurring bill comes back. */
@Serializable
enum class Frequency {
    /** Every week, on the day of the week the schedule starts on. */
    @SerialName("weekly")
    WEEKLY,

    /** Every month, on the day of the month the schedule starts on, or the month's last day when it is shorter. */
    @SerialName("monthly")
    MONTHLY,
}

/** When a recurring bill, such as the rent, comes back: every week or every month from [start], its first occurrence. */
@Serializable
data class RecurrenceSchedule(val frequency: Frequency, @Serializable(with = LocalDateAsString::class) val start: LocalDate)

/**
 * The occurrences of a [RecurrenceSchedule], each the start of one period of a recurring bill.
 *
 * Every occurrence is worked out from the start, never from the one before it. A monthly schedule
 * that starts on the 31st falls on the last day of a shorter month, and is back on the 31st in the
 * next month that has one, instead of staying on the 28th for good.
 */
object Recurrence {

    /** The occurrence [index] periods after the start, which is occurrence 0. */
    fun occurrence(schedule: RecurrenceSchedule, index: Long): LocalDate = when (schedule.frequency) {
        Frequency.WEEKLY -> schedule.start.plusWeeks(index)
        Frequency.MONTHLY -> schedule.start.plusMonths(index)
    }

    /** Every occurrence from the start up to [until], [until] itself included; none when [until] is before the start. */
    fun occurrences(schedule: RecurrenceSchedule, until: LocalDate): List<LocalDate> =
        generateSequence(0L) { it + 1 }.map { occurrence(schedule, it) }.takeWhile { !it.isAfter(until) }.toList()

    /** The first occurrence after [after], never on that day itself; the start when [after] is before it. */
    fun next(schedule: RecurrenceSchedule, after: LocalDate): LocalDate {
        if (after.isBefore(schedule.start)) return schedule.start
        // Close to the answer at once, rather than counting every period since the start, then on to it.
        var index = when (schedule.frequency) {
            Frequency.WEEKLY -> ChronoUnit.WEEKS.between(schedule.start, after)
            Frequency.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(schedule.start), YearMonth.from(after))
        }
        while (!occurrence(schedule, index).isAfter(after)) index++
        return occurrence(schedule, index)
    }

    /** The key of the period that begins on [occurrence]: its date in ISO form, such as "2026-02-28". */
    fun periodKey(occurrence: LocalDate): String = occurrence.toString()

    /**
     * The id of the draft bill for the period of [templateId] that begins on [occurrence]: the
     * name-based UUID of `{templateId}_{periodKey}`. Every device works out the same id for the same
     * period, so a draft is made once, however many of them are signed in to the account. It is a
     * UUID because that is the only kind of bill id the proxy takes, when a finished draft goes to a
     * group, to a payment request or onto a bill's link.
     */
    fun draftId(templateId: String, occurrence: LocalDate): String =
        UUID.nameUUIDFromBytes("${templateId}_${periodKey(occurrence)}".toByteArray(Charsets.UTF_8)).toString()
}

/** Keeps a day as yyyy-mm-dd. */
object LocalDateAsString : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate {
        val text = decoder.decodeString()
        return try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            throw SerializationException("Not a day: $text", e)
        }
    }
}
