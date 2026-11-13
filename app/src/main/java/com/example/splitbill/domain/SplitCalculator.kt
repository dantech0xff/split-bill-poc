package com.example.splitbill.domain

import java.math.BigInteger

/** What one person owes: their part of the items plus their part of the shared charges. */
data class PersonShare(
    val personId: String,
    val itemsMinor: Long,
    val chargesMinor: Long,
) {
    val totalMinor: Long get() = itemsMinor + chargesMinor
}

data class SplitResult(val shares: List<PersonShare>) {
    val totalMinor: Long get() = shares.sumOf { it.totalMinor }
    fun shareOf(personId: String): PersonShare? = shares.firstOrNull { it.personId == personId }
}

/**
 * Splits a [Bill] between people using integer arithmetic only.
 *
 * Rules:
 * - An item is shared equally by the people assigned to it. An item with no assignment is shared by
 *   everyone.
 * - Items shared by exactly the same group of people are added up and divided once, so two people
 *   who shared the same things never differ by more than the unit that cannot be divided.
 * - Charges (tax, service, tip, discounts) are shared in proportion to each person's items.
 * - If nothing is assigned to specific people, the whole bill is simply divided equally.
 * - Leftover minor units (the cent that cannot be divided) go to people in list order, so the
 *   shares always add up to exactly the bill total. Put the payer first to let them absorb it.
 */
object SplitCalculator {

    /**
     * @param participants ids of the people sharing the bill, in display order.
     * @param assignments item index to the ids sharing that item. Missing or empty means everyone.
     *   Ids that are not in [participants] are ignored.
     */
    fun split(
        bill: Bill,
        participants: List<String>,
        assignments: Map<Int, Set<String>> = emptyMap(),
    ): SplitResult {
        val people = participants.distinct()
        if (people.isEmpty()) return SplitResult(emptyList())

        val sharersPerItem = bill.items.indices.map { index ->
            val assigned = assignments[index].orEmpty()
            people.filter { it in assigned }.ifEmpty { people }
        }

        val everythingShared = sharersPerItem.all { it.size == people.size }
        if (everythingShared) return splitEqually(bill, people)

        val itemsByPerson = people.associateWith { 0L }.toMutableMap()
        bill.items.indices.groupBy { sharersPerItem[it] }.forEach { (sharers, itemIndices) ->
            val pooled = itemIndices.sumOf { bill.items[it].amountMinor }
            val parts = divideEqually(pooled, sharers.size)
            sharers.forEachIndexed { position, person ->
                itemsByPerson[person] = itemsByPerson.getValue(person) + parts[position]
            }
        }

        val itemAmounts = people.map { itemsByPerson.getValue(it) }
        val chargeAmounts = divideProportionally(bill.chargesTotalMinor, itemAmounts)
        return SplitResult(
            people.mapIndexed { i, person -> PersonShare(person, itemAmounts[i], chargeAmounts[i]) },
        )
    }

    private fun splitEqually(bill: Bill, people: List<String>): SplitResult {
        val totals = divideEqually(bill.totalMinor, people.size)
        val items = divideEqually(bill.itemsTotalMinor, people.size)
        return SplitResult(
            people.mapIndexed { i, person -> PersonShare(person, items[i], totals[i] - items[i]) },
        )
    }

    /**
     * Divides [amount] into [parts] whole amounts that differ by at most one minor unit and sum to
     * exactly [amount]. The first parts receive the units that cannot be divided.
     */
    internal fun divideEqually(amount: Long, parts: Int): List<Long> {
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
    internal fun divideProportionally(amount: Long, weights: List<Long>): List<Long> {
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
