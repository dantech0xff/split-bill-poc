package com.dantech.splitvibe.domain

/** Money that goes, or went, from one member of a group to another, in the group's currency. */
data class Transfer(val fromMemberId: String, val toMemberId: String, val amountMinor: Long)

/**
 * Who owes what in a group, in the group's currency.
 *
 * A member's balance is what they paid for the group's bills, minus their parts of those bills, plus
 * what they paid other members back, minus what other members paid them back. It is positive when
 * the group owes them and negative when they owe the group. Every bill's parts and payments add up
 * to its total, and every payment back leaves one member and reaches another, so the balances of a
 * group always add up to zero. Changing a bill after people have paid back leaves those payments as
 * they are: the difference simply shows up as new balances.
 */
object Balances {

    /**
     * The balance of each of [members], in their order, from [bills] in the group's currency and the
     * confirmed payments back ([settlements]). An id that a bill or a payment names but [members]
     * does not gets a balance too, after the others, so the balances still add up to zero.
     *
     * @throws ArithmeticException when the amounts are too large to add up, which only amounts far
     *   beyond any real group can cause.
     */
    fun of(members: List<String>, bills: List<GroupAmounts>, settlements: List<Transfer>): Map<String, Long> {
        val balances = LinkedHashMap<String, Long>()
        members.forEach { balances[it] = 0L }
        fun add(member: String, amount: Long) = balances.merge(member, amount, Math::addExact)
        for (bill in bills) {
            bill.paid.forEach { (member, amount) -> add(member, amount) }
            bill.shares.forEach { (member, amount) -> add(member, Math.negateExact(amount)) }
        }
        for (settlement in settlements) {
            add(settlement.fromMemberId, settlement.amountMinor)
            add(settlement.toMemberId, Math.negateExact(settlement.amountMinor))
        }
        return balances
    }
}
