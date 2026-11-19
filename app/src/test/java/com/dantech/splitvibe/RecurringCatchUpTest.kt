package com.dantech.splitvibe

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.recurring.RecurringDrafts
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.annotation.Config

/**
 * The activity makes the drafts of recurring bills that came due every time the app comes to the
 * foreground, as the system may hold the daily work back: on a fresh start, back to an activity that
 * was only in the background, and in one the system recreated from its saved state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class RecurringCatchUpTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val clock = TestClock(LocalDate.of(2026, 11, 16))
    private val bills = BillRepository(storage.database, storage.people, storage.pages, contentWords()) { clock.millis() }
    private val recurring = RecurringBillRepository(storage.database, clock, contentWords())

    /** Whether each notice was posted from the main thread, in order. */
    private val onMainThread: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setUp() {
        val work = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, work)
        val notify = { _: RecurringBill, _: List<SavedBill> -> onMainThread += Looper.myLooper() == Looper.getMainLooper() }
        useDrafts(RecurringDrafts(recurring, bills, notify, clock))
    }

    @After
    fun tearDown() = storage.close()

    /** The activity takes its objects from the app's Koin: [drafts] takes the place of the phone's. */
    private fun useDrafts(drafts: RecurringDrafts) = loadKoinModules(module { single { drafts } })

    /** The rent, made to recur every Monday from today, so each week the app comes back has a draft due. */
    private fun mondays(): RecurringBill = runBlocking {
        val rent = bills.save(SpendingFixtures.finished("rent", "2026-11-16", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh")))
        recurring.create(rent, "Rent", RecurrenceSchedule(Frequency.WEEKLY, LocalDate.of(2026, 11, 16)), LocalDate.of(2026, 11, 16))
    }

    private fun RecurringBill.madeOn(day: LocalDate): Boolean = runBlocking { bills.get(Recurrence.draftId(id, day)) } != null

    @Test
    fun `what came due is made on a fresh start, back in a live activity and in a recreated one, off the main thread`() {
        val rent = mondays()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitOnMain { rent.madeOn(LocalDate.of(2026, 11, 16)) }

            // A week later the user comes back to the activity, which was only in the background.
            clock.moveTo(LocalDate.of(2026, 11, 23))
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitOnMain { rent.madeOn(LocalDate.of(2026, 11, 23)) }

            // Another week later, the system recreates the activity from the state it saved.
            clock.moveTo(LocalDate.of(2026, 11, 30))
            scenario.recreate()
            awaitOnMain { rent.madeOn(LocalDate.of(2026, 11, 30)) }
        }

        // One notice for each, posted with the drafts off the main thread.
        assertEquals(listOf(false, false, false), onMainThread)
    }

    @Test
    fun `a catch-up that fails keeps the app from nothing`() {
        // Bills that can no longer be read at all, as with a database closed under the app. Each
        // catch-up reads the clock as it starts, so its reads say how many have run.
        val broken = TestStorage(context)
        val read = AtomicInteger()
        val counting = object : Clock() {
            override fun getZone(): ZoneId = clock.zone

            override fun withZone(zone: ZoneId): Clock = clock.withZone(zone)

            override fun instant(): Instant = clock.instant().also { read.incrementAndGet() }
        }
        val brokenRecurring = RecurringBillRepository(broken.database, counting, contentWords())
        useDrafts(RecurringDrafts(brokenRecurring, broken.bills, notify = { _, _ -> }, counting))
        broken.close()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitOnMain { read.get() >= 1 }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitOnMain { read.get() >= 2 }
            // What each failure brings back to the main thread is run there, and the app is still open.
            val settled = System.currentTimeMillis() + SETTLE_MS
            awaitOnMain { System.currentTimeMillis() > settled }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    private companion object {
        /** Long enough for a catch-up that failed off the main thread to have come back to it. */
        const val SETTLE_MS = 300L
    }
}
