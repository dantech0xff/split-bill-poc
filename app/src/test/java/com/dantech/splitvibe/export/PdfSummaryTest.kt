package com.dantech.splitvibe.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.ui.insights.InsightsPeriod
import com.dantech.splitvibe.ui.insights.contentOf
import com.dantech.splitvibe.ui.insights.insightsOf
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The PDF summary of all of the fixture's bills, as the export lays it out on A4 pages: what each
 * page says, compared with the reference text in `src/test/exports`, and how each page looks,
 * drawn on a canvas as the PDF file's pages are, compared with the reference images in
 * `src/test/screenshots`. Record both again with `-Pscreenshots=record` after an intended change.
 *
 * Robolectric has no PDF writer behind [android.graphics.pdf.PdfDocument], so the file itself is
 * checked on a device, by `PdfSummaryOnDeviceTest`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PdfSummaryTest {

    private val content = contentOf(insightsOf(SpendingFixtures.year, "VND", InsightsPeriod.ALL_TIME, LocalDate.of(2026, 11, 16)))
    private val summary = PdfSummary(content, Locale.US)

    @Test
    fun `the pages say what the reference text says`() {
        val reference = File("src/test/exports/summary.txt")
        val text = summary.text() + "\n"
        if (System.getProperty("roborazzi.test.record") == "true") {
            reference.writeText(text)
        } else {
            assertEquals(reference.readText(), text)
        }
    }

    @Test
    fun `a table that goes on over a page break starts the next page with its header again`() {
        assertEquals(2, summary.pages.size)
        val second = summary.text().substringAfter("\n\n").lines()
        assertEquals("Date | Place | Category | Total | Your share", second.first())
        assertEquals("Split Vibe · All time | Page 2 of 2", second.last())
    }

    @Test
    fun `each page looks like its reference image`() {
        summary.pages.indices.forEach { index ->
            val page = Bitmap.createBitmap(PdfSummary.PAGE_WIDTH * SCALE, PdfSummary.PAGE_HEIGHT * SCALE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(page)
            // A PDF page is white paper.
            canvas.drawColor(Color.WHITE)
            canvas.scale(SCALE.toFloat(), SCALE.toFloat())
            summary.draw(index, canvas)
            page.captureRoboImage(File("src/test/screenshots/46-pdf-summary-page-${index + 1}.png"))
        }
    }

    private companion object {
        /** Twice the points of the page, as a phone shows it, so the text is sharp enough to read. */
        const val SCALE = 2
    }
}
