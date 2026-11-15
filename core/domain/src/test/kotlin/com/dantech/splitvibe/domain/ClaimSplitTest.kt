package com.dantech.splitvibe.domain

import com.dantech.splitvibe.domain.ClaimSplit.Pick
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaimSplitTest {

    private val vnd = BillCurrency("VND", 0)
    private val people = listOf(ME_ID, "p1", "p2")

    /**
     * Pho 60,000, bun cha 50,000 and a jug of tea 30,000 that nobody picked, with a service charge of
     * 14,000 on 140,000 of items.
     */
    private val bill = BillReconciler.reconcile(
        merchant = "Pho 24",
        date = "2026-11-14",
        currency = vnd,
        items = listOf(
            BillItem("i1", "Pho bo", BigDecimal.ONE, 60_000, 60_000),
            BillItem("i2", "Bun cha", BigDecimal.ONE, 50_000, 50_000),
            BillItem("i3", "Tea", BigDecimal.ONE, 30_000, 30_000),
        ),
        charges = listOf(BillCharge("c1", "Service charge", ChargeKind.SERVICE_CHARGE, 14_000)),
        printedTotalMinor = 154_000,
        warnings = emptyList(),
    )

    private fun shares(setup: SplitSetup) = SplitCalculator.split(bill, people, setup).shares.associate { it.personId to it.totalMinor }

    @Test
    fun `each person pays for what they picked, and everyone shares what nobody picked`() {
        val setup = ClaimSplit.setup(bill, listOf(Pick(ME_ID, setOf("i1")), Pick("p1", setOf("i1", "i2"))))

        assertEquals(Division.Equally(among = setOf(ME_ID, "p1")), setup.items["i1"])
        assertEquals(Division.Equally(among = setOf("p1")), setup.items["i2"])
        assertNull("Nobody picked the tea, so it is shared by everyone", setup.items["i3"])
        // Items: me 30,000 + 10,000 of the tea; p1 30,000 + 50,000 + 10,000; p2 10,000.
        // The service charge of 14,000 follows the items: 4,000, 9,000 and 1,000.
        assertEquals(mapOf(ME_ID to 44_000L, "p1" to 99_000L, "p2" to 11_000L), shares(setup))
        assertEquals(bill.totalMinor, shares(setup).values.sum())
    }

    @Test
    fun `two claims of the same person add up, and items no longer on the bill are left out`() {
        val setup = ClaimSplit.setup(bill, listOf(Pick("p1", setOf("i1")), Pick("p1", setOf("i2", "gone")), Pick("p2", setOf("gone"))))

        assertEquals(setOf("i1", "i2"), setup.items.keys)
        assertEquals(Division.Equally(among = setOf("p1")), setup.items["i1"])
        assertEquals(Division.Equally(among = setOf("p1")), setup.items["i2"])
    }

    @Test
    fun `someone who picked an item shares the bill, and the others who did not share stay out`() {
        val setup = ClaimSplit.setup(bill, listOf(Pick("p2", setOf("i3"))), notSharing = setOf("p1", "p2"))

        assertEquals(setOf("p1"), setup.notSharing)
        assertNull(setup.whole)
    }

    @Test
    fun `without picks the bill is split by item with every item shared by everyone`() {
        val setup = ClaimSplit.setup(bill, emptyList())

        assertEquals(SplitSetup(whole = null), setup)
        assertEquals(bill.totalMinor, shares(setup).values.sum())
    }
}
