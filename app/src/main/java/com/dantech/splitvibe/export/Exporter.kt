package com.dantech.splitvibe.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.domain.Insights
import java.io.File
import java.io.IOException
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What an export holds: three CSV files for a spreadsheet, or one PDF summary to read or print. */
enum class ExportFormat { CSV, PDF }

/** The finished bills of one period and what they add up to in the local currency, as the insights screen shows them. */
class ExportContent(
    /** What the period is called, such as "November 2026" or "All time". */
    val period: String,
    /** A short form of [period] for file names, such as "2026-11". */
    val periodKey: String,
    /** The finished bills of the period, in the order the files list them. */
    val bills: List<SavedBill>,
    /** What [bills] add up to in the local currency. */
    val insights: Insights,
    /** What the totals leave out and why, as sentences. */
    val notes: List<String>,
)

/**
 * Writes the finished bills of a period as files: three CSV files ([CsvExport]) or a PDF summary
 * ([PdfSummary]). The files only ever go where the user chooses: to the app they pick in the share
 * sheet, or to the place they pick in the system's file picker. [locale] decides how numbers are
 * written, so a spreadsheet app in the same locale reads them right.
 */
class Exporter(private val context: Context, private val locale: () -> Locale = Locale::getDefault) {

    /** The files of [format] for [content]. */
    suspend fun files(content: ExportContent, format: ExportFormat): List<ExportFile> = withContext(Dispatchers.Default) {
        when (format) {
            ExportFormat.CSV -> CsvExport.files(content.bills, CsvConvention.of(locale()))
            ExportFormat.PDF -> listOf(ExportFile(pdfName(content), PDF_TYPE, PdfSummary(content, locale()).bytes()))
        }
    }

    /**
     * The share sheet for [files], kept in the app's cache for this one share: only the app the user
     * picks may read them. Files of shares a day old or more are deleted.
     *
     * @throws IOException when the files cannot be written, such as on a full disk.
     */
    suspend fun shareIntent(files: List<ExportFile>, title: String): Intent = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "Nothing to share" }
        val root = File(context.cacheDir, FOLDER)
        val dayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        root.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.deleteRecursively() }
        // A folder of its own for every share, so an app that reads late never gets a later share's files.
        val folder = File(root, UUID.randomUUID().toString())
        if (!folder.mkdirs()) throw IOException("The files could not be kept for sharing")
        val uris = files.map { file ->
            val kept = File(folder, file.name).apply { writeBytes(file.bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", kept)
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
        // The share sheet's preview and the app picked both read the files through this.
        send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, title)
    }

    /**
     * Writes [files] into [folder], a folder the user picked in the system's file picker. A file of
     * the same name already there is kept, and the system names the new one apart.
     *
     * @throws IOException when a file cannot be made or written there.
     */
    suspend fun saveToFolder(folder: Uri, files: List<ExportFile>) = withContext(Dispatchers.IO) {
        elsewhere {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            for (file in files) {
                val document = DocumentsContract.createDocument(resolver, parent, file.mimeType, file.name)
                    ?: throw IOException("${file.name} could not be made")
                write(document, file)
            }
        }
    }

    /**
     * Writes [file] to [document], a file the user made in the system's file picker.
     *
     * @throws IOException when it cannot be written there.
     */
    suspend fun saveTo(document: Uri, file: ExportFile) = withContext(Dispatchers.IO) { elsewhere { write(document, file) } }

    private fun write(document: Uri, file: ExportFile) {
        val out = context.contentResolver.openOutputStream(document, "wt") ?: throw IOException("${file.name} could not be written")
        out.use { it.write(file.bytes) }
    }

    /**
     * Runs [writing], which writes where another app keeps files for the user. What goes wrong over
     * there, such as that app being gone or refusing the file, comes back as a runtime exception of
     * that app's; it is an [IOException] here, as the files could not be saved in that place.
     */
    private inline fun <T> elsewhere(writing: () -> T): T = try {
        writing()
    } catch (e: RuntimeException) {
        throw IOException("The place picked could not be written to", e)
    }

    companion object {
        const val PDF_TYPE = "application/pdf"

        /** Where shared files are kept in the cache; see `res/xml/file_paths.xml`. */
        private const val FOLDER = "exports"

        /** The name of the PDF summary of [content], such as "spending-2026-11.pdf". */
        fun pdfName(content: ExportContent): String = "spending-${content.periodKey}.pdf"

        /** [month] as a heading, such as "November 2026", in English as the app is. */
        fun monthName(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ENGLISH))

        /** What a group's line of the totals is called: its name, or "No group" for bills in none. */
        fun groupName(group: String?): String = group ?: "No group"
    }
}
