package com.dantech.splitvibe.domain

import java.math.BigDecimal
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money rules hold for any bill, not just the examples: 2,000 random bills for each way of
 * splitting, with random people, random payers and random gaps to the printed total. The shares
 * always add up to the bill total in whole minor units, and so do the payments.
 */
class SplitPropertyTest {

    private val random = Random(20261115)

    @Test
    fun `split equally`() = check(SplitMode.EQUAL) { _, _, sharing ->
        SplitSetup(whole = Division.Equally(among = subsetOf(sharing)))
    }

    @Test
    fun `split item by item, each item its own way`() = check(SplitMode.BY_ITEM) { bill, people, sharing ->
        SplitSetup(
            whole = null,
            items = bill.items.filter {
                random.nextBoolean()
            }.associate { item -> item.id to divisionFor(item.amountMinor, people, sharing) },
        )
    }

    @Test
    fun `split by shares`() = check(SplitMode.BY_SHARES) { _, _, sharing ->
        val shares = sharing.associateWith { random.nextLong(0, 6) }.toMutableMap()
        shares[sharing.random(random)] = random.nextLong(1, 6)
        SplitSetup(whole = Division.ByShares(shares))
    }

    @Test
    fun `split by percent`() = check(SplitMode.BY_PERCENT) { _, _, sharing ->
        SplitSetup(whole = Division.ByPercent(partition(Division.ByPercent.FULL, sharing)))
    }

    @Test
    fun `split by exact amounts`() = check(SplitMode.BY_AMOUNT) { bill, _, sharing ->
        SplitSetup(whole = Division.ByAmount(partition(bill.totalMinor, sharing)))
    }

    private fun check(mode: SplitMode, setupFor: (Bill, List<String>, List<String>) -> SplitSetup) = repeat(2_000) {
        val bill = randomBill()
        val people = listOf(ME_ID) + List(random.nextInt(0, 7)) { "p${it + 1}" }
        val notSharing = people.filter { random.nextInt(5) == 0 }.toSet().takeIf { it.size < people.size } ?: emptySet()
        val sharing = people.filter { it !in notSharing }
        val setup = setupFor(bill, people, sharing).copy(notSharing = notSharing)
        val payments = partition(
            bill.totalMinor,
            subsetOf(people).ifEmpty {
                setOf(ME_ID)
            }.toList(),
        ).map { (id, amount) -> Payment(id, amount) }

        val result = SplitCalculator.split(bill, people, setup)

        val case = "$mode: ${bill.items.map { it.amountMinor }} ${bill.charges.map { it.amountMinor }} total ${bill.totalMinor} " +
            "between $people (not sharing $notSharing) with $setup"
        assertEquals(case, mode, setup.mode)
        assertTrue(case + " " + result.problems, result.isComplete)
        assertEquals(case, people, result.shares.map { it.personId })
        assertEquals(case, bill.totalMinor, result.totalMinor)
        assertEquals(case, bill.itemsTotalMinor, result.shares.sumOf { it.itemsMinor })
        assertTrue(case, result.shares.filter { it.personId in notSharing }.all { it.totalMinor == 0L })
        when (val whole = setup.whole) {
            is Division.Equally -> {
                val sharers = result.shares.filter { it.totalMinor != 0L || bill.totalMinor == 0L }.map { it.totalMinor }
                assertTrue(case, sharers.isEmpty() || sharers.max() - sharers.min() <= 1)
            }

            is Division.ByAmount -> {
                // Everyone pays exactly the amount given to them; who does not share pays nothing.
                val given = people.map { if (it in notSharing) 0L else whole.amounts[it] ?: 0L }
                assertEquals(case, given, result.shares.map { it.totalMinor })
            }

            else -> Unit
        }
        assertEquals(case, bill.totalMinor, payments.sumOf { it.amountMinor })
        assertNull(case, Payments.problem(payments, bill.totalMinor, bill.currency))
        // A bill edited afterwards keeps a single payer paying all of it.
        val edited = bill.totalMinor + random.nextLong(0, 10_000)
        assertEquals(case, edited, Payments.followTotal(listOf(Payment(ME_ID, bill.totalMinor)), edited).sumOf { it.amountMinor })
    }

    private fun randomBill(): Bill {
        val currency = if (random.nextBoolean()) BillCurrency("USD", 2) else BillCurrency("VND", 0)
        val items = List(random.nextInt(0, 12)) { i ->
            BillItem("i${i + 1}", "Item ${i + 1}", BigDecimal.ONE, null, random.nextLong(0, 5_000_000))
        }
        val charges = List(random.nextInt(0, 4)) { i ->
            BillCharge("c${i + 1}", "Charge ${i + 1}", ChargeKind.OTHER, random.nextLong(-200_000, 600_000))
        }
        val lines = items.sumOf { it.amountMinor } + charges.sumOf { it.amountMinor }
        // Now and then the printed total differs from the lines, or there is none.
        val printed = when (random.nextInt(4)) {
            0 -> null
            1 -> lines + random.nextLong(-50_000, 50_000)
            else -> lines
        }
        val bill = Bill(null, null, currency, items, charges, printed)
        // Bills that come to less than nothing are refunds, not splits.
        return if (bill.totalMinor >= 0) bill else bill.copy(printedTotalMinor = 0)
    }

    /** A random division of [amount] that is valid for the people [sharing] it. */
    private fun divisionFor(amount: Long, people: List<String>, sharing: List<String>): Division = when (random.nextInt(4)) {
        0 -> Division.Equally(among = subsetOf(people), except = subsetOf(people).takeIf { random.nextInt(3) == 0 } ?: emptySet())
        1 -> Division.ByShares(sharing.associateWith { random.nextLong(0, 4) } + (sharing.random(random) to random.nextLong(1, 4)))
        2 -> Division.ByPercent(partition(Division.ByPercent.FULL, sharing))
        else -> Division.ByAmount(partition(amount, sharing))
    }

    /** [amount] cut at random into non-negative parts for [people], adding up to it exactly. */
    private fun partition(amount: Long, people: List<String>): Map<String, Long> {
        val cuts = (List(people.size - 1) { random.nextLong(0, amount + 1) } + listOf(0L, amount)).sorted()
        return people.indices.associate { i -> people[i] to cuts[i + 1] - cuts[i] }
    }

    private fun subsetOf(people: List<String>): Set<String> = people.filter { random.nextBoolean() }.toSet()
}
