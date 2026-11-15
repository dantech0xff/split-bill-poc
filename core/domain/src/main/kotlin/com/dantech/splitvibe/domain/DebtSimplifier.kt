package com.dantech.splitvibe.domain

/**
 * The simplest way to settle a group: the member who owes the most pays the member who is owed the
 * most, as much as one of them needs, until nobody owes anything. Every transfer leaves at least one
 * of the two at zero, so a group of n members never needs more than n − 1 transfers. Equal balances
 * are taken in the order the members come in, so the suggestion stays the same from one look to the
 * next.
 */
object DebtSimplifier {

    /** The transfers that bring every one of [balances] to zero; they must add up to zero. */
    fun transfers(balances: Map<String, Long>): List<Transfer> {
        require(balances.values.fold(0L, Math::addExact) == 0L) { "balances must add up to zero" }
        val members = balances.keys.toList()
        val left = balances.values.toLongArray()
        val transfers = mutableListOf<Transfer>()
        while (true) {
            // The first of equal amounts wins, which keeps the order of the members.
            val debtor = left.indices.filter { left[it] < 0 }.minByOrNull { left[it] } ?: break
            val creditor = left.indices.filter { left[it] > 0 }.maxByOrNull { left[it] } ?: break
            val amount = minOf(Math.negateExact(left[debtor]), left[creditor])
            transfers += Transfer(members[debtor], members[creditor], amount)
            left[debtor] += amount
            left[creditor] -= amount
        }
        return transfers
    }
}
