package com.dantech.splitvibe.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.InsightBill
import com.dantech.splitvibe.domain.InsightShare
import com.dantech.splitvibe.domain.Insights
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Payments
import com.dantech.splitvibe.domain.SplitPlan
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves on the Android runtime what the local tests cannot, as Robolectric has no PDF writer: the
 * summary's pages go into a real PDF file, one A4 page for each page laid out.
 */
@RunWith(AndroidJUnit4::class)
class PdfSummaryOnDeviceTest {

    private val vnd = BillCurrency("VND", 0)

    /** Sixty bills of dinner, more than one page holds. */
    private val bills = (1..60).map { number ->
        val pho = BillItem("i1", "Phở", BigDecimal.ONE, null, 90_000)
        val bill = Bill("Phở Thìn $number", "2026-11-%02d".format(1 + number % 28), vnd, listOf(pho), emptyList(), null)
            .copy(category = BillCategory.FOOD_AND_DRINK)
        val plan = SplitPlan().withParticipantAdded("Minh").withPayments(Payments.single(ME_ID, bill.totalMinor))
        SavedBill(id = "bill-$number", status = BillStatus.FINAL, createdAt = 0, updatedAt = 0, bill = bill, plan = plan)
            .withSharesWorkedOut()
    }

    private val content = ExportContent(
        period = "November 2026",
        periodKey = "2026-11",
        bills = bills,
        insights = Insights.of(
            bills.map { saved ->
                InsightBill(
                    day = LocalDate.parse(saved.bill.date),
                    category = saved.bill.category,
                    group = null,
                    currency = vnd,
                    totalMinor = saved.bill.totalMinor,
                    shares = saved.shares.map { InsightShare(it.personId, if (it.personId == ME_ID) "You" else "Minh", it.totalMinor) },
                    myShareMinor = saved.shares.first { it.personId == ME_ID }.totalMinor,
                    rate = null,
                )
            },
            vnd,
        ),
        notes = emptyList(),
    )

    @Test
    fun the_summary_is_a_pdf_file_with_an_a4_page_for_each_page_laid_out() {
        val summary = PdfSummary(content, Locale.US)
        val bytes = summary.bytes()
        val text = bytes.toString(Charsets.ISO_8859_1)

        assertTrue(summary.pages.size > 1)
        assertTrue(text.startsWith("%PDF-"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertEquals(summary.pages.size, Regex("""/Type\s*/Page\b""").findAll(text).count())
        assertEquals(summary.pages.size, Regex("""/MediaBox\s*\[0 0 595 842]""").findAll(text).count())
    }
}
