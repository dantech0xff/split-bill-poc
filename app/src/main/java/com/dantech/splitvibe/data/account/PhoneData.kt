package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import com.dantech.splitvibe.data.bills.BillPages
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.settings.SettingsStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the person keeps only on this phone, which they may delete together with their account: the
 * bills with their drafts and the photos of their pages, the recurring bills, the people they split
 * with, their ways to get paid, the groups and requests kept from the proxy, and the name they go by.
 * The app's whole cache goes too, which holds the photos from the camera and the document scanner and
 * the copies shared to another app, and so do the app's notices, which can name a bill. Settings that
 * are not about them, such as the currency and the consent to the speech service, stay.
 */
class PhoneData(
    private val database: SplitVibeDatabase,
    private val pages: BillPages,
    private val settings: SettingsStore,
    context: Context,
) {
    private val cacheDir = context.cacheDir
    private val notices = NotificationManagerCompat.from(context)

    /**
     * Deletes it all. The rows go in one transaction, so a failure there leaves every table as it
     * was and deletes nothing else. Past it, a file that cannot be deleted stops nothing: the other
     * files, the notices and the name still go. Deleting again after a failure, or after the app was
     * ended part of the way through, deletes what is left.
     *
     * @throws IOException when a file that should go is still there.
     */
    suspend fun delete() {
        database.withTransaction {
            database.bills().deleteAll()
            database.recurringBills().deleteAll()
            database.people().deleteAll()
            database.paymentMethods().deleteAll()
            database.proxyCache().clear()
        }
        val failures = listOfNotNull(failureOf { pages.deleteAll() }, failureOf { clearCache() })
        notices.cancelAll()
        settings.forgetName()
        failures.firstOrNull()?.let { throw it }
    }

    private suspend fun clearCache() = withContext(Dispatchers.IO) {
        val left = cacheDir.listFiles().orEmpty().filter { !it.deleteRecursively() && it.exists() }
        if (left.isNotEmpty()) throw IOException("${left.size} files in the cache could not be deleted")
    }

    private suspend fun failureOf(deleting: suspend () -> Unit): IOException? = try {
        deleting()
        null
    } catch (e: IOException) {
        e
    }
}
