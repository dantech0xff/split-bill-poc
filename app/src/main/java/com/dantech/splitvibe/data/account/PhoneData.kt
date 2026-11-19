package com.dantech.splitvibe.data.account

import androidx.room.withTransaction
import com.dantech.splitvibe.data.bills.BillPages
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.export.Exporter
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.share.ShareCardRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the person keeps only on this phone, which they may delete together with their account: the
 * bills with their drafts and the photos of their pages, the recurring bills, the people they split
 * with, their ways to get paid, the groups and requests kept from the proxy, and the name they go by.
 * The copies of these shared to another app, which wait in the cache, go too. Settings that are not
 * about them, such as the currency and the consent to the speech service, stay.
 */
class PhoneData(
    private val database: SplitVibeDatabase,
    private val pages: BillPages,
    private val settings: SettingsStore,
    /** The app's cache, where photos from the camera, shared cards and exports wait. */
    private val cacheDir: File,
) {

    /** Deletes it all; the rows go in one transaction, so a failure leaves every table as it was. */
    suspend fun delete() {
        database.withTransaction {
            database.bills().deleteAll()
            database.recurringBills().deleteAll()
            database.people().deleteAll()
            database.paymentMethods().deleteAll()
            database.proxyCache().clear()
        }
        pages.deleteAll()
        withContext(Dispatchers.IO) {
            listOf(BillImageLoader.CAMERA_DIRECTORY, ShareCardRenderer.FOLDER, Exporter.FOLDER).forEach { folder ->
                File(cacheDir, folder).deleteRecursively()
            }
        }
        settings.forgetName()
    }
}

/** The account was deleted, but what the phone keeps could not all be deleted with it. */
class PhoneDataException(cause: Throwable) : Exception("The data on this phone could not all be deleted", cause)
