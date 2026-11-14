package com.dantech.splitvibe.llm

import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ItemAssignmentHint
import com.dantech.splitvibe.domain.ShareRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Calls the real Anthropic API with the ML Kit output recorded for the bundled sample bill.
 *
 * Skipped unless an API key is provided, because it needs the network and spends a few cents:
 *
 *     ANTHROPIC_API_KEY=sk-ant-... ./gradlew :app:testDebugUnitTest --tests '*LiveClaudeBillAnalyzerTest*' --rerun
 */
class LiveClaudeBillAnalyzerTest {

    @Test
    fun `real API reads the sample bill and understands the instruction`() = runBlocking {
        val apiKey = System.getenv("ANTHROPIC_API_KEY").orEmpty()
        assumeTrue("Set ANTHROPIC_API_KEY to run this test against the real API", apiKey.isNotBlank())

        val analysis = ClaudeBillAnalyzer(apiKey).use { analyzer ->
            analyzer.analyze(Fixtures.ocr("sample_bill_ocr.json"), "Split with Minh and Lan. Minh had the beers.", localCurrency = "VND")
        }

        analysis as BillAnalysis.Ready
        val bill = analysis.bill
        assertEquals(BillCurrency("VND", 0), bill.currency)
        assertEquals(902_664L, bill.totalMinor)
        assertEquals(7, bill.items.size)
        assertEquals(796_000L, bill.itemsTotalMinor)
        assertEquals(106_664L, bill.chargesTotalMinor)

        val beers = bill.items.indexOfFirst { it.name.contains("Bia", ignoreCase = true) }
        assertTrue("the beers were not found among ${bill.items.map { it.name }}", beers >= 0)
        assertEquals(listOf("Minh", "Lan"), analysis.splitHint.people)
        assertEquals(listOf(ItemAssignmentHint(beers, ShareRule.ONLY, listOf("Minh"), includesUser = false)), analysis.splitHint.itemAssignments)
    }
}
