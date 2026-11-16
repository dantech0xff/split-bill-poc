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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Makes the drafts of the recurring bills that are due ([RecurringDrafts]), also while the app is
 * closed: once a day while there is a recurring bill, and once more right away whenever one is made
 * or started again. The activity also makes what is due whenever the app is opened, so occurrences the
 * daily work missed, such as while the phone was off, are made as soon as the user is back. It
 * takes its objects from Koin, so WorkManager's own factory can create it.
 */
class RecurringBillWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val drafts: RecurringDrafts by inject()

    override suspend fun doWork(): Result = try {
        drafts.makeDue()
        Result.success()
    } catch (e: SQLException) {
        // The bills could not be read or written this time, such as on a full disk: later, then.
        Result.retry()
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

        /** Runs every day, unless it does already. */
        fun keepDaily(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecurringBillWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
