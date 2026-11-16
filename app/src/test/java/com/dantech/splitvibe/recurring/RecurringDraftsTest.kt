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
import com.dantech.splitvibe.ui.recurring.firstDraftDay
import com.dantech.splitvibe.ui.recurring.suggestedSchedule
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

    /** The drafts of each notice, in the order the notices were posted; the runs at once post from two threads. */
    private val notices: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
    private val drafts = RecurringDrafts(recurring, bills, notify = { _, made -> notices += made.map { it.id } }, clock = clock)

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

    /** [rent] made to recur from [start], with its first draft then, or today when [start] is before today. */
    private fun makeRecurring(frequency: Frequency, start: String): RecurringBill = runBlocking {
        recurring.create(rent, "Rent", RecurrenceSchedule(frequency, LocalDate.parse(start)), firstDraft = LocalDate.parse(start))
    }

    /** [rent] made on [day] to recur every Tuesday from 17 November, its first draft then. */
    private fun makeRecurringOn(day: LocalDate): RecurringBill {
        clock.moveTo(day)
        return makeRecurring(Frequency.WEEKLY, "2026-11-17")
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
        assertEquals(ids.map(::listOf), notices)
    }

    @Test
    fun `a rent dated the 31st and repeated as the sheet suggests makes its drafts on the 31st, and on the last day of a shorter month`() {
        val october = runBlocking {
            bills.save(SpendingFixtures.finished("rent-oct", "2026-10-31", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh", "Khoa")))
        }
        val schedule = suggestedSchedule(october.bill.date, Frequency.MONTHLY, clock.today)
        val template = runBlocking { recurring.create(october, "Rent", schedule, firstDraftDay(schedule, clock.today)) }

        val made = runDaily(LocalDate.of(2027, 3, 31))

        assertEquals(RecurrenceSchedule(Frequency.MONTHLY, LocalDate.of(2026, 10, 31)), template.schedule)
        // November and February are short: their drafts are on their last days, and the 31st comes back after them.
        val days = listOf("2026-11-30", "2026-12-31", "2027-01-31", "2027-02-28", "2027-03-31").map(LocalDate::parse)
        assertEquals(days, made.keys.toList())
        made.forEach { (day, onThatDay) -> assertEquals(listOf(Recurrence.draftId(template.id, day)), onThatDay.map { it.id }) }
        // The bill itself is the occurrence of 31 October, which makes no draft.
        assertNull(runBlocking { bills.get(Recurrence.draftId(template.id, LocalDate.of(2026, 10, 31))) })
    }

    @Test
    fun `a bill of today repeated as the sheet suggests makes no draft of itself, and its first one a period later`() {
        val ofToday = runBlocking {
            bills.save(SpendingFixtures.finished("rent-today", "2026-11-16", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh")))
        }
        val schedule = suggestedSchedule(ofToday.bill.date, Frequency.MONTHLY, clock.today)
        val template = runBlocking { recurring.create(ofToday, "Rent", schedule, firstDraftDay(schedule, clock.today)) }

        assertTrue(runBlocking { drafts.makeDue() }.isEmpty())
        assertEquals(listOf(LocalDate.of(2026, 12, 16)), runDaily(LocalDate.of(2026, 12, 16)).keys.toList())
        assertEquals(LocalDate.of(2026, 11, 16), template.schedule.start)
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
    fun `a recurring bill leaves out who joined its bill from the link, and its reading's warnings, so no draft remembers them`() {
        val dinner = runBlocking {
            val read = SpendingFixtures.finished(
                "dinner",
                "2026-11-13",
                "Bún chả",
                BillCategory.FOOD_AND_DRINK,
                300_000,
                listOf("Minh"),
                strangers = listOf("An"),
            )
            bills.save(read.copy(bill = read.bill.copy(warnings = listOf("The bill total could not be read."))))
        }
        val fridays = RecurrenceSchedule(Frequency.WEEKLY, LocalDate.of(2026, 11, 13))
        val template = runBlocking { recurring.create(dinner, "Friday dinner", fridays, LocalDate.of(2026, 11, 20)) }
        clock.moveTo(LocalDate.of(2026, 11, 20))

        val draft = runBlocking { drafts.makeDue() }.single()
        runBlocking { bills.save(draft.copy(status = BillStatus.FINAL)) }

        assertEquals(listOf("Minh"), template.plan.participants.filterNot { it.isMe }.map { it.name })
        assertTrue(template.bill.warnings.isEmpty())
        assertEquals(listOf(150_000L, 150_000L), draft.shares.map { it.totalMinor })
        assertTrue(draft.bill.warnings.isEmpty())
        // Finishing the draft remembers only the people the user split with, as the bills did: Minh and the rent's Khoa.
        assertEquals(setOf("Minh", "Khoa"), runBlocking { storage.people.people.first() }.map { it.name }.toSet())
    }

    @Test
    fun `two runs at once make each draft once, and tell of each in one notice`() {
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
            assertEquals("round $round", ids.sorted(), notices.flatten().filter { it in ids }.sorted())
            ids.forEach { assertNotNull("round $round", runBlocking { bills.get(it) }) }
            runBlocking { recurring.delete(template.id) }
        }
    }

    @Test
    fun `the periods missed while the work did not run are made at the next run, only once, with one notice for them all`() {
        val template = makeRecurring(Frequency.MONTHLY, "2026-12-01")
        // The phone stays off from December to the middle of March.
        clock.moveTo(LocalDate.of(2027, 3, 15))

        val caughtUp = runBlocking { drafts.makeDue() }
        val again = runBlocking { drafts.makeDue() }

        val days = listOf("2026-12-01", "2027-01-01", "2027-02-01", "2027-03-01").map(LocalDate::parse)
        assertEquals(days.map { Recurrence.draftId(template.id, it) }, caughtUp.map { it.id })
        assertEquals(days.map(LocalDate::toString), caughtUp.map { it.bill.date })
        assertTrue(again.isEmpty())
        assertEquals(listOf(days.map { Recurrence.draftId(template.id, it) }), notices)
        assertEquals(LocalDate.of(2027, 4, 1), runBlocking { recurring.all.first() }.single().nextDraft())
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
    fun `a notice that cannot be shown stops nothing, and every recurring bill's drafts are made once`() {
        makeRecurring(Frequency.WEEKLY, "2026-11-17")
        makeRecurring(Frequency.MONTHLY, "2026-11-20")
        clock.moveTo(LocalDate.of(2026, 11, 24))
        val failing = RecurringDrafts(recurring, bills, notify = { _, _ -> throw SecurityException("No notices here") }, clock = clock)

        val made = runBlocking { failing.makeDue() }
        val again = runBlocking { failing.makeDue() }

        // Two Tuesdays of the weekly bill and the monthly bill's first draft.
        assertEquals(3, made.size)
        made.forEach { assertNotNull(runBlocking { bills.get(it.id) }) }
        assertTrue(again.isEmpty())
    }

    @Test
    fun `a recurring bill paused or deleted while a run is under way makes no more drafts in that run`() {
        // Made on three days in a row, so a run reads them in this order.
        val first = makeRecurringOn(LocalDate.of(2026, 11, 14))
        val paused = makeRecurringOn(LocalDate.of(2026, 11, 15))
        val deleted = makeRecurringOn(LocalDate.of(2026, 11, 16))
        clock.moveTo(LocalDate.of(2026, 11, 17))
        // The user pauses one and deletes another as the run tells of the first one's draft.
        val run = RecurringDrafts(recurring, bills, clock = clock, notify = { _, _ ->
            runBlocking {
                recurring.pause(paused.id)
                recurring.delete(deleted.id)
            }
        })

        val made = runBlocking { run.makeDue() }

        assertEquals(listOf(Recurrence.draftId(first.id, clock.today)), made.map { it.id })
        assertNull(runBlocking { bills.get(Recurrence.draftId(paused.id, clock.today)) })
        assertNull(runBlocking { bills.get(Recurrence.draftId(deleted.id, clock.today)) })
    }

    @Test
    fun `a draft deleted while a run was under way is not made again by that run`() {
        makeRecurringOn(LocalDate.of(2026, 11, 15))
        val later = makeRecurringOn(LocalDate.of(2026, 11, 16))
        clock.moveTo(LocalDate.of(2026, 11, 17))
        val draft = Recurrence.draftId(later.id, clock.today)
        var interrupted = false
        // As the run tells of the first recurring bill's draft, a second run makes the other one's,
        // and the user deletes it at once. The first run read that recurring bill before all this.
        val run = RecurringDrafts(recurring, bills, clock = clock, notify = { _, _ ->
            if (!interrupted) {
                interrupted = true
                runBlocking {
                    assertEquals(listOf(draft), drafts.makeDue().map { it.id })
                    bills.delete(draft)
                }
            }
        })

        runBlocking { run.makeDue() }

        assertTrue(interrupted)
        assertNull(runBlocking { bills.get(draft) })
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
        assertEquals(LocalDate.of(2026, 12, 5), template.nextDraft())
        assertEquals(listOf(LocalDate.of(2026, 12, 5)), runDaily(LocalDate.of(2026, 12, 5)).keys.toList())
    }
}
