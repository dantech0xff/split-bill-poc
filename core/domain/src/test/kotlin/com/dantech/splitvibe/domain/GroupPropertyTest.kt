package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The group rules hold for any group, not just the examples: 2,000 random groups of one to eight
 * members, with bills in up to six currencies added by different members, random parts and payers,
 * random rates and random payments back.
 */
class GroupPropertyTest {

    private val random = Random(20261115)
    private val currencies = listOf(
        BillCurrency("THB", 2),
        BillCurrency("VND", 0),
        BillCurrency("USD", 2),
        BillCurrency("JPY", 0),
        BillCurrency("KWD", 3),
        BillCurrency("EUR", 2),
    )

    @Test
    fun `any group adds up to zero and settles in fewer transfers than it has members`() = repeat(2_000) {
        val members = List(random.nextInt(1, 9)) { "m$it" }
        val groupCurrency = currencies.random(random)
        val bills = List(random.nextInt(0, 9)) { converted(randomBill(members), groupCurrency, members) }
        val paidBack = if (members.size < 2) {
            emptyList()
        } else {
            List(random.nextInt(0, 4)) {
                val (from, to) = members.shuffled(random).take(2)
                Transfer(from, to, random.nextLong(1, 10_000_000))
            }
        }

        val balances = Balances.of(members, bills, paidBack)
        val transfers = DebtSimplifier.transfers(balances)

        val case = "$groupCurrency group of $members with $bills, paid back $paidBack"
        assertEquals(case, members, balances.keys.toList())
        assertEquals(case, 0L, balances.values.sum())
        assertEquals(case, balances, Balances.of(members.shuffled(random), bills.shuffled(random), paidBack.shuffled(random)))
        assertTrue(case, transfers.size <= members.size - 1)
        assertTrue(case, transfers.all { it.amountMinor > 0 && it.fromMemberId != it.toMemberId })
        val after = balances.toMutableMap()
        for (transfer in transfers) {
            after.merge(transfer.fromMemberId, transfer.amountMinor, Long::plus)
            after.merge(transfer.toMemberId, -transfer.amountMinor, Long::plus)
        }
        assertTrue("$case left $after after $transfers", after.values.all { it == 0L })
    }

    /** A bill in a random currency, paid by and shared between random members. */
    private fun randomBill(members: List<String>): Pair<BillCurrency, GroupAmounts> {
        val currency = currencies.random(random)
        val total = when (random.nextInt(4)) {
            0 -> random.nextLong(0, 100)
            else -> random.nextLong(0, 10_000_000)
        }
        return currency to GroupAmounts(total, partition(total, subsetOf(members)), partition(total, subsetOf(members)))
    }

    /**
     * [bill] in [groupCurrency] at a random rate, checked: the parts and the payments each add up to
     * the converted total, and none is a whole minor unit or more away from its exact value.
     */
    private fun converted(bill: Pair<BillCurrency, GroupAmounts>, groupCurrency: BillCurrency, members: List<String>): GroupAmounts {
        val (currency, amounts) = bill
        val rate = ExchangeRate(currency.code, groupCurrency.code, BigDecimal.valueOf(random.nextLong(1, 400_000_000), 4))
        val result = checkNotNull(GroupConversion.convert(amounts, currency, groupCurrency, rate))

        val case = "$amounts in ${currency.code} to ${groupCurrency.code} at ${rate.rate}: $result"
        val total = if (currency == groupCurrency) amounts.totalMinor else rate.convert(amounts.totalMinor, currency, groupCurrency)
        assertEquals(case, total, result.totalMinor)
        assertTrue(case, result.isValidFor(members.toSet()))
        assertEquals(case, amounts.shares.keys.toList(), result.shares.keys.toList())
        assertEquals(case, amounts.paid.keys.toList(), result.paid.keys.toList())
        if (amounts.totalMinor > 0) {
            assertWithinOneUnit(case, amounts.shares, result.shares, amounts.totalMinor, result.totalMinor)
            assertWithinOneUnit(case, amounts.paid, result.paid, amounts.totalMinor, result.totalMinor)
        } else {
            assertEquals(case, 0L, result.totalMinor)
        }
        return result
    }

    /** Each converted part differs from part × converted total ÷ total by less than one minor unit. */
    private fun assertWithinOneUnit(case: String, before: Map<String, Long>, after: Map<String, Long>, total: Long, convertedTotal: Long) {
        before.forEach { (member, part) ->
            val exact = BigInteger.valueOf(part) * BigInteger.valueOf(convertedTotal)
            val given = BigInteger.valueOf(checkNotNull(after[member])) * BigInteger.valueOf(total)
            assertTrue("$case: $member", (given - exact).abs() < BigInteger.valueOf(total))
        }
    }

    /** At least one of [members], in their order. */
    private fun subsetOf(members: List<String>): List<String> =
        members.filter { random.nextBoolean() }.ifEmpty { listOf(members.random(random)) }

    /** [total] cut into one random amount, zero or more, for each of [members]. */
    private fun partition(total: Long, members: List<String>): Map<String, Long> {
        val cuts = (List(members.size - 1) { random.nextLong(0, total + 1) } + listOf(0L, total)).sorted()
        return members.withIndex().associateTo(LinkedHashMap()) { (i, member) -> member to cuts[i + 1] - cuts[i] }
    }
}
