package com.dantech.splitvibe

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dantech.splitvibe.billing.BillingRepository
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.recurring.RecurringBillWorker
import com.dantech.splitvibe.recurring.RecurringDrafts
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.AppLink
import com.dantech.splitvibe.ui.AppNavHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val recurringDrafts: RecurringDrafts by inject()
    private val billing: BillingRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Every time the app comes to the foreground: also back to a live activity, or to one the
        // system restored after it ended the app. A run that finds nothing due changes nothing.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { catchUpOnRecurringBills() } }
        // RevenueCat runs as the signed-in account from the moment the app opens, rather than once the
        // Pro screen does, so that a purchase its SDK has not sent yet goes to RevenueCat soon. The SDK
        // sends it as whoever is signed in then, not as the account that made it, which is why the
        // store first sends what this run left unsent before it stops or changes user (follow()).
        // Here rather than when the application starts, which every local test does too.
        lifecycleScope.launch { billing.follow() }
        // Photos shared from another app, and a link or a notice that opened the app, are taken
        // once, not again when the activity comes back.
        val shared = if (savedInstanceState == null) sharedImagesOf(intent, packageName) else emptyList()
        val link = if (savedInstanceState == null) AppLink.of(intent, BuildConfig.AI_PROXY_URL) else null
        setContent {
            SplitVibeTheme {
                AppNavHost(sharedImages = shared, link = link)
            }
        }
    }

    /**
     * Makes the drafts of recurring bills that came due while the app was away, rather than waiting
     * for the daily work, which the system may hold back; and keeps that work scheduled while there
     * is a recurring bill. It works off the main thread, and nothing it meets may keep the app from
     * opening: what fails now, the daily work makes later.
     */
    private suspend fun catchUpOnRecurringBills() {
        try {
            val active = withContext(Dispatchers.Default) {
                recurringDrafts.makeDue()
                recurringDrafts.anyActive()
            }
            if (active) RecurringBillWorker.keepDaily(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Such as a full disk, or a phone whose notice or work service failed this time.
        }
    }
}

/**
 * The photos another app shared, in order and at most as many as one bill has pages. Only photos
 * another app hands over are taken: never a file path, and never a file of this app's own.
 */
internal fun sharedImagesOf(intent: Intent?, packageName: String): List<Uri> {
    if (intent?.type?.startsWith("image/") != true) return emptyList()
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    // The host, not the authority: "0@app.fileprovider" names the app's own provider too.
    return uris
        .filter { it.scheme == ContentResolver.SCHEME_CONTENT && it.userInfo == null && it.host?.startsWith(packageName) == false }
        .take(DocumentScanner.MAX_PAGES)
}
