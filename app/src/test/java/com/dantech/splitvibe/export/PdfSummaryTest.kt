package com.dantech.splitvibe.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.R
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.english
import com.dantech.splitvibe.ui.insights.InsightsPeriod
import com.dantech.splitvibe.ui.insights.contentOf
import com.dantech.splitvibe.ui.insights.insightsOf
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val content = contentOf(
        insightsOf(SpendingFixtures.year, "VND", InsightsPeriod.ALL_TIME, SpendingFixtures.clock),
    ) { it.english() }
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val summary = PdfSummary(content, resources)

    @Test
    @Config(sdk = [34], qualifiers = "en-rXA")
    fun `its words are the app's own, in the app's language`() {
        val pseudo = PdfSummary(content, resources).text()

        assertTrue(pseudo, pseudo.startsWith(resources.getString(R.string.pdf_title)))
        assertTrue(pseudo, pseudo.contains(resources.getString(R.string.pdf_page, 1, 2)))
        for (english in listOf("Spending summary", "By person", "Their share", "Page 1 of")) {
            assertTrue("$english is the code's, not the resources'", english !in pseudo)
        }
    }

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
    fun `an amount is never cut short, and only a name too long for its column is, in what each page draws`() {
        // Rupiah, which this locale writes with its code: wider than the column an amount has at most.
        val villa = SpendingFixtures.finished(
            id = "villa",
            date = "2026-11-14",
            merchant = LONG_PLACE,
            category = BillCategory.LODGING,
            totalMinor = 123_456_789_012,
            people = listOf(LONG_NAME),
            currency = BillCurrency("IDR", 2),
            rate = ExchangeRate("IDR", "VND", BigDecimal("1.6")),
        )

        val drawn = drawn(listOf(villa))

        // IDR1,234,567,890.12 at 1.6 is ₫1,975,308,624, and each half of it ₫987,654,312.
        listOf("IDR1,234,567,890.12", "IDR617,283,945.06", "₫1,975,308,624", "₫987,654,312").forEach { amount ->
            assertTrue("$amount is not drawn whole", drawn.any { it.text == amount })
        }
        // The name, in the table of people, and the place, in the list of bills, are cut short to fit, and nothing else is.
        val cut = drawn.filter { it.text.endsWith("…") }.map { it.text.removeSuffix("…") }
        assertEquals(2, cut.size)
        assertTrue(cut.toString(), LONG_PLACE.startsWith(cut[1]) && cut[1].length > PLACE_SHOWN)
        assertTrue(cut.toString(), LONG_NAME.startsWith(cut[0]) && cut[0].length > NAME_SHOWN)
        assertApartAndOnThePage(drawn)
    }

    @Test
    fun `amounts too wide to leave a name its room are drawn smaller, still whole`() {
        // The largest amount a bill can have, in a currency the phone does not know, of a long code.
        val tokens = BillCurrency("GOLDTOKENSOFTHEREALM", 3)
        val most = BillCurrency.MAX_AMOUNT_MINOR
        val vault = SpendingFixtures.finished("vault", "2026-11-14", "Vault", BillCategory.OTHER, most, emptyList(), currency = tokens)

        val drawn = drawn(listOf(vault))

        // The bill's total and the user's share of it, which is all of it.
        assertEquals(2, drawn.count { it.text == tokens.format(most, Locale.US) })
        assertApartAndOnThePage(drawn)
    }

    /** What each page of the summary of [bills] draws as a line of text, as a PDF page gets it. */
    private fun drawn(bills: List<SavedBill>): List<Drawn> {
        val insights = insightsOf(bills, "VND", InsightsPeriod.ALL_TIME, SpendingFixtures.clock)
        val summary = PdfSummary(contentOf(insights) { it.english() }, resources)
        return summary.pages.indices.flatMap { index ->
            val page = Bitmap.createBitmap(PdfSummary.PAGE_WIDTH, PdfSummary.PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = TextCanvas(index, page)
            summary.draw(index, canvas)
            canvas.drawn
        }
    }

    /** Each text of a line is clear of the next one, and inside the page's margins. */
    private fun assertApartAndOnThePage(drawn: List<Drawn>) {
        drawn.groupBy { it.page to it.baseline }.values.forEach { line ->
            line.sortedBy { it.left }.zipWithNext().forEach { (one, next) ->
                assertTrue("${one.text} runs into ${next.text}", one.right <= next.left)
            }
        }
        drawn.forEach { assertTrue("${it.text} is off the page", it.left >= PdfSummary.MARGIN && it.right <= PAGE_RIGHT + ROUNDING) }
    }

    /** A text drawn on [page], from [left] to [right] along its [baseline]. */
    private class Drawn(val page: Int, val text: String, val left: Float, val right: Float, val baseline: Float)

    /** A canvas that notes each line of text drawn on it, as the table's cells and the footer are drawn. */
    private class TextCanvas(private val page: Int, bitmap: Bitmap) : Canvas(bitmap) {
        val drawn = mutableListOf<Drawn>()

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            drawn += Drawn(page, text, x, x + paint.measureText(text), y)
            super.drawText(text, x, y, paint)
        }
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

        const val LONG_PLACE = "Biệt thự Hồ Tuyền Lâm, Khu du lịch quốc gia hồ Tuyền Lâm, Đà Lạt, Lâm Đồng, Việt Nam"
        const val LONG_NAME = "Nguyễn Thị Phương Thảo Trang Linh Anh Hoàng Minh Châu Bảo Ngọc Kim Ngân Diệu Hiền"

        /** How much of each, at the least, its column shows before it is cut. */
        const val PLACE_SHOWN = 10
        const val NAME_SHOWN = 20

        const val PAGE_RIGHT = PdfSummary.PAGE_WIDTH - PdfSummary.MARGIN

        /** What measuring a text again may add to its width, as floats are. */
        const val ROUNDING = 0.01f
    }
}
