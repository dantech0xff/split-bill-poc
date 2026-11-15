package com.dantech.splitvibe

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.AppLink
import com.dantech.splitvibe.ui.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
