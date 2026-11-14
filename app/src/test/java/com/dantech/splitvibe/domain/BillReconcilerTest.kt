package com.dantech.splitvibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BillReconcilerTest {

    private val usd = BillCurrency("USD", 2)
    private val items = listOf(
        BillItem("Pizza", BigDecimal.ONE, 1_800, 1_800),
        BillItem("Soda", BigDecimal(2), 250, 500),
    )
    private val tax = BillCharge("Tax", ChargeKind.TAX, 230)

    @Test
    fun `consistent bill is kept as is`() {
        val bill = BillReconciler.reconcile("Luigi's", "2026-11-13", usd, items, listOf(tax), 2_530, listOf("note"))

        assertEquals(listOf(tax), bill.charges)
        assertEquals(listOf("note"), bill.warnings)
        assertEquals(bill.totalMinor, bill.itemsTotalMinor + bill.chargesTotalMinor)
    }

    @Test
    fun `gap between items and printed total becomes a visible line and a warning`() {
        val bill = BillReconciler.reconcile(null, null, usd, items, listOf(tax), 2_600, emptyList())

        val difference = bill.charges.last()
        assertEquals(BillReconciler.DIFFERENCE_LABEL, difference.label)
        assertEquals(70L, difference.amountMinor)
        assertEquals(2_600L, bill.itemsTotalMinor + bill.chargesTotalMinor)
        assertTrue(bill.warnings.single().contains("Please check the items"))
    }

    @Test
    fun `bill with only a total warns that only an equal split is possible`() {
        val bill = BillReconciler.reconcile(null, null, usd, emptyList(), emptyList(), 5_000, emptyList())

        assertEquals(5_000L, bill.chargesTotalMinor)
        assertTrue(bill.warnings.single().contains("equal split"))
    }

    @Test
    fun `line with a negative amount becomes a deduction on the whole bill`() {
        val voucher = BillItem("Voucher", BigDecimal.ONE, null, -300)

        val bill = BillReconciler.reconcile(null, null, usd, items + voucher, listOf(tax), 2_230, emptyList())

        assertEquals(items, bill.items)
        assertEquals(listOf(tax, BillCharge("Voucher", ChargeKind.DISCOUNT, -300)), bill.charges)
        assertEquals(2_230L, bill.itemsTotalMinor + bill.chargesTotalMinor)
        assertTrue(bill.warnings.isEmpty())
    }
}
