package com.dantech.splitvibe.data.recurring

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.domain.SplitPlan
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * A bill that comes back, such as the rent: what each of its drafts starts from, and when they are
 * made. It is kept on the phone only, and nothing of one occasion is in it: no photos, read text,
 * instruction, warnings, link, people who joined from the link, group or shares.
 */
data class RecurringBill(
    val id: String,
    val name: String,
    val schedule: RecurrenceSchedule,
    /** The bill each draft copies, with the day of its occurrence as its date. */
    val bill: Bill,
    /** Who takes part, how it is split and who paid, as each draft starts. */
    val plan: SplitPlan,
    /** A paused bill makes no drafts, and the occurrences that pass meanwhile are never made. */
    val paused: Boolean,
    val createdAt: Long,
    /**
     * The last occurrence dealt with: its draft made, or passed before the bill was made or while it
     * was paused. A draft deleted afterwards is therefore not made again.
     */
    val madeThrough: LocalDate,
) {
    /** The occurrences up to [today], [today] included, whose drafts are still to be made, earliest first. */
    fun due(today: LocalDate): List<LocalDate> = Recurrence.occurrences(schedule, today).filter { it.isAfter(madeThrough) }

    /** The day of the next draft still to be made, [today] at the earliest. */
    fun next(today: LocalDate): LocalDate = Recurrence.next(schedule, maxOf(madeThrough, today.minusDays(1)))

    /** The draft of the occurrence on [day], unfinished, for the user to check and finish. Its id is the same on every device. */
    fun draft(day: LocalDate, now: Long): SavedBill = SavedBill(
        id = Recurrence.draftId(id, day),
        createdAt = now,
        updatedAt = now,
        bill = bill.copy(date = day.toString()),
        plan = plan,
    )
}

/** What each draft of a recurring bill starts from, kept as JSON. */
@Serializable
internal data class RecurringBillContent(val bill: Bill, val plan: SplitPlan)

/** A recurring bill as the database keeps it: the schedule and marks as columns, the bill and its split as JSON. */
@Entity(tableName = "recurring_bills")
data class RecurringBillEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.dantech.splitvibe.domain.Frequency.name], such as "MONTHLY". */
    val frequency: String,
    /** The first occurrence, as yyyy-mm-dd. */
    val start: String,
    val paused: Boolean,
    val createdAt: Long,
    /** [RecurringBill.madeThrough] as yyyy-mm-dd, which sorts the same as the days it stands for. */
    val madeThrough: String,
    /** A [RecurringBillContent] as JSON. */
    val document: String,
)

@Dao
interface RecurringBillDao {
    /** Every recurring bill, the first made first. */
    @Query("SELECT * FROM recurring_bills ORDER BY createdAt, id")
    fun all(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills WHERE paused = 0 ORDER BY createdAt, id")
    suspend fun active(): List<RecurringBillEntity>

    @Insert
    suspend fun insert(bill: RecurringBillEntity)

    @Query("UPDATE recurring_bills SET paused = 1 WHERE id = :id")
    suspend fun pause(id: String)

    /** Starts [id] again: the occurrences up to [passed] went by while it was paused and are not made. */
    @Query("UPDATE recurring_bills SET paused = 0, madeThrough = MAX(madeThrough, :passed) WHERE id = :id")
    suspend fun resume(id: String, passed: String)

    /** Moves [RecurringBillEntity.madeThrough] on to [day], and never back, as two runs at once may finish in any order. */
    @Query("UPDATE recurring_bills SET madeThrough = :day WHERE id = :id AND madeThrough < :day")
    suspend fun madeThrough(id: String, day: String)

    @Query("DELETE FROM recurring_bills WHERE id = :id")
    suspend fun delete(id: String)
}
