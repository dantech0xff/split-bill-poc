package com.dantech.splitvibe.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupBalancesTest {

    private val thb = BillCurrency("THB", 2)
    private val usd = BillCurrency("USD", 2)
    private val vnd = BillCurrency("VND", 0)
    private val members = listOf("A", "B", "C", "D")

    private val usdToThb = ExchangeRate("USD", "THB", BigDecimal("35.5"), "2026-11-14", "Frankfurter")
    private val vndToThb = ExchangeRate("VND", "THB", BigDecimal("0.00137"), "2026-11-14", "Frankfurter")

    /**
     * A trip of four in Thailand, kept in baht: five bills in three currencies, added by A and B.
     * In baht minor units, at 35.5 THB to the dollar and 0.00137 THB to the dong:
     *
     * 1. Added by A. 1,200.00 THB for all four, A paid: 120000, parts of 30000.
     * 2. Added by A. 90.00 USD for A, B and C, B paid: 319500, parts of 106500.
     * 3. Added by B. 650,000 VND, A 200,000 and B, C, D 150,000 each; C paid 400,000 and D 250,000:
     *    89050, parts A 27400 and B, C, D 20550; C paid 54800 and D 34250.
     * 4. Added by B. 10.00 USD, A 3.34 and B, D 3.33; A paid 6.00 and D 4.00: 35500, parts A 11857,
     *    B 11822 and D 11821 (the unit left over goes to B, the first of two equal remainders);
     *    A paid 21300 and D 14200.
     * 5. Added by A. 480.00 THB for C and D, A paid: 48000, parts of 24000.
     *
     * D then paid A back 100.00 THB (10000).
     *
     * | Member | Bill 1 | Bill 2  | Bill 3 | Bill 4 | Bill 5 | Paid back | Balance |
     * |--------|--------|---------|--------|--------|--------|-----------|---------|
     * | A      | +90000 | −106500 | −27400 | +9443  | +48000 | −10000    | +3543   |
     * | B      | −30000 | +213000 | −20550 | −11822 | 0      | 0         | +150628 |
     * | C      | −30000 | −106500 | +34250 | 0      | −24000 | 0         | −126250 |
     * | D      | −30000 | 0       | +13700 | +2379  | −24000 | +10000    | −27921  |
     */
    private val tripBills: List<GroupAmounts>
        get() = listOf(
            inBaht(thb, null, 120_000, shares("A" to 30_000, "B" to 30_000, "C" to 30_000, "D" to 30_000), paid("A" to 120_000)),
            inBaht(usd, usdToThb, 9_000, shares("A" to 3_000, "B" to 3_000, "C" to 3_000), paid("B" to 9_000)),
            inBaht(
                vnd,
                vndToThb,
                650_000,
                shares("A" to 200_000, "B" to 150_000, "C" to 150_000, "D" to 150_000),
                paid("C" to 400_000, "D" to 250_000),
            ),
            inBaht(usd, usdToThb, 1_000, shares("A" to 334, "B" to 333, "D" to 333), paid("A" to 600, "D" to 400)),
            billFive(48_000),
        )

    private val paidBack = listOf(Transfer("D", "A", 10_000))

    @Test
    fun `a trip in three currencies comes to the balances worked out by hand`() {
        val converted = tripBills

        assertEquals(GroupAmounts(319_500, shares("A" to 106_500, "B" to 106_500, "C" to 106_500), paid("B" to 319_500)), converted[1])
        assertEquals(
            GroupAmounts(89_050, shares("A" to 27_400, "B" to 20_550, "C" to 20_550, "D" to 20_550), paid("C" to 54_800, "D" to 34_250)),
            converted[2],
        )
        assertEquals(
            GroupAmounts(35_500, shares("A" to 11_857, "B" to 11_822, "D" to 11_821), paid("A" to 21_300, "D" to 14_200)),
            converted[3],
        )

        val balances = Balances.of(members, converted, paidBack)

        assertEquals(mapOf("A" to 3_543L, "B" to 150_628L, "C" to -126_250L, "D" to -27_921L), balances)
        assertEquals(0L, balances.values.sum())
        assertEquals(
            listOf(Transfer("C", "B", 126_250), Transfer("D", "B", 24_378), Transfer("D", "A", 3_543)),
            DebtSimplifier.transfers(balances),
        )
    }

    @Test
    fun `changing a bill or its rate after everyone paid back shows just the difference`() {
        val settled = paidBack + DebtSimplifier.transfers(Balances.of(members, tripBills, paidBack))
        assertTrue(Balances.of(members, tripBills, settled).values.all { it == 0L })

        // Bill 5 was 520.00 THB, not 480.00: C and D each owe A another 20.00.
        val billFiveChanged = tripBills.toMutableList().apply { this[4] = billFive(52_000) }
        assertEquals(mapOf("A" to 4_000L, "B" to 0L, "C" to -2_000L, "D" to -2_000L), Balances.of(members, billFiveChanged, settled))

        // Bill 2 at 36 baht to the dollar: 324000 in all, 108000 each.
        val rateChanged = tripBills.toMutableList().apply {
            val atThirtySix = usdToThb.copy(rate = BigDecimal("36"))
            this[1] = inBaht(usd, atThirtySix, 9_000, shares("A" to 3_000, "B" to 3_000, "C" to 3_000), paid("B" to 9_000))
        }
        assertEquals(mapOf("A" to -1_500L, "B" to 3_000L, "C" to -1_500L, "D" to 0L), Balances.of(members, rateChanged, settled))
    }

    @Test
    fun `a bill in the group's currency is not converted`() {
        val amounts = GroupAmounts(1_001, shares("A" to 500, "B" to 501), paid("A" to 1_001))

        assertEquals(amounts, GroupConversion.convert(amounts, thb, thb, null))
    }

    @Test
    fun `a currency written to another precision is only rescaled`() {
        val amounts = GroupAmounts(1_000, shares("A" to 999, "B" to 1), paid("A" to 1_000))

        val converted = GroupConversion.convert(amounts, BillCurrency("IDR", 0), BillCurrency("IDR", 2), null)

        assertEquals(GroupAmounts(100_000, shares("A" to 99_900, "B" to 100), paid("A" to 100_000)), converted)
    }

    @Test
    fun `a bill in another currency needs a rate, and a rate that does not fit converts to nothing`() {
        val amounts = GroupAmounts(1_000, shares("A" to 1_000), paid("A" to 1_000))

        assertNull(GroupConversion.convert(amounts, usd, thb, null))
        assertNull(GroupConversion.convert(amounts, usd, thb, usdToThb.copy(rate = BigDecimal("1E+20"))))
        assertThrows(IllegalArgumentException::class.java) { GroupConversion.convert(amounts, usd, thb, vndToThb) }
    }

    @Test
    fun `participants who are the same member are added together`() {
        val shares = listOf(PersonShare(ME_ID, 600, 60), PersonShare("p1", 300, 30), PersonShare("p2", 100, 10))
        val payments = listOf(Payment("p1", 700), Payment("p2", 400))

        val amounts = GroupConversion.amountsOf(1_100, shares, payments, mapOf(ME_ID to "A", "p1" to "B", "p2" to "B"))

        assertEquals(GroupAmounts(1_100, shares("A" to 660, "B" to 440), paid("B" to 1_100)), amounts)
        assertThrows(IllegalArgumentException::class.java) { GroupConversion.amountsOf(1_100, shares, payments, mapOf(ME_ID to "A")) }
    }

    @Test
    fun `only amounts that add up and name members can stand in a group`() {
        val members = setOf("A", "B")
        val good = GroupAmounts(1_000, shares("A" to 400, "B" to 600), paid("B" to 1_000))

        assertTrue(good.isValidFor(members))
        assertFalse(good.copy(shares = shares("A" to 400, "B" to 500)).isValidFor(members))
        assertFalse(good.copy(paid = paid("B" to 999)).isValidFor(members))
        assertFalse(good.copy(paid = paid("B" to 1_001, "A" to -1)).isValidFor(members))
        assertFalse(good.copy(paid = paid("Z" to 1_000)).isValidFor(members))
        assertFalse(GroupAmounts(-1, shares("A" to -1), paid("A" to -1)).isValidFor(members))
        val tooLarge = BillCurrency.MAX_AMOUNT_MINOR + 1
        assertFalse(GroupAmounts(tooLarge, shares("A" to tooLarge), paid("A" to tooLarge)).isValidFor(members))
        // Parts that overflow a long when added could otherwise wrap round to the total.
        assertFalse(GroupAmounts(1, shares("A" to Long.MAX_VALUE, "B" to Long.MIN_VALUE + 2), paid("A" to 1)).isValidFor(members))
    }

    @Test
    fun `a member nobody knows still gets a balance, so the balances add up`() {
        val balances = Balances.of(listOf("A"), listOf(GroupAmounts(500, shares("A" to 200, "Z" to 300), paid("A" to 500))), emptyList())

        assertEquals(mapOf("A" to 300L, "Z" to -300L), balances)
    }

    @Test
    fun `a settled group needs no transfers, and equal debts are paid in member order`() {
        assertEquals(emptyList<Transfer>(), DebtSimplifier.transfers(mapOf("A" to 0L, "B" to 0L)))
        assertEquals(emptyList<Transfer>(), DebtSimplifier.transfers(emptyMap()))
        assertEquals(
            listOf(Transfer("A", "C", 500), Transfer("B", "C", 500)),
            DebtSimplifier.transfers(mapOf("A" to -500L, "B" to -500L, "C" to 1_000L)),
        )
        assertThrows(IllegalArgumentException::class.java) { DebtSimplifier.transfers(mapOf("A" to -1L)) }
    }

    private fun billFive(totalMinor: Long) =
        GroupAmounts(totalMinor, shares("C" to totalMinor / 2, "D" to totalMinor / 2), paid("A" to totalMinor))

    private fun inBaht(currency: BillCurrency, rate: ExchangeRate?, totalMinor: Long, shares: Map<String, Long>, paid: Map<String, Long>) =
        checkNotNull(GroupConversion.convert(GroupAmounts(totalMinor, shares, paid), currency, thb, rate))

    private fun shares(vararg parts: Pair<String, Number>) = amounts(parts)

    private fun paid(vararg parts: Pair<String, Number>) = amounts(parts)

    private fun amounts(parts: Array<out Pair<String, Number>>): Map<String, Long> =
        parts.associateTo(LinkedHashMap()) { (member, amount) -> member to amount.toLong() }
}
