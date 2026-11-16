package com.dantech.splitvibe.data.recurring

import androidx.room.withTransaction
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.bills.storedJson
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The recurring bills kept on the phone, each of which makes a draft on every occurrence of its
 * schedule (see [com.dantech.splitvibe.recurring.RecurringDrafts]). [clock] tells the day, in the
 * zone the phone is in.
 */
class RecurringBillRepository(
    private val database: SplitVibeDatabase,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.recurringBills()

    /** Every recurring bill, the first made first. */
    val all: Flow<List<RecurringBill>> = dao.all().map { rows -> rows.mapNotNull(::read) }

    /** The recurring bills that are not paused, the first made first. */
    suspend fun active(): List<RecurringBill> = dao.active().mapNotNull(::read)

    /**
     * Makes [saved] come back on [schedule], called [name], and returns it. Its first draft is
     * [firstDraft], an occurrence of [schedule], or the first occurrence from today on when that is
     * later: no draft is made for a day before either. The schedule's start only sets the days it
     * comes back on, as each occurrence is counted from it. So a schedule that starts on the 31st, as
     * the rent it comes from, is back on the 31st after a first draft on the 30th of a shorter month.
     */
    suspend fun create(saved: SavedBill, name: String, schedule: RecurrenceSchedule, firstDraft: LocalDate): RecurringBill {
        // Nothing of the one occasion is kept: the reading's warnings were about that bill only, and
        // the people who joined it from its link typed their own names, which are never suggested for
        // later bills, as a draft would once it is finished.
        val bill = saved.bill.copy(warnings = emptyList())
        val strangers = saved.share?.added?.values.orEmpty()
        val recurring = RecurringBill(
            id = newId(),
            name = name.trim().take(MAX_NAME).ifEmpty { saved.bill.merchant?.trim()?.take(MAX_NAME)?.ifEmpty { null } ?: DEFAULT_NAME },
            schedule = schedule,
            bill = bill,
            plan = strangers.fold(saved.plan) { plan, id -> plan.withParticipantRemoved(id, bill) },
            paused = false,
            createdAt = clock.millis(),
            madeThrough = maxOf(LocalDate.now(clock), firstDraft).minusDays(1),
        )
        dao.insert(recurring.toEntity())
        return recurring
    }

    suspend fun pause(id: String) = dao.pause(id)

    /** Starts [id] again from today; the occurrences that went by while it was paused are not made. */
    suspend fun resume(id: String) = dao.resume(id, LocalDate.now(clock).minusDays(1).toString())

    /**
     * Makes the draft of [id]'s occurrence on [day] with [make], and notes that the drafts of [id]
     * are made up to [day], in one transaction with the check that [id] still makes that draft: it is
     * there and not paused, and its drafts are not made up to [day] already. So a recurring bill
     * paused or deleted while a run is under way makes no more drafts, and a draft that a run which
     * ended meanwhile made, and the user deleted, is not made again. Returns what [make] made, or
     * null when [id] no longer makes that draft.
     */
    suspend fun makeDraft(id: String, day: LocalDate, make: suspend () -> SavedBill?): SavedBill? = database.withTransaction {
        val row = dao.get(id)
        if (row == null || row.paused || row.madeThrough >= day.toString()) return@withTransaction null
        make().also { dao.madeThrough(id, day.toString()) }
    }

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
        document = storedJson.encodeToString(RecurringBillContent.serializer(), RecurringBillContent(bill, plan)),
    )

    /** The bill [row] keeps; null for a row this version cannot read, such as one written by a newer version. */
    private fun read(row: RecurringBillEntity): RecurringBill? = try {
        val content = storedJson.decodeFromString(RecurringBillContent.serializer(), row.document)
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
    }
}
