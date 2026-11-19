package com.dantech.splitvibe.ui.recurring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestClock
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.english
import java.time.LocalDate
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The recurring bills as their screen and the list of bills use them: making one from a bill,
 * listing it with the day of its next draft, pausing, resuming and deleting it; and what the sheet
 * that makes one suggests and says.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecurringViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val clock = TestClock(LocalDate.of(2026, 11, 16))
    private val bills = BillRepository(storage.database, storage.people, storage.pages, contentWords()) { clock.millis() }
    private val recurring = RecurringBillRepository(storage.database, clock, contentWords())

    /** How often the drafts were asked to be made at once. */
    private var runs = 0
    private val viewModel = RecurringViewModel(recurring, bills, runDrafts = { runs++ }, clock = clock)
    private val watching = MainScope()

    private val rent = runBlocking {
        bills.save(SpendingFixtures.finished("rent", "2026-11-01", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh", "Khoa")))
    }

    init {
        // The screen watches the list while it shows.
        watching.launch { viewModel.rows.collect {} }
    }

    @After
    fun tearDown() {
        watching.cancel()
        storage.close()
    }

    private fun rows(): List<RecurringRow> {
        awaitOnMain { viewModel.rows.value != null }
        return checkNotNull(viewModel.rows.value)
    }

    @Test
    fun `a bill made to recur is listed with the day of its next draft, and what is due is made at once`() {
        viewModel.repeat(rent.id, "Flat rent", RecurrenceSchedule(Frequency.MONTHLY, LocalDate.of(2026, 11, 1)), LocalDate.of(2026, 12, 1))
        awaitOnMain { viewModel.rows.value?.size == 1 }

        val row = rows().single()
        assertEquals("Flat rent", row.bill.name)
        assertEquals(rent.bill, row.bill.bill)
        assertEquals(LocalDate.of(2026, 12, 1), row.next)
        assertEquals("Next draft Dec 1, 2026", detailOf(row).english())
        assertEquals(1, runs)
    }

    @Test
    fun `a draft that is due but not made yet is listed as due, not as the next period's`() {
        val tuesdays = RecurrenceSchedule(Frequency.WEEKLY, LocalDate.of(2026, 11, 17))
        runBlocking { recurring.create(rent, "Cleaning", tuesdays, tuesdays.start) }
        // Thursday, and the system held the work back, so Tuesday's draft is not made yet.
        clock.moveTo(LocalDate.of(2026, 11, 19))
        val later = RecurringViewModel(recurring, bills, runDrafts = {}, clock = clock)
        watching.launch { later.rows.collect {} }
        awaitOnMain { later.rows.value != null }

        val row = checkNotNull(later.rows.value).single()
        assertEquals(LocalDate.of(2026, 11, 17), row.next)
        assertTrue(row.due)
        assertEquals("Draft due Nov 17, 2026", detailOf(row).english())
        assertEquals("Paused", detailOf(row.copy(bill = row.bill.copy(paused = true))).english())
    }

    @Test
    fun `pausing, resuming and deleting change the list, and resuming makes what is due at once`() {
        val tuesdays = RecurrenceSchedule(Frequency.WEEKLY, LocalDate.of(2026, 11, 17))
        viewModel.repeat(rent.id, "Rent", tuesdays, tuesdays.start)
        awaitOnMain { viewModel.rows.value?.size == 1 }
        val id = rows().single().bill.id

        viewModel.pause(id)
        awaitOnMain { rows().single().bill.paused }
        viewModel.resume(id)
        awaitOnMain { !rows().single().bill.paused }
        viewModel.delete(id)
        awaitOnMain { rows().isEmpty() }

        // Once when it was made and once when it was resumed.
        assertEquals(2, runs)
    }

    @Test
    fun `the sheet suggests the bill's own day, with the first draft on the next occurrence after it, and today at the earliest`() {
        val today = LocalDate.of(2026, 11, 16)
        fun suggested(date: String?, frequency: Frequency) =
            suggestedSchedule(date, frequency, today).let { it.start to firstDraftDay(it, today) }

        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 12, 1), suggested("2026-11-01", Frequency.MONTHLY))
        // A Tuesday: the next Tuesday from today on.
        assertEquals(LocalDate.of(2026, 11, 10) to LocalDate.of(2026, 11, 17), suggested("2026-11-10", Frequency.WEEKLY))
        assertEquals(LocalDate.of(2026, 10, 6) to LocalDate.of(2026, 11, 17), suggested("2026-10-06", Frequency.WEEKLY))
        // The bill is of today: its next occurrence is the first draft.
        assertEquals(today to LocalDate.of(2026, 12, 16), suggested("2026-11-16", Frequency.MONTHLY))
        // Without a date that can be read, from today.
        assertEquals(today to LocalDate.of(2026, 12, 16), suggested(null, Frequency.MONTHLY))
        assertEquals(today to LocalDate.of(2026, 11, 23), suggested("13/11/2026", Frequency.WEEKLY))
        // A rent dated the 31st keeps the 31st, and only its first draft is on the last day of a shorter month.
        assertEquals(LocalDate.of(2026, 10, 31) to LocalDate.of(2026, 11, 30), suggested("2026-10-31", Frequency.MONTHLY))
        assertEquals(
            "Every month on the 31st, or the last day of a shorter month",
            describe(suggestedSchedule("2026-10-31", Frequency.MONTHLY, today)).english(),
        )
    }

    @Test
    fun `a schedule is described in words, with the day of the month as an ordinal`() {
        fun monthly(day: Int) = describe(RecurrenceSchedule(Frequency.MONTHLY, LocalDate.of(2027, 1, day))).english()

        assertEquals("Every week on Tuesday", describe(RecurrenceSchedule(Frequency.WEEKLY, LocalDate.of(2026, 11, 17))).english())
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd").map { "Every month on the $it" },
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23).map(::monthly),
        )
        assertEquals("Every month on the 28th", monthly(28))
        assertEquals("Every month on the 31st, or the last day of a shorter month", monthly(31))
        assertEquals("Every month on the 29th, or the last day of a shorter month", monthly(29))
    }
}
