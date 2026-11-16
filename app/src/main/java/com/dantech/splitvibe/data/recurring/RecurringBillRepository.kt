package com.dantech.splitvibe.data.recurring

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The recurring bills kept on the phone, each of which makes a draft on every occurrence of its
 * schedule (see [com.dantech.splitvibe.recurring.RecurringDrafts]). [clock] tells the day, in the
 * zone the phone is in.
 */
class RecurringBillRepository(
    database: SplitVibeDatabase,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.recurringBills()

    /** Every recurring bill, the first made first. */
    val all: Flow<List<RecurringBill>> = dao.all().map { rows -> rows.mapNotNull(::read) }

    /** The recurring bills that are not paused, the first made first. */
    suspend fun active(): List<RecurringBill> = dao.active().mapNotNull(::read)

    /**
     * Makes [saved] come back on [schedule], called [name], and returns it. Its drafts start on the
     * first occurrence from today on: a start before today only sets the day it comes back on.
     */
    suspend fun create(saved: SavedBill, name: String, schedule: RecurrenceSchedule): RecurringBill {
        val bill = RecurringBill(
            id = newId(),
            name = name.trim().take(MAX_NAME).ifEmpty { saved.bill.merchant?.trim()?.take(MAX_NAME)?.ifEmpty { null } ?: DEFAULT_NAME },
            schedule = schedule,
            bill = saved.bill,
            plan = saved.plan,
            paused = false,
            createdAt = clock.millis(),
            madeThrough = LocalDate.now(clock).minusDays(1),
        )
        dao.insert(bill.toEntity())
        return bill
    }

    suspend fun pause(id: String) = dao.pause(id)

    /** Starts [id] again from today; the occurrences that went by while it was paused are not made. */
    suspend fun resume(id: String) = dao.resume(id, LocalDate.now(clock).minusDays(1).toString())

    /** Notes that the drafts of [id] are made up to [day], unless they are made further already. */
    suspend fun madeThrough(id: String, day: LocalDate) = dao.madeThrough(id, day.toString())

    /** Deletes [id]. The drafts it made are bills of their own and stay. */
    suspend fun delete(id: String) = dao.delete(id)

    private fun RecurringBill.toEntity() = RecurringBillEntity(
        id = id,
        name = name,
        frequency = schedule.frequency.name,
        start = schedule.start.toString(),
        paused = paused,
        createdAt = createdAt,
        madeThrough = madeThrough.toString(),
        document = json.encodeToString(RecurringBillContent.serializer(), RecurringBillContent(bill, plan)),
    )

    /** The bill [row] keeps; null for a row this version cannot read, such as one written by a newer version. */
    private fun read(row: RecurringBillEntity): RecurringBill? = try {
        val content = json.decodeFromString(RecurringBillContent.serializer(), row.document)
        RecurringBill(
            id = row.id,
            name = row.name,
            schedule = RecurrenceSchedule(Frequency.valueOf(row.frequency), LocalDate.parse(row.start)),
            bill = content.bill,
            plan = content.plan,
            paused = row.paused,
            createdAt = row.createdAt,
            madeThrough = LocalDate.parse(row.madeThrough),
        )
    } catch (e: IllegalArgumentException) {
        // A document that does not read as one, which is a SerializationException, or an unknown frequency.
        null
    } catch (e: DateTimeParseException) {
        null
    }

    companion object {
        /** As long as a name the user types for anything else. */
        const val MAX_NAME = 80

        /** The name of a recurring bill made from a bill without a merchant, when none is given. */
        const val DEFAULT_NAME = "Bill"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
