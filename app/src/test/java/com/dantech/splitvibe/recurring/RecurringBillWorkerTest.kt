package com.dantech.splitvibe.recurring

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.TestClock
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.notifications.Notices
import com.dantech.splitvibe.ui.AppLink
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The work that makes the drafts of recurring bills, run by WorkManager's test runner with a clock
 * the test moves: what it makes and the notices it posts, and how it is scheduled.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecurringBillWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val clock = TestClock(LocalDate.of(2026, 11, 16))
    private val bills = BillRepository(storage.database, storage.people, storage.pages) { clock.millis() }
    private val recurring = RecurringBillRepository(storage.database, clock)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build(),
        )
        // The worker takes its objects from the app's Koin: these take the place of the phone's.
        val notify = { template: RecurringBill, made: List<SavedBill> -> Notices.showDrafts(context, template.name, made) }
        val drafts = RecurringDrafts(recurring, bills, notify, clock = clock)
        loadKoinModules(module { single { drafts } })
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    // The test's work database, which is in memory, stays open: WorkManager still notes a finished
    // run on its own thread after the run's state says so, and a closed database would fail that.
    @After
    fun tearDown() = storage.close()

    private fun weeklyRent() = runBlocking {
        val rent = bills.save(SpendingFixtures.finished("rent", "2026-11-03", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh", "Khoa")))
        // Every Tuesday from the bill's own, with the first draft on the next one, as the sheet suggests.
        recurring.create(rent, "Rent", RecurrenceSchedule(Frequency.WEEKLY, LocalDate.of(2026, 11, 3)), LocalDate.of(2026, 11, 17))
    }

    @Test
    fun `the drafts a run makes come in one notice that names the recurring bill off the lock screen and opens the newest`() {
        val template = weeklyRent()
        clock.moveTo(LocalDate.of(2026, 11, 17))
        val first = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }
        // Two weeks away: one run catches up both Tuesdays, and the next finds nothing new.
        clock.moveTo(LocalDate.of(2026, 12, 1))
        val second = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }
        val third = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }

        listOf(first, second, third).forEach { assertEquals(ListenableWorker.Result.success(), it) }
        val ids = listOf("2026-11-17", "2026-11-24", "2026-12-01").map { Recurrence.draftId(template.id, LocalDate.parse(it)) }
        ids.forEach { assertNotNull(runBlocking { bills.get(it) }) }
        // One notice for each run that made drafts, tagged with the newest of them.
        assertEquals(2, shadowOf(notifications).allNotifications.size)
        assertEquals("Recurring bills", notifications.getNotificationChannel(Notices.DRAFTS_CHANNEL).name)
        val single = notice(ids[0])
        val caughtUp = notice(ids[2])
        assertEquals("Rent" to "Its draft for Nov 17, 2026 is ready to check and finish.", words(single))
        assertEquals("Rent" to "2 drafts are ready to check and finish, the newest for Dec 1, 2026.", words(caughtUp))
        listOf(single to ids[0], caughtUp to ids[2]).forEach { (notice, id) ->
            assertEquals(Notices.DRAFTS_CHANNEL, notice.channelId)
            // Each notice keeps its own tap, which opens its newest draft.
            assertEquals(AppLink.OpenBill(id), AppLink.of(shadowOf(notice.contentIntent).savedIntent, PROXY_URL))
            // A lock screen that hides private notices says that a recurring bill is due, not which.
            assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility)
            assertEquals("A recurring bill is due" to null, words(checkNotNull(notice.publicVersion)))
        }
    }

    private fun notice(id: String): Notification = checkNotNull(shadowOf(notifications).getNotification(id, 1)) { "No notice for $id" }

    /** The title and the text of [notice]. */
    private fun words(notice: Notification): Pair<String?, String?> {
        val extras = notice.extras
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() to extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    @Test
    fun `without leave to show notices the drafts are still made`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val template = weeklyRent()
        clock.moveTo(LocalDate.of(2026, 11, 17))

        runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }

        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertEquals(clock.today.toString(), runBlocking { bills.get(Recurrence.draftId(template.id, clock.today)) }?.bill?.date)
    }

    private val work get() = WorkManager.getInstance(context)

    private fun daily(): List<WorkInfo> = work.getWorkInfosForUniqueWork(RecurringBillWorker.DAILY).get()

    /** Schedules the work as making a recurring bill does, and waits for the run it starts now. */
    private fun scheduleAndRun() {
        RecurringBillWorker.schedule(context)
        awaitOnMain {
            work.getWorkInfosForUniqueWork(RecurringBillWorker.NOW).get().all { it.state == WorkInfo.State.SUCCEEDED } &&
                daily().singleOrNull { !it.state.isFinished }?.state == WorkInfo.State.ENQUEUED
        }
    }

    @Test
    fun `scheduling runs the work now and every day from tomorrow, and scheduling again keeps the daily run`() {
        val template = weeklyRent()
        clock.moveTo(LocalDate.of(2026, 11, 17))

        // Twice, as when two recurring bills are made one after the other.
        scheduleAndRun()
        scheduleAndRun()

        val daily = daily().single()
        assertEquals(TimeUnit.DAYS.toMillis(1), daily.periodicityInfo?.repeatIntervalMillis)
        // Its first run is a day away, as the run now makes what is due today.
        assertEquals(TimeUnit.DAYS.toMillis(1), daily.initialDelayMillis)
        val made = runBlocking { bills.listings().first() }.map { it.id } - "rent"
        assertEquals(listOf(Recurrence.draftId(template.id, clock.today)), made)

        // A week later, the daily run makes the next draft.
        clock.moveTo(LocalDate.of(2026, 11, 24))
        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setInitialDelayMet(daily.id)
        awaitOnMain {
            runBlocking { bills.get(Recurrence.draftId(template.id, clock.today)) } != null &&
                daily().single().state == WorkInfo.State.ENQUEUED
        }

        // Keeping the daily run, as the app does whenever it comes to the foreground, keeps the one there is.
        RecurringBillWorker.keepDaily(context)
        assertEquals(daily.id, daily().single().id)
    }

    @Test
    fun `the daily run stops once no recurring bill is active, and comes back with the next one`() {
        val template = weeklyRent()
        scheduleAndRun()

        runBlocking { recurring.pause(template.id) }
        val run = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }

        assertEquals(ListenableWorker.Result.success(), run)
        assertEquals(listOf(WorkInfo.State.CANCELLED), daily().map { it.state })

        runBlocking { recurring.resume(template.id) }
        scheduleAndRun()
        assertEquals(WorkInfo.State.ENQUEUED, daily().single { !it.state.isFinished }.state)
    }

    @Test
    fun `a run before the app has set up its objects is tried again later`() {
        val template = weeklyRent()
        clock.moveTo(LocalDate.of(2026, 11, 17))

        // Before the app has started Koin, and while it has started it without its modules yet.
        stopKoin()
        val notStarted = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }
        startKoin { }
        val notReady = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }
        stopKoin()

        assertEquals(ListenableWorker.Result.retry(), notStarted)
        assertEquals(ListenableWorker.Result.retry(), notReady)
        assertNull(runBlocking { bills.get(Recurrence.draftId(template.id, clock.today)) })
    }

    private companion object {
        const val PROXY_URL = "https://split-vibe-proxy.example.workers.dev"
    }
}
