package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun `a big integer becomes a Long exactly when it fits, as on Android before longValueExact existed`() {
        val longs = listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE)
        longs.forEach { assertEquals(it, BigInteger.valueOf(it).toLongExact()) }

        val justOver = BigInteger.valueOf(Long.MAX_VALUE) + BigInteger.ONE
        val justUnder = BigInteger.valueOf(Long.MIN_VALUE) - BigInteger.ONE
        listOf(justOver, justUnder, justOver.shiftLeft(10)).forEach { number ->
            assertThrows(ArithmeticException::class.java) { number.toLongExact() }
        }
    }

    private val usd = BillCurrency("USD", 2)
    private val vnd = BillCurrency("VND", 0)

    private fun bill(items: List<Long>, charges: List<Long> = emptyList(), currency: BillCurrency = usd, printedTotal: Long? = null): Bill =
        BillReconciler.reconcile(
            merchant = null,
            date = null,
            currency = currency,
            items = items.mapIndexed { i, amount -> BillItem("i${i + 1}", "Item ${i + 1}", BigDecimal.ONE, amount, amount) },
            charges = charges.mapIndexed { i, amount -> BillCharge("c${i + 1}", "Charge ${i + 1}", ChargeKind.OTHER, amount) },
            printedTotalMinor = printedTotal ?: (items.sum() + charges.sum()),
            warnings = emptyList(),
        )

    private fun byItem(vararg divisions: Pair<String, Division>) = SplitSetup(whole = null, items = divisions.toMap())

    private fun only(vararg people: String) = Division.Equally(among = people.toSet())

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

        val result = SplitCalculator.split(bill, listOf("me", "minh", "lan"), byItem("i1" to only("minh")))

        assertEquals(listOf(2_000L, 5_000L, 2_000L), result.shares.map { it.itemsMinor })
        assertEquals(listOf(200L, 500L, 200L), result.shares.map { it.chargesMinor })
        assertEquals(9_900L, result.totalMinor)
    }

    @Test
    fun `discount is shared in proportion to what each person ordered`() {
        val bill = bill(items = listOf(10_000, 5_000), charges = listOf(-1_500))

        val result = SplitCalculator.split(bill, listOf("me", "minh"), byItem("i1" to only("me"), "i2" to only("minh")))

        assertEquals(listOf(-1_000L, -500L), result.shares.map { it.chargesMinor })
        assertEquals(listOf(9_000L, 4_500L), result.totals())
    }

    @Test
    fun `gap to the printed total is shared like a charge`() {
        // The lines add up to 90.00 but the bill says 100.00.
        val bill = bill(items = listOf(6_000, 3_000), printedTotal = 10_000)

        val result = SplitCalculator.split(bill, listOf("me", "minh"), byItem("i1" to only("me"), "i2" to only("minh")))

        assertEquals(listOf(6_667L, 3_333L), result.totals())
        assertEquals(10_000L, result.totalMinor)
    }

    @Test
    fun `person with no items pays no charges`() {
        val bill = bill(items = listOf(4_000), charges = listOf(400))

        val result = SplitCalculator.split(bill, listOf("me", "minh"), byItem("i1" to only("minh")))

        assertEquals(listOf(0L, 4_400L), result.totals())
    }

    @Test
    fun `items shared by the same people are pooled before dividing`() {
        // 95.000 and 185.000 are shared by all three; divided one by one they would leave two odd
        // units to hand out, pooled (280.000) there is one, and the first person takes it.
        val bill = bill(items = listOf(95_000, 185_000, 50_000), currency = vnd)

        val result = SplitCalculator.split(bill, listOf("a", "b", "c"), byItem("i3" to only("b")))

        assertEquals(listOf(93_334L, 143_333L, 93_333L), result.totals())
    }

    @Test
    fun `proportional charge that cannot be divided still adds up`() {
        val bill = bill(items = listOf(1_000, 1_000, 1_000), charges = listOf(100))

        val result = SplitCalculator.split(bill, listOf("a", "b", "c"), byItem("i1" to only("a"), "i2" to only("b"), "i3" to only("c")))

        assertEquals(listOf(34L, 33L, 33L), result.shares.map { it.chargesMinor })
        assertEquals(3_100L, result.totalMinor)
    }

    @Test
    fun `assignments to people who are not on the bill are ignored`() {
        val bill = bill(items = listOf(1_000, 2_000))

        val result = SplitCalculator.split(bill, listOf("me", "minh"), byItem("i1" to only("stranger"), "i2" to only("minh", "stranger")))

        // Item 1 falls back to everyone; item 2 is Minh's.
        assertEquals(listOf(500L, 2_500L), result.totals())
    }

    @Test
    fun `everyone but the people left out shares an item, including people added later`() {
        val bill = bill(items = listOf(3_000, 6_000))

        val result = SplitCalculator.split(bill, listOf("me", "minh", "lan"), byItem("i1" to Division.Equally(except = setOf("me"))))

        assertEquals(listOf(2_000L, 3_500L, 3_500L), result.totals())
    }

    @Test
    fun `someone who only paid gets a share of zero`() {
        val bill = bill(items = listOf(9_000))

        val result = SplitCalculator.split(bill, listOf("me", "minh", "lan"), SplitSetup(notSharing = setOf("me")))

        assertEquals(listOf(0L, 4_500L, 4_500L), result.totals())
    }

    @Test
    fun `an item can be divided by shares, percent or exact amounts`() {
        val bill = bill(items = listOf(9_000, 1_000, 3_000))

        val result = SplitCalculator.split(
            bill,
            listOf("me", "minh"),
            byItem(
                "i1" to Division.ByShares(mapOf("me" to 2L, "minh" to 1L)),
                "i2" to Division.ByPercent(mapOf("me" to 2_500L, "minh" to 7_500L)),
                "i3" to Division.ByAmount(mapOf("me" to 1_000L, "minh" to 2_000L)),
            ),
        )

        assertEquals(listOf(6_000L + 250L + 1_000L, 3_000L + 750L + 2_000L), result.totals())
    }

    @Test
    fun `the whole bill can be divided by shares`() {
        val bill = bill(items = listOf(8_000), charges = listOf(1_000))

        val result = SplitCalculator.split(
            bill,
            listOf("me", "minh", "lan"),
            SplitSetup(
                whole = Division.ByShares(
                    mapOf(
                        "me" to 1L,
                        "minh" to 2L,
                        "lan" to 0L,
                    ),
                ),
            ),
        )

        assertEquals(listOf(3_000L, 6_000L, 0L), result.totals())
        assertEquals(8_000L, result.shares.sumOf { it.itemsMinor })
    }

    @Test
    fun `the whole bill can be divided by percent`() {
        val bill = bill(items = listOf(100_000), currency = vnd)

        val result = SplitCalculator.split(
            bill,
            listOf("me", "minh", "lan"),
            SplitSetup(
                whole = Division.ByPercent(
                    mapOf(
                        "me" to 3_333L,
                        "minh" to 3_333L,
                        "lan" to 3_334L,
                    ),
                ),
            ),
        )

        assertEquals(listOf(33_330L, 33_330L, 33_340L), result.totals())
    }

    @Test
    fun `the whole bill can be divided by exact amounts`() {
        val bill = bill(items = listOf(7_000), charges = listOf(700))

        val result = SplitCalculator.split(
            bill,
            listOf("me", "minh"),
            SplitSetup(
                whole = Division.ByAmount(
                    mapOf(
                        "me" to 5_000L,
                        "minh" to 2_700L,
                    ),
                ),
            ),
        )

        assertEquals(listOf(5_000L, 2_700L), result.totals())
        assertEquals(7_000L, result.shares.sumOf { it.itemsMinor })
    }

    @Test
    fun `percentages or amounts that do not add up give no shares but say what is wrong`() {
        val bill = bill(items = listOf(10_000))
        val people = listOf("me", "minh")

        val percent = SplitCalculator.split(bill, people, SplitSetup(whole = Division.ByPercent(mapOf("me" to 5_000L, "minh" to 4_550L))))
        val amounts = SplitCalculator.split(bill, people, SplitSetup(whole = Division.ByAmount(mapOf("me" to 6_000L, "minh" to 3_000L))))
        val shares = SplitCalculator.split(bill, people, SplitSetup(whole = Division.ByShares(emptyMap())))
        val item = SplitCalculator.split(bill, people, byItem("i1" to Division.ByAmount(mapOf("me" to 11_000L))))

        assertEquals(listOf("Percentages add up to 95.5%. They need to add up to 100%."), percent.problems)
        assertTrue(amounts.problems.single(), amounts.problems.single().startsWith("Amounts add up to"))
        assertEquals(listOf("Give at least one person a share."), shares.problems)
        assertTrue(item.problems.single(), item.problems.single().startsWith("Item 1: Amounts add up to"))
        listOf(percent, amounts, shares, item).forEach { assertTrue(it.shares.isEmpty()) }
    }

    @Test
    fun `bill without readable items is split equally on the total`() {
        val bill = bill(items = emptyList(), currency = vnd, printedTotal = 250_000)

        val result = SplitCalculator.split(bill, listOf("me", "minh", "lan", "an"))

        assertEquals(listOf(62_500L, 62_500L, 62_500L, 62_500L), result.totals())
    }

    @Test
    fun `nobody to share the bill means no shares`() {
        assertTrue(SplitCalculator.split(bill(listOf(1_000)), emptyList()).shares.isEmpty())
        assertEquals(
            listOf("Choose at least one person to share the bill."),
            SplitCalculator.split(bill(listOf(1_000)), listOf("me"), SplitSetup(notSharing = setOf("me"))).problems,
        )
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
