package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.Serializable

/** What one person owes: their part of the items plus their part of the shared charges. */
@Serializable
data class PersonShare(val personId: String, val itemsMinor: Long, val chargesMinor: Long) {
    val totalMinor: Long get() = itemsMinor + chargesMinor
}

/**
 * The shares of a split, or what has to be fixed before there are any. A split either adds up to
 * the bill total or has no numbers at all: when [problems] is not empty, [shares] is empty.
 */
data class SplitResult(val shares: List<PersonShare>, val problems: List<String> = emptyList()) {
    val isComplete: Boolean get() = problems.isEmpty()
    val totalMinor: Long get() = shares.sumOf { it.totalMinor }
    fun shareOf(personId: String): PersonShare? = shares.firstOrNull { it.personId == personId }
}

/**
 * Splits a [Bill] between people using integer arithmetic only.
 *
 * Rules:
 * - The shares always add up to exactly the bill total, in whole minor units.
 * - Divided equally, the minor units that cannot be divided go to people in list order; put the
 *   payer first to let them absorb them. Divided in proportion, they go by the largest remainder.
 * - Item by item, items divided equally between exactly the same people are added up and divided
 *   once, so two people who shared the same things never differ by more than one minor unit.
 *   Charges (tax, service, tip, discounts, and any gap to the printed total) are shared in
 *   proportion to each person's items, and if nothing is assigned to specific people the whole
 *   bill is simply divided equally.
 * - Every participant gets a share, people who do not share the bill a share of zero.
 */
object SplitCalculator {

    /** Changes whenever the way shares are worked out changes, so that shares saved earlier can be told apart. */
    const val VERSION = 1

    /**
     * @param participants ids of everyone on the bill, in display order.
     */
    fun split(bill: Bill, participants: List<String>, setup: SplitSetup = SplitSetup()): SplitResult {
        val people = participants.distinct()
        val sharing = people.filter { it !in setup.notSharing }
        if (sharing.isEmpty()) return incomplete("Choose at least one person to share the bill.")
        return when (val whole = setup.whole) {
            null -> byItem(bill, people, sharing, setup.items)
            else -> wholeBill(bill, people, sharing, whole)
        }
    }

    private fun wholeBill(bill: Bill, people: List<String>, sharing: List<String>, division: Division): SplitResult {
        val totals = when (val parts = divide(bill.totalMinor, people, sharing, division, bill.currency)) {
            is Parts.Failed -> return incomplete(parts.problem)
            is Parts.Done -> parts.values
        }
        // The items part follows the same division, so that each share still reads as items plus charges.
        val items = if (division is Division.Equally) {
            (divide(bill.itemsTotalMinor, people, sharing, division, bill.currency) as Parts.Done).values
        } else {
            proportionalAmong(bill.itemsTotalMinor, weightsOf(division, people, sharing), people, sharing)
        }
        return SplitResult(people.indices.map { i -> PersonShare(people[i], items[i], totals[i] - items[i]) })
    }

    private fun byItem(bill: Bill, people: List<String>, sharing: List<String>, divisions: Map<String, Division>): SplitResult {
        val problems = mutableListOf<String>()
        val itemsByPerson = LongArray(people.size)
        val pools = LinkedHashMap<List<String>, Long>()
        var everythingShared = true

        for (item in bill.items) {
            val division = divisions[item.id] ?: Division.Equally()
            if (division is Division.Equally) {
                val sharers = sharersOf(division, sharing)
                if (sharers.size != sharing.size) everythingShared = false
                pools[sharers] = (pools[sharers] ?: 0L) + item.amountMinor
                continue
            }
            everythingShared = false
            when (val parts = divide(item.amountMinor, people, sharing, division, bill.currency)) {
                is Parts.Failed -> problems += "${item.name.ifBlank { "An item" }}: ${parts.problem}"
                is Parts.Done -> parts.values.forEachIndexed { i, part -> itemsByPerson[i] += part }
            }
        }
        if (problems.isNotEmpty()) return SplitResult(emptyList(), problems)
        if (everythingShared) return wholeBill(bill, people, sharing, Division.Equally())

        pools.forEach { (sharers, pooled) ->
            val parts = divideEqually(pooled, sharers.size)
            sharers.forEachIndexed { position, person -> itemsByPerson[people.indexOf(person)] += parts[position] }
        }
        val itemAmounts = itemsByPerson.toList()
        val chargeAmounts = proportionalAmong(bill.sharedChargesMinor, itemAmounts, people, sharing)
        return SplitResult(people.indices.map { i -> PersonShare(people[i], itemAmounts[i], chargeAmounts[i]) })
    }

    /** [amount] divided between [people] as [division] says, or why it cannot be. Only [sharing] get a part. */
    private fun divide(amount: Long, people: List<String>, sharing: List<String>, division: Division, currency: BillCurrency): Parts =
        when (division) {
            is Division.Equally -> {
                val sharers = sharersOf(division, sharing)
                val parts = divideEqually(amount, sharers.size)
                Parts.Done(people.map { person -> sharers.indexOf(person).let { if (it < 0) 0L else parts[it] } })
            }

            is Division.ByShares -> {
                val weights = weightsOf(division, people, sharing)
                when {
                    weights.any { it < 0 } -> Parts.Failed("A share cannot be less than zero.")
                    weights.all { it == 0L } -> Parts.Failed("Give at least one person a share.")
                    else -> Parts.Done(divideProportionally(amount, weights))
                }
            }

            is Division.ByPercent -> {
                val weights = weightsOf(division, people, sharing)
                val sum = weights.sum()
                when {
                    weights.any { it < 0 } -> Parts.Failed("A percentage cannot be less than zero.")

                    sum != Division.ByPercent.FULL -> Parts.Failed(
                        "Percentages add up to ${percentText(sum)}. They need to add up to 100%.",
                    )

                    else -> Parts.Done(divideProportionally(amount, weights))
                }
            }

            is Division.ByAmount -> {
                val values = weightsOf(division, people, sharing)
                val sum = values.sum()
                when {
                    values.any { it < 0 } -> Parts.Failed("An amount cannot be less than zero.")

                    sum < amount -> Parts.Failed(
                        "Amounts add up to ${currency.format(sum)}, ${currency.format(amount - sum)} short of ${currency.format(amount)}.",
                    )

                    sum > amount -> Parts.Failed(
                        "Amounts add up to ${currency.format(sum)}, ${currency.format(sum - amount)} more than ${currency.format(amount)}.",
                    )

                    else -> Parts.Done(values)
                }
            }
        }

    /** What each person counts for in [division]; people who do not share the bill count for nothing. */
    private fun weightsOf(division: Division, people: List<String>, sharing: List<String>): List<Long> {
        val sharingSet = sharing.toSet()
        return people.map { person ->
            if (person !in sharingSet) {
                0L
            } else {
                when (division) {
                    is Division.Equally -> if (person in sharersOf(division, sharing)) 1L else 0L
                    is Division.ByShares -> division.shares[person] ?: 0L
                    is Division.ByPercent -> division.basisPoints[person] ?: 0L
                    is Division.ByAmount -> division.amounts[person] ?: 0L
                }
            }
        }
    }

    /** Who shares an amount divided equally. Nobody left means everyone sharing the bill, so no amount goes unpaid. */
    private fun sharersOf(division: Division.Equally, sharing: List<String>): List<String> {
        val chosen = if (division.among.isEmpty()) sharing else sharing.filter { it in division.among }
        return chosen.filter { it !in division.except }.ifEmpty { sharing }
    }

    /** [amount] in proportion to [weights]; with nothing to be proportional to, equally between [sharing]. */
    private fun proportionalAmong(amount: Long, weights: List<Long>, people: List<String>, sharing: List<String>): List<Long> {
        val usable = weights.map { it.coerceAtLeast(0) }
        if (usable.any { it > 0 }) return divideProportionally(amount, usable)
        val parts = divideEqually(amount, sharing.size)
        return people.map { person -> sharing.indexOf(person).let { if (it < 0) 0L else parts[it] } }
    }

    private fun incomplete(problem: String) = SplitResult(emptyList(), listOf(problem))

    private sealed interface Parts {
        data class Done(val values: List<Long>) : Parts

        data class Failed(val problem: String) : Parts
    }

    /**
     * Divides [amount] into [parts] whole amounts that differ by at most one minor unit and sum to
     * exactly [amount]. The first parts receive the units that cannot be divided.
     */
    fun divideEqually(amount: Long, parts: Int): List<Long> {
        require(parts > 0) { "parts must be positive" }
        val sign = if (amount < 0) -1 else 1
        val magnitude = Math.abs(amount)
        val base = magnitude / parts
        val extras = (magnitude % parts).toInt()
        return List(parts) { position -> sign * (base + if (position < extras) 1 else 0) }
    }

    /**
     * Divides [amount] in proportion to [weights] (largest-remainder method) so the parts sum to
     * exactly [amount]. Falls back to an equal division when there is nothing to be proportional to.
     */
    fun divideProportionally(amount: Long, weights: List<Long>): List<Long> {
        require(weights.isNotEmpty()) { "weights must not be empty" }
        val usable = weights.map { it.coerceAtLeast(0) }
        val weightSum = usable.fold(BigInteger.ZERO) { acc, w -> acc + BigInteger.valueOf(w) }
        if (weightSum.signum() == 0) return divideEqually(amount, weights.size)

        val sign = if (amount < 0) -1 else 1
        val magnitude = BigInteger.valueOf(Math.abs(amount))

        val quotients = LongArray(usable.size)
        val remainders = Array(usable.size) { BigInteger.ZERO }
        usable.forEachIndexed { i, weight ->
            val (quotient, remainder) = (magnitude * BigInteger.valueOf(weight)).divideAndRemainder(weightSum)
            quotients[i] = quotient.longValueExact()
            remainders[i] = remainder
        }

        var leftover = (magnitude - BigInteger.valueOf(quotients.sum())).longValueExact()
        // Largest remainder first; ties keep list order.
        val order = usable.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
        for (i in order) {
            if (leftover == 0L) break
            quotients[i] += 1
            leftover -= 1
        }
        return quotients.map { sign * it }
    }
}

/** Hundredths of a percent as people write a percentage: "10%", "33.33%", "0.5%". */
internal fun percentText(basisPoints: Long): String = BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%"
