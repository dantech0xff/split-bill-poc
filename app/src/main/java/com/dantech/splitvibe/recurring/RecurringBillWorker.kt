package com.dantech.splitvibe.recurring

import android.content.Context
import android.database.SQLException
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.koin.core.context.GlobalContext

/**
 * Makes the drafts of the recurring bills that are due ([RecurringDrafts]), also while the app is
 * closed: once a day while a recurring bill is active, and once more right away whenever one is made
 * or started again. The activity also makes what is due whenever the app comes to the foreground, so
 * occurrences the daily work missed, such as while the phone was off, are made as soon as the user
 * is back. It takes its objects from Koin, so WorkManager's own factory can create it.
 */
class RecurringBillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WorkManager starts before the app does, and can run work before the app has set up its
        // objects, such as right after the app was stopped by force: later, then.
        val drafts = GlobalContext.getOrNull()?.getOrNull<RecurringDrafts>() ?: return Result.retry()
        return try {
            drafts.makeDue()
            // The daily run is only needed while a recurring bill is active.
            if (drafts.anyActive()) keepDaily(applicationContext) else stopDaily(applicationContext)
            Result.success()
        } catch (e: SQLException) {
            // The bills could not be read or written this time, such as on a full disk: later, then.
            Result.retry()
        }
    }

    companion object {
        internal const val DAILY = "recurring-bills"
        internal const val NOW = "recurring-bills-now"

        /** Runs every day from now on, and once now, after any run under way, as a recurring bill was just made or started again. */
        fun schedule(context: Context) {
            keepDaily(context)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<RecurringBillWorker>().build())
        }

        /**
         * Runs every day, unless it does already. The first daily run is a day from now: what is due
         * now, the run that comes with a new recurring bill, or the app in the foreground, makes.
         */
        fun keepDaily(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecurringBillWorker>(1, TimeUnit.DAYS).setInitialDelay(1, TimeUnit.DAYS).build(),
            )
        }

        /** Stops the daily run, as no recurring bill is active; making or starting one again schedules it again. */
        private fun stopDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY)
        }
    }
}
