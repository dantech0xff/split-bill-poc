package com.dantech.splitvibe.scan

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.ActivityResult
import com.dantech.splitvibe.ocr.await
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.CancellationException

/**
 * The document scanner of Google Play services: it finds the edges of the bill, straightens and
 * crops it, and takes several pages, without the app holding a camera permission. A phone without
 * Google Play services cannot scan; the app then offers its own camera and photo picker instead.
 */
class DocumentScanner {

    private val client by lazy {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(MAX_PAGES)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build(),
        )
    }

    /** What to launch to scan a bill over [activity], or null when this phone cannot scan. */
    suspend fun startIntent(activity: Activity): IntentSender? = try {
        client.getStartScanIntent(activity).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Long bills fit in a few pages; more would hold more text than one reading takes. */
        const val MAX_PAGES = 5

        /** The pages a finished scan handed back, in order; none when the person backed out. */
        fun pagesOf(result: ActivityResult): List<Uri> {
            if (result.resultCode != Activity.RESULT_OK) return emptyList()
            return GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
        }
    }
}
