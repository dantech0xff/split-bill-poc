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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
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
        val drafts = RecurringDrafts(recurring, bills, notify = { Notices.showDraft(context, it) }, clock = clock)
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
    fun `the work makes the drafts that are due, each with one notice that opens it`() {
        val template = weeklyRent()
        clock.moveTo(LocalDate.of(2026, 11, 24))

        val first = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }
        val second = runBlocking { TestListenableWorkerBuilder<RecurringBillWorker>(context).build().doWork() }

        assertEquals(ListenableWorker.Result.success(), first)
        assertEquals(ListenableWorker.Result.success(), second)
        val ids = listOf(17, 24).map { Recurrence.draftId(template.id, LocalDate.of(2026, 11, it)) }
        val posted = shadowOf(notifications).allNotifications
        // One notice for each draft, and none again for a draft made before.
        assertEquals(2, posted.size)
        assertEquals("Recurring bills", notifications.getNotificationChannel(Notices.DRAFTS_CHANNEL).name)
        ids.forEach { id ->
            val notice = checkNotNull(shadowOf(notifications).getNotification(id, 1)) { "No notice for $id" }
            assertEquals(Notices.DRAFTS_CHANNEL, notice.channelId)
            val opened = shadowOf(notice.contentIntent).savedIntent
            assertEquals(AppLink.OpenBill(id), AppLink.of(opened, "https://split-vibe-proxy.example.workers.dev"))
        }
        assertEquals(
            "Its draft for Nov 17, 2026 is ready to check and finish.",
            shadowOf(notifications).getNotification(ids[0], 1).extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
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

    @Test
    fun `scheduling runs the work now and then every day, and scheduling again keeps the daily run`() {
        val template = weeklyRent()
        clock.moveTo(LocalDate.of(2026, 11, 17))
        val work = WorkManager.getInstance(context)

        // Twice, as when two recurring bills are made one after the other.
        RecurringBillWorker.schedule(context)
        RecurringBillWorker.schedule(context)
        fun daily() = work.getWorkInfosForUniqueWork(RecurringBillWorker.DAILY).get().single()
        awaitOnMain {
            work.getWorkInfosForUniqueWork(RecurringBillWorker.NOW).get().all { it.state == WorkInfo.State.SUCCEEDED } &&
                daily().state == WorkInfo.State.ENQUEUED
        }

        val daily = daily()
        assertEquals(TimeUnit.DAYS.toMillis(1), daily.periodicityInfo?.repeatIntervalMillis)
        val made = runBlocking { bills.listings().first() }.map { it.id } - "rent"
        assertEquals(listOf(Recurrence.draftId(template.id, clock.today)), made)

        // A week later, the daily run makes the next draft.
        clock.moveTo(LocalDate.of(2026, 11, 24))
        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setPeriodDelayMet(daily.id)
        awaitOnMain {
            runBlocking { bills.get(Recurrence.draftId(template.id, clock.today)) } != null && daily().state == WorkInfo.State.ENQUEUED
        }

        // Keeping the daily run, as every start of the app does, keeps the one there is.
        RecurringBillWorker.keepDaily(context)
        assertEquals(daily.id, daily().id)
    }
}
