package com.dantech.splitvibe.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillReconcilerTest {

    private val usd = BillCurrency("USD", 2)
    private val items = listOf(
        BillItem("i1", "Pizza", BigDecimal.ONE, 1_800, 1_800),
        BillItem("i2", "Soda", BigDecimal(2), 250, 500),
    )
    private val tax = BillCharge("c1", "Tax", ChargeKind.TAX, 230)

    @Test
    fun `consistent bill is kept as is`() {
        val bill = BillReconciler.reconcile("Luigi's", "2026-11-13", usd, items, listOf(tax), 2_530, listOf("note"))

        assertEquals(listOf(tax), bill.charges)
        assertEquals(listOf("note"), bill.warnings)
        assertEquals(0L, bill.differenceMinor)
        assertNull(bill.differenceNote())
    }

    @Test
    fun `gap between the lines and the printed total is shown and shared, not hidden`() {
        val bill = BillReconciler.reconcile(null, null, usd, items, listOf(tax), 2_600, emptyList())

        assertEquals(listOf(tax), bill.charges)
        assertEquals(2_600L, bill.totalMinor)
        assertEquals(70L, bill.differenceMinor)
        assertEquals(300L, bill.sharedChargesMinor)
        assertTrue(bill.differenceNote()!!.contains("Please check the items"))
    }

    @Test
    fun `bill with only a total says that only an equal split is possible`() {
        val bill = BillReconciler.reconcile(null, null, usd, emptyList(), emptyList(), 5_000, emptyList())

        assertEquals(5_000L, bill.differenceMinor)
        assertTrue(bill.differenceNote()!!.contains("equal split"))
    }

    @Test
    fun `bill without a printed total is what its lines add up to`() {
        val bill = BillReconciler.reconcile(null, null, usd, items, listOf(tax), printedTotalMinor = null, emptyList())

        assertEquals(2_530L, bill.totalMinor)
        assertEquals(0L, bill.differenceMinor)
    }

    @Test
    fun `line with a negative amount becomes a deduction on the whole bill`() {
        val voucher = BillItem("i3", "Voucher", BigDecimal.ONE, null, -300)

        val bill = BillReconciler.reconcile(null, null, usd, items + voucher, listOf(tax), 2_230, emptyList())

        assertEquals(items, bill.items)
        assertEquals(listOf(tax, BillCharge("c2", "Voucher", ChargeKind.DISCOUNT, -300)), bill.charges)
        assertEquals(0L, bill.differenceMinor)
    }
}
