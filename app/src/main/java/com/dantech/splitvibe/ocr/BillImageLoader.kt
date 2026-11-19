package com.dantech.splitvibe.ocr

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.dantech.splitvibe.R
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads bill photos as upright bitmaps sized for text recognition. */
class BillImageLoader(private val context: Context) {

    /**
     * Decodes the photo at [uri], scaled down so its long side is at most [MAX_SIDE] pixels (plenty
     * for receipt text, and safe for memory) and rotated according to its EXIF orientation.
     */
    suspend fun load(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a readable image: $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight))
        }
        val decoded = resolver.open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Could not decode image: $uri")
        val rotation = resolver.open(uri).use { ExifInterface(it).rotationDegrees }
        upright(decoded, rotation)
    }

    /** The bundled demo receipt, for trying the flow without a real bill. */
    suspend fun loadSample(): Bitmap = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(context.resources, R.drawable.sample_bill, options)
            ?: throw IOException("Sample bill is missing from the app resources")
    }

    /**
     * Deletes the app's own copies behind [uris] once their photos are kept as the pages of a bill:
     * a photo the camera app wrote for it, or a page the document scanner left in the app's cache.
     * A photo of another app, such as one from the gallery, is never touched.
     */
    suspend fun discard(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val cache = context.cacheDir.canonicalFile
        val provider = "${context.packageName}.fileprovider"
        for (uri in uris) {
            val file = when {
                uri.scheme == ContentResolver.SCHEME_FILE -> uri.path?.let(::File)

                uri.scheme == ContentResolver.SCHEME_CONTENT && uri.userInfo == null && uri.host == provider ->
                    uri.lastPathSegment?.let { File(File(cache, CAMERA_DIRECTORY), it) }

                else -> null
            } ?: continue
            val canonical = file.canonicalFile
            if (canonical.path.startsWith(cache.path + File.separator)) canonical.delete()
        }
    }

    /** A fresh content URI the system camera app can write a photo to. */
    fun newCameraTarget(): Uri {
        val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() } // only the latest capture is ever needed
        val file = File(directory, "bill_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun upright(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun ContentResolver.open(uri: Uri): InputStream = openInputStream(uri) ?: throw IOException("Could not open image: $uri")

    companion object {
        internal const val MAX_SIDE = 2400

        /** The folder in the cache that the camera writes photos of bills to. */
        internal const val CAMERA_DIRECTORY = "bill_photos"

        /** Smallest power-of-two downscale that brings [longSide] to [MAX_SIDE] or below. */
        internal fun sampleSize(longSide: Int): Int {
            var sample = 1
            while (longSide / sample > MAX_SIDE) sample *= 2
            return sample
        }
    }
}
