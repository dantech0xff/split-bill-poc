package com.dantech.splitvibe

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which photos shared from another app a split starts from. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SharedImagesTest {

    private val app = "com.dantech.splitvibe"
    private val photo = Uri.parse("content://media/external/images/media/42")

    @Test
    fun `one shared photo is taken`() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, photo)

        assertEquals(listOf(photo), sharedImagesOf(intent, app))
    }

    @Test
    fun `several shared photos are taken in order, as many as a bill has pages`() {
        val photos = (1..7).map { Uri.parse("content://media/external/images/media/$it") }
        val intent = Intent(
            Intent.ACTION_SEND_MULTIPLE,
        ).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photos))

        assertEquals(photos.take(5), sharedImagesOf(intent, app))
    }

    @Test
    fun `what is not a photo another app hands over is left alone`() {
        fun send(type: String, uri: Uri) = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)

        assertEquals(emptyList<Uri>(), sharedImagesOf(send("text/plain", photo), app))
        assertEquals(emptyList<Uri>(), sharedImagesOf(send("image/png", Uri.parse("file:///data/data/$app/files/secret.png")), app))
        assertEquals(emptyList<Uri>(), sharedImagesOf(send("image/jpeg", Uri.parse("content://$app.fileprovider/bill_photos/a.jpg")), app))
        assertEquals(emptyList<Uri>(), sharedImagesOf(Intent(Intent.ACTION_MAIN), app))
        assertEquals(emptyList<Uri>(), sharedImagesOf(null, app))
    }
}
