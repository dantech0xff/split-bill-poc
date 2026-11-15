package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.ItemAssignmentHint
import com.dantech.splitvibe.domain.ShareRule
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitHint
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BillAnalysisParserTest {

    private fun ready(json: String): BillAnalysis.Ready = BillAnalysisParser.parse(json) as BillAnalysis.Ready

    @Test
    fun `vietnamese bill becomes whole dong with tax as a charge`() {
        val analysis = ready(
            """
            {
              "status": "ok", "merchant": "Quán Ốc Đào", "date": "2026-11-13", "currency": "VND",
              "items": [
                {"name": "Ốc hương rang muối", "quantity": 1, "unit_price": 120000, "amount": 120000},
                {"name": "Bia Saigon", "quantity": 4, "unit_price": 22000, "amount": 88000}
              ],
              "charges": [{"label": "VAT 8%", "kind": "tax", "amount": 16640}],
              "total": 224640,
              "split": {"people": ["Minh"], "user_included": true, "people_count": null,
                        "item_assignments": [{"item_index": 1, "rule": "only", "people": ["Minh"], "includes_user": false}]},
              "warnings": [], "message": "Đã đọc hóa đơn."
            }
            """.trimIndent(),
        )

        val bill = analysis.bill
        assertEquals("Quán Ốc Đào", bill.merchant)
        assertEquals(BillCurrency("VND", 0), bill.currency)
        assertEquals(listOf(120_000L, 88_000L), bill.items.map { it.amountMinor })
        assertEquals(BigDecimal(4), bill.items[1].quantity)
        assertEquals(22_000L, bill.items[1].unitPriceMinor)
        assertEquals(ChargeKind.TAX, bill.charges.single().kind)
        assertEquals(224_640L, bill.totalMinor)
        assertTrue(bill.warnings.isEmpty())
        assertEquals(
            SplitHint(people = listOf("Minh"), itemAssignments = listOf(ItemAssignmentHint("i2", ShareRule.ONLY, listOf("Minh"), false))),
            analysis.splitHint,
        )
        assertEquals("Đã đọc hóa đơn.", analysis.message)
    }

    @Test
    fun `decimal prices keep their exact cents`() {
        val analysis = ready(
            """
            {"status": "ok", "merchant": "", "date": "", "currency": "USD",
             "items": [{"name": "Latte", "quantity": 3, "unit_price": 4.10, "amount": 12.30},
                       {"name": "Bagel", "quantity": 1, "unit_price": null, "amount": 0.1}],
             "charges": [{"label": "Sales tax", "kind": "tax", "amount": 1.02},
                         {"label": "Tip", "kind": "Tip", "amount": 2.5}],
             "total": 15.92,
             "split": {"people": [], "user_included": true, "people_count": 3, "item_assignments": []},
             "warnings": [], "message": "ok"}
            """.trimIndent(),
        )

        val bill = analysis.bill
        assertNull(bill.merchant)
        assertNull(bill.date)
        assertEquals(listOf(1_230L, 10L), bill.items.map { it.amountMinor })
        assertNull(bill.items[1].unitPriceMinor)
        assertEquals(listOf(ChargeKind.TAX, ChargeKind.TIP), bill.charges.map { it.kind })
        assertEquals(1_592L, bill.totalMinor)
        assertEquals(bill.totalMinor, bill.itemsTotalMinor + bill.chargesTotalMinor)
        assertEquals(3, analysis.splitHint.peopleCount)
    }

    @Test
    fun `discount written as a positive number still reduces the bill`() {
        val bill = ready(
            """
            {"status": "ok", "currency": "USD",
             "items": [{"name": "Pizza", "quantity": 1, "unit_price": 20, "amount": 20}],
             "charges": [{"label": "", "kind": "discount", "amount": 5}, {"label": "Zero", "kind": "other", "amount": 0}],
             "total": 15,
             "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).bill

        assertEquals(-500L, bill.charges.single().amountMinor)
        assertEquals("Discount", bill.charges.single().label)
        assertTrue(bill.warnings.isEmpty())
    }

    @Test
    fun `numbers that do not add up are kept visible`() {
        val bill = ready(
            """
            {"status": "ok", "currency": "USD",
             "items": [{"name": "Pasta", "quantity": 1, "unit_price": 14, "amount": 14}],
             "charges": [], "total": 16.5,
             "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
             "warnings": ["The second item was unreadable."], "message": ""}
            """.trimIndent(),
        ).bill

        assertTrue(bill.charges.isEmpty())
        assertEquals(1_650L, bill.totalMinor)
        assertEquals(250L, bill.differenceMinor)
        assertTrue(bill.differenceNote()!!.contains("Please check the items"))
        assertEquals(listOf("The second item was unreadable."), bill.warnings)
    }

    @Test
    fun `unreadable total falls back to the sum of the items`() {
        val bill = ready(
            """
            {"status": "ok", "currency": "VND",
             "items": [{"name": "Pho", "quantity": 2, "unit_price": 65000, "amount": 130000}],
             "charges": [], "total": 0,
             "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).bill

        assertEquals(130_000L, bill.totalMinor)
        assertNull("the lines make the total", bill.printedTotalMinor)
        assertTrue(bill.warnings.single().contains("total could not be read"))
    }

    @Test
    fun `split details that make no sense are dropped`() {
        val hint = ready(
            """
            {"status": "ok", "currency": "USD",
             "items": [{"name": "Soup", "quantity": 1, "unit_price": 5, "amount": 5}],
             "charges": [], "total": 5,
             "split": {"people": [" Minh ", "", "Minh"], "user_included": true, "people_count": 0,
                       "item_assignments": [{"item_index": 3, "rule": "only", "people": ["Minh"], "includes_user": false},
                                            {"item_index": 0, "rule": "only", "people": [], "includes_user": false},
                                            {"item_index": 0, "rule": "only", "people": [" Lan"], "includes_user": true}]},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).splitHint

        assertEquals(
            SplitHint(people = listOf("Minh"), itemAssignments = listOf(ItemAssignmentHint("i1", ShareRule.ONLY, listOf("Lan"), true))),
            hint,
        )
    }

    @Test
    fun `item somebody did not have is kept as an exclusion, even when only the user is named`() {
        val hint = ready(
            """
            {"status": "ok", "currency": "USD",
             "items": [{"name": "Beer", "quantity": 3, "unit_price": 6, "amount": 18},
                       {"name": "Dessert", "quantity": 1, "unit_price": 9, "amount": 9}],
             "charges": [], "total": 27,
             "split": {"people": ["Lan"], "user_included": true, "people_count": 3,
                       "item_assignments": [{"item_index": 0, "rule": "all_except", "people": [], "includes_user": true},
                                            {"item_index": 1, "rule": "ALL_EXCEPT", "people": ["Lan"], "includes_user": false}]},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).splitHint

        assertEquals(
            listOf(
                ItemAssignmentHint("i1", ShareRule.ALL_EXCEPT, emptyList(), includesUser = true),
                ItemAssignmentHint("i2", ShareRule.ALL_EXCEPT, listOf("Lan"), includesUser = false),
            ),
            hint.itemAssignments,
        )
    }

    @Test
    fun `negative line read as an item is treated as a deduction`() {
        val bill = ready(
            """
            {"status": "ok", "currency": "USD",
             "items": [{"name": "Burger", "quantity": 1, "unit_price": 12, "amount": 12},
                       {"name": "Coupon", "quantity": 1, "unit_price": null, "amount": -2}],
             "charges": [], "total": 10,
             "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).bill

        assertEquals(listOf("Burger"), bill.items.map { it.name })
        assertEquals(listOf(-200L), bill.charges.map { it.amountMinor })
        assertTrue(bill.warnings.isEmpty())
    }

    @Test
    fun `rupiah bill printed without decimals is split in whole rupiah`() {
        val bill = ready(
            """
            {"status": "ok", "merchant": "Warung Sari", "date": "", "currency": "IDR", "amount_decimals": 0,
             "items": [{"name": "Nasi goreng", "quantity": 2, "unit_price": 35000, "amount": 70000},
                       {"name": "Es teh", "quantity": 3, "unit_price": 8000, "amount": 24000}],
             "charges": [{"label": "PB1 10%", "kind": "tax", "amount": 9400}], "total": 103400,
             "split": {"people": [], "user_included": true, "people_count": 3, "item_assignments": []},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).bill

        assertEquals(BillCurrency("IDR", 0), bill.currency)
        assertEquals(103_400L, bill.totalMinor)
        val thirds = SplitCalculator.split(bill, listOf("a", "b", "c")).shares.map { it.totalMinor }
        assertEquals("no share may carry decimals nobody can pay", listOf(34_467L, 34_467L, 34_466L), thirds)
    }

    @Test
    fun `dollar bill printed with cents is split to the cent`() {
        val bill = ready(
            """
            {"status": "ok", "currency": "USD", "amount_decimals": 2,
             "items": [{"name": "Burger", "quantity": 2, "unit_price": 10, "amount": 20}],
             "charges": [], "total": 20,
             "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
             "warnings": [], "message": ""}
            """.trimIndent(),
        ).bill

        assertEquals(BillCurrency("USD", 2), bill.currency)
        assertEquals(listOf(667L, 667L, 666L), SplitCalculator.split(bill, listOf("a", "b", "c")).shares.map { it.totalMinor })
    }

    @Test
    fun `bill without a recognisable currency is taken to be in the app's local currency`() {
        val json = """
            {"status": "ok", "currency": "XXX", "amount_decimals": 0,
             "items": [{"name": "Com tam", "quantity": 1, "unit_price": 45000, "amount": 45000}],
             "charges": [], "total": 45000,
             "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
             "warnings": [], "message": ""}
        """.trimIndent()

        val inAppCurrency = (BillAnalysisParser.parse(json, localCurrency = "VND") as BillAnalysis.Ready).bill
        val withoutSetting = (BillAnalysisParser.parse(json) as BillAnalysis.Ready).bill

        assertEquals(BillCurrency("VND", 0), inAppCurrency.currency)
        assertEquals(BillCurrency("", 0), withoutSetting.currency)
    }

    @Test
    fun `photo that is not a bill is rejected with the model's explanation`() {
        val analysis = BillAnalysisParser.parse(
            """{"status": "not_a_bill", "items": [], "charges": [], "total": 0, "message": "This looks like a menu, not a bill."}""",
        )

        assertEquals(BillAnalysis.Rejected("This looks like a menu, not a bill."), analysis)
    }

    @Test
    fun `bill without any amount is rejected`() {
        val analysis = BillAnalysisParser.parse(
            """{"status": "OK", "currency": "USD", "items": [], "charges": [], "total": 0, "message": ""}""",
        )

        assertTrue(analysis is BillAnalysis.Rejected)
    }

    @Test
    fun `answer that is not the agreed JSON is a bad response`() {
        val notJson = assertThrows(BillAnalyzerException::class.java) { BillAnalysisParser.parse("Sorry, I cannot help.") }
        val wrongShape = assertThrows(BillAnalyzerException::class.java) { BillAnalysisParser.parse("""{"items": "none"}""") }
        val absurd = assertThrows(BillAnalyzerException::class.java) {
            BillAnalysisParser.parse("""{"status": "ok", "currency": "USD", "items": [], "charges": [], "total": 1e13}""")
        }

        assertEquals(BillAnalyzerException.Kind.BAD_RESPONSE, notJson.kind)
        assertEquals(BillAnalyzerException.Kind.BAD_RESPONSE, wrongShape.kind)
        assertEquals(BillAnalyzerException.Kind.BAD_RESPONSE, absurd.kind)
    }
}
