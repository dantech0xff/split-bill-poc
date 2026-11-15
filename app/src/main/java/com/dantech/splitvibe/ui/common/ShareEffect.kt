package com.dantech.splitvibe.ui.common

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.dantech.splitvibe.share.QrCodes
import com.dantech.splitvibe.share.ShareCardRenderer
import com.dantech.splitvibe.ui.groups.ShareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hands [share] to Android's share sheet once it is set, with its payment code as a picture, then calls [onShared]. */
@Composable
fun ShareEffect(share: ShareText?, onShared: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(share) {
        val shown = share ?: return@LaunchedEffect
        val image = shown.paying?.qrPayload?.let { payload ->
            val bitmap = withContext(Dispatchers.Default) { QrCodes.bitmap(payload) }
            withContext(Dispatchers.IO) { ShareCardRenderer.save(context, bitmap, "pay-code") }
        }
        try {
            context.startActivity(ShareCardRenderer.shareIntent(shown.text, image))
        } catch (e: ActivityNotFoundException) {
            // No app takes text on this phone; there is nothing more to offer.
        }
        onShared()
    }
}
