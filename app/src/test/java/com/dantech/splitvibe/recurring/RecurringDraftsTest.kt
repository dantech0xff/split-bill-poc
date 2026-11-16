package com.dantech.splitvibe.recurring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestClock
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * How recurring bills make their drafts, with a clock the test moves from day to day: one draft for
 * each occurrence, made once however often or however many at once the work runs, made late for the
 * days it did not run, none while paused, and kept when the recurring bill is deleted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecurringDraftsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val clock = TestClock(LocalDate.of(2026, 11, 16))
    private val bills = BillRepository(storage.database, storage.people, storage.pages) { clock.millis() }
    private val recurring = RecurringBillRepository(storage.database, clock)

    /** The drafts a notice was posted for, in order; the runs at once post from two threads. */
    private val notices: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val drafts = RecurringDrafts(recurring, bills, notify = { notices += it.id }, clock = clock)

    /** The rent, finished, with what belongs to its one occasion: photos, read text, an instruction, a link and a group. */
    private val rent: SavedBill = runBlocking {
        bills.save(
            SpendingFixtures.finished("rent-nov", "2026-11-01", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh", "Khoa")).copy(
                instruction = "Split with Minh and Khoa",
                ocrDocument = "TIEN NHA 9.000.000",
                pages = listOf("${UUID.randomUUID()}.jpg"),
                share = ShareLink(shareId = "share-1", url = "https://links.invalid/s/#token"),
                group = GroupLink("group-1", SpendingFixtures.FLAT, revision = 3, memberOf = emptyMap()),
            ),
        )
    }

    @After
    fun tearDown() = storage.close()

    private fun makeRecurring(frequency: Frequency, start: String): RecurringBill = runBlocking {
        recurring.create(rent, "Rent", RecurrenceSchedule(frequency, LocalDate.parse(start)))
    }

    /** Runs the work on every day from the clock's day to [last], and returns the drafts made on each day that made any. */
    private fun runDaily(last: LocalDate): Map<LocalDate, List<SavedBill>> {
        val made = linkedMapOf<LocalDate, List<SavedBill>>()
        var day = clock.today
        while (!day.isAfter(last)) {
            clock.moveTo(day)
            runBlocking { drafts.makeDue() }.takeIf { it.isNotEmpty() }?.let { made[day] = it }
            day = day.plusDays(1)
        }
        return made
    }

    private fun stored(): List<SavedBill> = runBlocking { bills.listings().first().mapNotNull { bills.get(it.id) } }

    @Test
    fun `a monthly bill makes one draft in each period, on its day or the last of a shorter month, through months of every length`() {
        clock.moveTo(LocalDate.of(2027, 1, 30))
        val template = makeRecurring(Frequency.MONTHLY, "2027-01-31")

        // February 2027 has 28 days, February 2028 has 29, and months of 30 and 31 days come between.
        val made = runDaily(LocalDate.of(2028, 4, 1))

        val days = listOf(
            "2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30", "2027-05-31", "2027-06-30", "2027-07-31", "2027-08-31",
            "2027-09-30", "2027-10-31", "2027-11-30", "2027-12-31", "2028-01-31", "2028-02-29", "2028-03-31",
        ).map(LocalDate::parse)
        // Each draft is made on its own day and on no other.
        assertEquals(days, made.keys.toList())
        made.forEach { (day, onThatDay) -> assertEquals(listOf(Recurrence.draftId(template.id, day)), onThatDay.map { it.id }) }
        val ids = days.map { Recurrence.draftId(template.id, it) }
        assertEquals((ids + rent.id).sorted(), stored().map { it.id }.sorted())
        // Every draft has an id the proxy takes, so a finished one can go to a group, a request or a link.
        ids.forEach { UUID.fromString(it) }
        assertEquals(ids, notices)
    }

    @Test
    fun `a draft is the bill and its split on the day of its occurrence, unfinished and without what belonged to the first occasion`() {
        val template = makeRecurring(Frequency.MONTHLY, "2026-12-01")
        clock.moveTo(LocalDate.of(2026, 12, 1))

        val draft = runBlocking { drafts.makeDue() }.single()

        assertEquals(Recurrence.draftId(template.id, LocalDate.of(2026, 12, 1)), draft.id)
        assertEquals(BillStatus.DRAFT, draft.status)
        assertEquals(rent.bill.copy(date = "2026-12-01"), draft.bill)
        assertEquals(rent.plan, draft.plan)
        assertEquals(listOf(3_000_000L, 3_000_000L, 3_000_000L), draft.shares.map { it.totalMinor })
        assertEquals("", draft.instruction)
        assertEquals("", draft.ocrDocument)
        assertTrue(draft.pages.isEmpty())
        assertNull(draft.share)
        assertNull(draft.group)
        assertEquals(draft, runBlocking { bills.get(draft.id) })
    }

    @Test
    fun `two runs at once make each draft once, with one notice each`() {
        repeat(20) { round ->
            clock.moveTo(LocalDate.of(2026, 11, 16))
            val template = makeRecurring(Frequency.WEEKLY, "2026-11-17")
            // Ten weeks without a run, so both runs find ten drafts to make.
            clock.moveTo(LocalDate.of(2027, 1, 19))

            val made = runBlocking {
                listOf(async(Dispatchers.IO) { drafts.makeDue() }, async(Dispatchers.IO) { drafts.makeDue() }).awaitAll().flatten()
            }

            val ids = (0L until 10L).map { Recurrence.draftId(template.id, LocalDate.of(2026, 11, 17).plusWeeks(it)) }
            assertEquals("round $round", ids.sorted(), made.map { it.id }.sorted())
            assertEquals("round $round", ids.sorted(), notices.filter { it in ids }.sorted())
            ids.forEach { assertNotNull("round $round", runBlocking { bills.get(it) }) }
            runBlocking { recurring.delete(template.id) }
        }
    }

    @Test
    fun `the periods missed while the work did not run are made at the next run, and only once`() {
        val template = makeRecurring(Frequency.MONTHLY, "2026-12-01")
        // The phone stays off from December to the middle of March.
        clock.moveTo(LocalDate.of(2027, 3, 15))

        val caughtUp = runBlocking { drafts.makeDue() }
        val again = runBlocking { drafts.makeDue() }

        val days = listOf("2026-12-01", "2027-01-01", "2027-02-01", "2027-03-01").map(LocalDate::parse)
        assertEquals(days.map { Recurrence.draftId(template.id, it) }, caughtUp.map { it.id })
        assertEquals(days.map(LocalDate::toString), caughtUp.map { it.bill.date })
        assertTrue(again.isEmpty())
        assertEquals(4, notices.size)
        assertEquals(LocalDate.of(2027, 4, 1), runBlocking { recurring.all.first() }.single().next(clock.today))
    }

    @Test
    fun `a paused bill makes nothing, and once resumed it skips the days that went by meanwhile`() {
        val template = makeRecurring(Frequency.WEEKLY, "2026-11-17")
        runBlocking { recurring.pause(template.id) }

        assertTrue(runDaily(LocalDate.of(2026, 12, 7)).isEmpty())
        assertTrue(runBlocking { drafts.anyActive() }.not())

        runBlocking { recurring.resume(template.id) }
        val made = runDaily(LocalDate.of(2026, 12, 15))

        // Only the Tuesdays from the day it was resumed: 8 and 15 December.
        assertEquals(listOf(LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 15)), made.keys.toList())
        assertEquals(2, notices.size)
    }

    @Test
    fun `deleting a recurring bill keeps the drafts it made, and makes no more`() {
        val template = makeRecurring(Frequency.WEEKLY, "2026-11-17")
        val made = runDaily(LocalDate.of(2026, 11, 24)).values.flatten()

        runBlocking { recurring.delete(template.id) }
        val later = runDaily(LocalDate.of(2026, 12, 31))

        assertEquals(2, made.size)
        made.forEach { assertEquals(it, runBlocking { bills.get(it.id) }) }
        assertTrue(later.isEmpty())
        assertTrue(runBlocking { recurring.all.first() }.isEmpty())
    }

    @Test
    fun `a draft the user deleted is not made again`() {
        val template = makeRecurring(Frequency.MONTHLY, "2026-11-16")
        val draft = runBlocking { drafts.makeDue() }.single()

        runBlocking { bills.delete(draft.id) }
        val again = runBlocking { drafts.makeDue() }

        assertEquals(Recurrence.draftId(template.id, LocalDate.of(2026, 11, 16)), draft.id)
        assertTrue(again.isEmpty())
        assertNull(runBlocking { bills.get(draft.id) })
        assertEquals(1, notices.size)
    }

    @Test
    fun `a start before today only sets the day the bill comes back on`() {
        val template = makeRecurring(Frequency.MONTHLY, "2026-10-05")

        assertTrue(runBlocking { drafts.makeDue() }.isEmpty())
        assertEquals(LocalDate.of(2026, 12, 5), template.next(clock.today))
        assertEquals(listOf(LocalDate.of(2026, 12, 5)), runDaily(LocalDate.of(2026, 12, 5)).keys.toList())
    }
}
