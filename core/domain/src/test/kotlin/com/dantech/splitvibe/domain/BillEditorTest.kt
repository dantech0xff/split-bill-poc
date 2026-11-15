package com.dantech.splitvibe.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillEditorTest {

    private val usd = BillCurrency("USD", 2)
    private val bill = Bill(
        merchant = "Luigi's",
        date = "2026-11-13",
        currency = usd,
        items = listOf(
            BillItem("i1", "Pizza", BigDecimal.ONE, 1_800, 1_800),
            BillItem("i2", "Soda", BigDecimal(2), 250, 500),
        ),
        charges = listOf(BillCharge("c1", "Tax", ChargeKind.TAX, 230)),
        printedTotalMinor = 2_530,
    )

    @Test
    fun `changing an item keeps the printed total and shows the difference`() {
        val edited = BillEditor.withItemAmount(bill, "i1", 2_000)

        assertEquals(2_000L, edited.items.first().amountMinor)
        assertEquals(2_530L, edited.totalMinor)
        assertEquals(-200L, edited.differenceMinor)
    }

    @Test
    fun `a new quantity or unit price moves the line amount`() {
        val moreSoda = BillEditor.withItemQuantity(bill, "i2", BigDecimal(3))
        val pricier = BillEditor.withItemUnitPrice(bill, "i2", 300)

        assertEquals(750L, moreSoda.items[1].amountMinor)
        assertEquals(600L, pricier.items[1].amountMinor)
    }

    @Test
    fun `a line amount that no longer agrees with the unit price drops the unit price`() {
        val edited = BillEditor.withItemAmount(bill, "i2", 520)

        assertNull(edited.items[1].unitPriceMinor)
        assertEquals(250L, BillEditor.withItemAmount(bill, "i2", 500).items[1].unitPriceMinor)
    }

    @Test
    fun `added items and charges get ids that were never used on the bill`() {
        val withSalad = BillEditor.withItemAdded(bill, " Salad ", BigDecimal(2), unitPriceMinor = 450)
        val withoutPizza = BillEditor.withoutItem(withSalad, "i1")
        val withBread = BillEditor.withItemAdded(withoutPizza, "Bread", amountMinor = 300)
        val withTip = BillEditor.withChargeAdded(bill, "", ChargeKind.TIP, 400)

        assertEquals(BillItem("i3", "Salad", BigDecimal(2), 450, 900), withSalad.items.last())
        assertEquals(listOf("i2", "i3", "i4"), withBread.items.map { it.id })
        assertEquals(BillCharge("c2", "Tip", ChargeKind.TIP, 400), withTip.charges.last())
    }

    @Test
    fun `a difference is settled by trusting the lines or by keeping the total`() {
        val edited = BillEditor.withItemAmount(bill, "i1", 2_000)

        val linesWin = BillEditor.withLinesAsTotal(edited)
        val totalWins = BillEditor.withDifferenceAsCharge(edited)

        assertEquals(2_730L, linesWin.totalMinor)
        assertEquals(0L, linesWin.differenceMinor)
        assertEquals(2_530L, totalWins.totalMinor)
        assertEquals(BillCharge("c2", BillEditor.ADJUSTMENT_LABEL, ChargeKind.OTHER, -200), totalWins.charges.last())
        assertEquals(0L, totalWins.differenceMinor)
    }

    @Test
    fun `a bill typed in by hand without a total is what its lines add up to`() {
        var typed = Bill.empty(BillCurrency("VND", 0))
        typed = BillEditor.withItemAdded(typed, "Pho bo", amountMinor = 65_000)
        typed = BillEditor.withItemAdded(typed, "Tra da", BigDecimal(2), unitPriceMinor = 5_000)
        typed = BillEditor.withChargeAdded(typed, "VAT", ChargeKind.TAX, 7_500)

        assertEquals(82_500L, typed.totalMinor)
        assertEquals(0L, typed.differenceMinor)
    }

    @Test
    fun `charges are signed the way the bill uses them`() {
        assertEquals(-500L, BillEditor.signedCharge(ChargeKind.DISCOUNT, 500))
        assertEquals(-500L, BillEditor.signedCharge(ChargeKind.DISCOUNT, -500))
        assertEquals(500L, BillEditor.signedCharge(ChargeKind.TIP, -500))
        assertEquals(-3L, BillEditor.signedCharge(ChargeKind.ROUNDING, -3))
    }

    @Test
    fun `moving a bill to another currency keeps every amount as written`() {
        val inYen = BillEditor.withCurrency(bill, BillCurrency("JPY", 0))
        val back = BillEditor.withCurrency(inYen, usd)

        assertEquals(listOf(18L, 5L), inYen.items.map { it.amountMinor })
        assertEquals(25L, inYen.totalMinor)
        assertEquals(listOf(1_800L, 500L), back.items.map { it.amountMinor })
    }

    @Test
    fun `merchant and date can be cleared`() {
        assertNull(BillEditor.withMerchant(bill, "  ").merchant)
        assertNull(BillEditor.withDate(bill, "").date)
    }
}
