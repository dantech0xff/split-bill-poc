package com.dantech.splitvibe.domain

import java.math.BigDecimal
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    private val usd = BillCurrency("USD", 2)
    private val vnd = BillCurrency("VND", 0)

    private fun bill(items: List<Long>, charges: List<Long> = emptyList(), currency: BillCurrency = usd): Bill {
        val billItems = items.mapIndexed { i, amount -> BillItem("Item $i", BigDecimal.ONE, amount, amount) }
        val billCharges = charges.mapIndexed { i, amount -> BillCharge("Charge $i", ChargeKind.OTHER, amount) }
        return BillReconciler.reconcile(
            merchant = null,
            date = null,
            currency = currency,
            items = billItems,
            charges = billCharges,
            totalMinor = items.sum() + charges.sum(),
            warnings = emptyList(),
        )
    }

    private fun SplitResult.totals() = shares.map { it.totalMinor }

    @Test
    fun `equal split gives the indivisible unit to the first person`() {
        val result = SplitCalculator.split(bill(listOf(100_000), currency = vnd), listOf("me", "minh", "lan"))

        assertEquals(listOf(33_334L, 33_333L, 33_333L), result.totals())
        assertEquals(100_000L, result.totalMinor)
    }

    @Test
    fun `equal split divides items and charges evenly`() {
        val result = SplitCalculator.split(bill(items = listOf(6_000, 3_000), charges = listOf(900)), listOf("me", "minh"))

        assertEquals(listOf(4_950L, 4_950L), result.totals())
        assertEquals(listOf(4_500L, 4_500L), result.shares.map { it.itemsMinor })
        assertEquals(listOf(450L, 450L), result.shares.map { it.chargesMinor })
    }

    @Test
    fun `assigned item is paid only by its people and charges follow consumption`() {
        // Beer 30.00 is Minh's alone; the 60.00 pizza is shared by all three; tax is 10%.
        val bill = bill(items = listOf(3_000, 6_000), charges = listOf(900))

        val result = SplitCalculator.split(bill, listOf("me", "minh", "lan"), mapOf(0 to setOf("minh")))

        assertEquals(listOf(2_000L, 5_000L, 2_000L), result.shares.map { it.itemsMinor })
        assertEquals(listOf(200L, 500L, 200L), result.shares.map { it.chargesMinor })
        assertEquals(9_900L, result.totalMinor)
    }

    @Test
    fun `discount is shared in proportion to what each person ordered`() {
        val bill = bill(items = listOf(10_000, 5_000), charges = listOf(-1_500))

        val result = SplitCalculator.split(bill, listOf("me", "minh"), mapOf(0 to setOf("me"), 1 to setOf("minh")))

        assertEquals(listOf(-1_000L, -500L), result.shares.map { it.chargesMinor })
        assertEquals(listOf(9_000L, 4_500L), result.totals())
    }

    @Test
    fun `person with no items pays no charges`() {
        val bill = bill(items = listOf(4_000), charges = listOf(400))

        val result = SplitCalculator.split(bill, listOf("me", "minh"), mapOf(0 to setOf("minh")))

        assertEquals(listOf(0L, 4_400L), result.totals())
    }

    @Test
    fun `items shared by the same people are pooled before dividing`() {
        // 95.000 and 185.000 are shared by all three; divided one by one they would leave two odd
        // units to hand out, pooled (280.000) there is one, and the first person takes it.
        val bill = bill(items = listOf(95_000, 185_000, 50_000), currency = vnd)

        val result = SplitCalculator.split(bill, listOf("a", "b", "c"), mapOf(2 to setOf("b")))

        assertEquals(listOf(93_334L, 143_333L, 93_333L), result.totals())
    }

    @Test
    fun `proportional charge that cannot be divided still adds up`() {
        val bill = bill(items = listOf(1_000, 1_000, 1_000), charges = listOf(100))
        val assignments = mapOf(0 to setOf("a"), 1 to setOf("b"), 2 to setOf("c"))

        val result = SplitCalculator.split(bill, listOf("a", "b", "c"), assignments)

        assertEquals(listOf(34L, 33L, 33L), result.shares.map { it.chargesMinor })
        assertEquals(3_100L, result.totalMinor)
    }

    @Test
    fun `assignments to people who are not sharing are ignored`() {
        val bill = bill(items = listOf(1_000, 2_000))

        val result = SplitCalculator.split(bill, listOf("me", "minh"), mapOf(0 to setOf("stranger"), 1 to setOf("minh", "stranger")))

        // Item 0 falls back to everyone; item 1 is Minh's.
        assertEquals(listOf(500L, 2_500L), result.totals())
    }

    @Test
    fun `bill without readable items is split equally on the total`() {
        val bill = BillReconciler.reconcile(null, null, vnd, emptyList(), emptyList(), 250_000, emptyList())

        val result = SplitCalculator.split(bill, listOf("me", "minh", "lan", "an"))

        assertEquals(listOf(62_500L, 62_500L, 62_500L, 62_500L), result.totals())
    }

    @Test
    fun `no participants means no shares`() {
        assertTrue(SplitCalculator.split(bill(listOf(1_000)), emptyList()).shares.isEmpty())
    }

    @Test
    fun `shares always add up to the bill total and nobody owes a negative amount`() {
        val random = Random(20261113)
        repeat(2_000) {
            // Mostly purchases, now and then a negative line such as a voucher or a void.
            val items = List(random.nextInt(0, 12)) {
                if (random.nextInt(8) ==
                    0
                ) {
                    -random.nextLong(0, 50_000)
                } else {
                    random.nextLong(0, 5_000_000)
                }
            }
            val charges = List(random.nextInt(0, 4)) { random.nextLong(-200_000, 600_000) }
            val bill = bill(items, charges)
            val people = List(random.nextInt(1, 8)) { "p$it" }
            val assignments = bill.items.indices
                .filter { random.nextBoolean() }
                .associateWith { people.filter { random.nextBoolean() }.toSet() }

            val result = SplitCalculator.split(bill, people, assignments)

            val case = "bill $items $charges split by $people with $assignments"
            assertEquals(case, bill.totalMinor, result.totalMinor)
            assertEquals(case, bill.itemsTotalMinor, result.shares.sumOf { it.itemsMinor })
            assertEquals(case, people, result.shares.map { it.personId })
            if (bill.totalMinor >= 0) {
                assertTrue(case, result.shares.all { it.totalMinor >= 0 })
            }
        }
    }

    @Test
    fun `divideEqually keeps parts within one unit and handles negatives`() {
        assertEquals(listOf(4L, 3L, 3L), SplitCalculator.divideEqually(10, 3))
        assertEquals(listOf(4L, 4L, 3L), SplitCalculator.divideEqually(11, 3))
        assertEquals(listOf(-4L, -3L, -3L), SplitCalculator.divideEqually(-10, 3))
        assertEquals(listOf(0L, 0L), SplitCalculator.divideEqually(0, 2))
    }

    @Test
    fun `divideProportionally survives amounts too large for naive multiplication`() {
        val huge = 4_000_000_000_000_000L
        val parts = SplitCalculator.divideProportionally(huge, listOf(huge, huge, huge + 1))

        assertEquals(huge, parts.sum())
    }
}
