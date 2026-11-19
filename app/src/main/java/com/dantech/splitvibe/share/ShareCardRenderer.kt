package com.dantech.splitvibe.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit

/** Holds what a part of the screen last drew, so it can be shared as an image of exactly what the user sees. */
class ShareCardState internal constructor(internal val layer: GraphicsLayer) {
    /** The part of the screen as it was last drawn. */
    suspend fun toBitmap(): Bitmap = layer.toImageBitmap().asAndroidBitmap()
}

@Composable
fun rememberShareCardState(): ShareCardState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ShareCardState(layer) }
}

/** Records what this element draws into [state] while still drawing it on screen. */
fun Modifier.shareCard(state: ShareCardState): Modifier = drawWithContent {
    state.layer.record { this@drawWithContent.drawContent() }
    drawLayer(state.layer)
}

/**
 * Turns cards drawn by the app's own components into images, and hands them and the text of a
 * request to Android's share sheet. The images live in the app's cache, which only the app it is
 * shared with may read, and only for that share.
 */
object ShareCardRenderer {

    /** The folder in the cache that shared cards are written to. */
    internal const val FOLDER = "shared"

    /** Saves [bitmap] as a PNG to share, in place of an earlier one called [name]. */
    fun save(context: Context, bitmap: Bitmap, name: String): Uri {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        // Images shared a day ago or more have done their job.
        val dayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        folder.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }
        val file = File(folder, name.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "card" } + ".png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** The share sheet for [text], with [image] when there is one, for the user to pick a chat or an app. */
    fun shareIntent(text: String, image: Uri? = null, title: String? = null): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            if (image == null) {
                type = "text/plain"
            } else {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, image)
                // The preview in the share sheet and the app picked both read the image through this.
                clipData = ClipData.newRawUri(null, image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(send, title)
    }
}
