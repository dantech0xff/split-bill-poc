package com.dantech.splitvibe

import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitPlanner
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalysisParser
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the app's own logic on data recorded from the real pipeline for the bundled sample bill:
 * `sample_bill_ocr.json` is what ML Kit returned on a device, and `sample_bill_answer.json` is what
 * Claude Opus answered for that text with the instruction "Split with Minh and Lan. Minh had the
 * beers."
 */
class RecordedSampleBillTest {

    private val contacts = SeededContactRepository().contacts()

    @Test
    fun `real OCR output is laid out as the rows of the receipt`() {
        val ocr = Fixtures.ocr("sample_bill_ocr.json")

        val rows = OcrLayoutEncoder.rows(ocr).map { row -> row.fragments.map { it.text } }

        // ML Kit returned the names, quantities and prices as separate blocks, and the receipt is tilted.
        assertEquals(22, rows.size)
        assertTrue(rows.toString(), listOf("Bia Saigon Special", "6", "22.000", "132.000") in rows)
        assertTrue(rows.toString(), listOf("Cơm chiên hải sản", "2", "75.000", "150.000") in rows)
        assertTrue(rows.toString(), listOf("VAT (8%)", "66.864") in rows)
        assertTrue(rows.toString(), listOf("TỔNG CỘNG", "902.664") in rows)
    }

    @Test
    fun `the AI proxy's request fixture holds the document the app writes for this bill`() {
        val request = Json.parseToJsonElement(Fixtures.text("sample_bill_request.json")).jsonObject

        // The proxy's tests send this request, so it must stay what the encoder writes.
        val document = request.getValue("ocrDocument").jsonPrimitive.content
        assertEquals(OcrLayoutEncoder.encode(Fixtures.ocr("sample_bill_ocr.json")), document)
    }

    @Test
    fun `recorded model answer becomes a bill that adds up`() {
        val analysis = BillAnalysisParser.parse(Fixtures.text("sample_bill_answer.json")) as BillAnalysis.Ready
        val bill = analysis.bill

        assertEquals("VND", bill.currency.code)
        assertEquals(7, bill.items.size)
        assertEquals(796_000L, bill.itemsTotalMinor)
        assertEquals(listOf(39_800L, 66_864L), bill.charges.map { it.amountMinor })
        assertEquals(902_664L, bill.totalMinor)
        assertTrue("the printed total must be reached without a correction line", bill.warnings.isEmpty())
    }

    @Test
    fun `instruction is turned into people, an item assignment and exact shares`() {
        val analysis = BillAnalysisParser.parse(Fixtures.text("sample_bill_answer.json")) as BillAnalysis.Ready

        val plan = SplitPlanner.fromHint(analysis.splitHint, analysis.bill, contacts)
        val split = SplitCalculator.split(analysis.bill, plan.participantIds, plan.assignments)

        // "Minh" and "Lan" resolve to Trần Quang Minh (c01) and Lê Thị Lan (c02); the beers are item 5.
        assertEquals(listOf(ME_ID, "c01", "c02"), plan.participantIds)
        assertEquals(mapOf(5 to setOf("c01")), plan.assignments)
        assertTrue(plan.notices.isEmpty())

        // Worked by hand: 664.000 of shared food is 221.334 + 221.333 + 221.333, Minh adds 132.000 of
        // beer, and the 106.664 of service charge and VAT follows each person's items.
        assertEquals(listOf(221_334L, 353_333L, 221_333L), split.shares.map { it.itemsMinor })
        assertEquals(listOf(29_659L, 47_347L, 29_658L), split.shares.map { it.chargesMinor })
        assertEquals(listOf(250_993L, 400_680L, 250_991L), split.shares.map { it.totalMinor })
        assertEquals(902_664L, split.totalMinor)
    }
}
