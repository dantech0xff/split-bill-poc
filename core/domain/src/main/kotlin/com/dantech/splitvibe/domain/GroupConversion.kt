package com.dantech.splitvibe.domain

import java.math.BigDecimal

/**
 * One bill of a group: what each member's part of it is ([shares]) and what each member paid
 * towards it ([paid]), keyed by member id. Like the shares of any split, the parts and the payments
 * each add up to exactly [totalMinor].
 */
data class GroupAmounts(val totalMinor: Long, val shares: Map<String, Long>, val paid: Map<String, Long>) {

    /**
     * Whether these amounts can stand in a group of [members]: the total is neither negative nor
     * beyond what anyone can type, every part and payment lies between zero and the total, both add
     * up to the total, and everyone they name is a member. A bill that fails is left out of the
     * balances as a whole, which keeps them adding up to zero.
     */
    fun isValidFor(members: Set<String>): Boolean {
        if (totalMinor !in 0..BillCurrency.MAX_AMOUNT_MINOR) return false
        if (!members.containsAll(shares.keys) || !members.containsAll(paid.keys)) return false
        val amounts = shares.values + paid.values
        // Each amount is at most the total, so neither sum can overflow.
        return amounts.all { it in 0..totalMinor } && shares.values.sum() == totalMinor && paid.values.sum() == totalMinor
    }
}

/**
 * Brings a bill into a group, whose currency may not be the bill's.
 *
 * The bill total is converted once. The converted total is then divided in proportion to each
 * member's part, and separately in proportion to what each member paid, by the largest remainder.
 * So the converted parts and the converted payments each add up to exactly the converted total, and
 * no part ends up a whole minor unit or more away from its exact value.
 */
object GroupConversion {

    /**
     * A bill's [shares] and [payments], which belong to its participants, as amounts by group
     * member. [memberOf] gives the member each participant is; two participants who are the same
     * member are added together.
     */
    fun amountsOf(
        totalMinor: Long,
        shares: List<PersonShare>,
        payments: List<Payment>,
        memberOf: Map<String, String>,
    ): GroupAmounts {
        fun member(participantId: String) = requireNotNull(memberOf[participantId]) { "$participantId is not matched to a member" }
        val byMemberShares = LinkedHashMap<String, Long>()
        shares.forEach { byMemberShares.merge(member(it.personId), it.totalMinor, Math::addExact) }
        val byMemberPaid = LinkedHashMap<String, Long>()
        payments.forEach { byMemberPaid.merge(member(it.participantId), it.amountMinor, Math::addExact) }
        return GroupAmounts(totalMinor, byMemberShares, byMemberPaid)
    }

    /**
     * [amounts] of a bill in [from] as amounts in the group's currency [to], at [rate]: what one unit
     * of [from] buys in [to]. Two currencies with the same code need no rate. Null when two different
     * currencies come without a rate, or when the converted total is beyond what anyone can type,
     * which only a mistyped rate can cause.
     */
    fun convert(amounts: GroupAmounts, from: BillCurrency, to: BillCurrency, rate: ExchangeRate?): GroupAmounts? {
        if (from == to) return amounts
        val used = when {
            from.code == to.code -> ExchangeRate(from.code, to.code, BigDecimal.ONE)
            rate == null -> return null
            else -> rate
        }
        require(used.from == from.code && used.to == to.code) { "a ${used.from} to ${used.to} rate cannot convert ${from.code}" }
        val total = used.convert(amounts.totalMinor, from, to)?.takeIf { it in 0..BillCurrency.MAX_AMOUNT_MINOR } ?: return null
        return GroupAmounts(total, inProportion(total, amounts.shares), inProportion(total, amounts.paid))
    }

    private fun inProportion(total: Long, parts: Map<String, Long>): Map<String, Long> {
        if (parts.isEmpty()) return emptyMap()
        return parts.keys.zip(SplitCalculator.divideProportionally(total, parts.values.toList())).toMap()
    }
}
