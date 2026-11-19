package com.dantech.splitvibe.data.bills

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The photos of bill pages, kept in the app's own storage so a saved bill can show them again. They
 * are never uploaded, and they go when the bill is deleted or the app's data is cleared.
 */
class BillPages(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY)

    /** Stores [page] and returns the name to load it by. */
    suspend fun save(page: Bitmap): String = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        File(directory, name).outputStream().use { out ->
            if (!page.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) throw IOException("The photo could not be saved")
        }
        name
    }

    /** The page stored as [name], or null when it is gone. */
    suspend fun load(name: String): Bitmap? = withContext(Dispatchers.IO) {
        fileOf(name)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    suspend fun delete(names: List<String>) = withContext(Dispatchers.IO) {
        names.forEach { name -> fileOf(name)?.delete() }
    }

    /** Deletes every stored page, also those of a bill that was never saved. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
    }

    /** Only plain names inside the pages folder, so a stored name can never point anywhere else. */
    private fun fileOf(name: String): File? = name.takeIf { NAME.matches(it) }?.let { File(directory, it) }

    private companion object {
        const val DIRECTORY = "bill_pages"
        const val QUALITY = 85
        val NAME = Regex("[0-9a-f-]{36}\\.jpg")
    }
}
