package com.dantech.splitvibe.domain

import kotlinx.serialization.Serializable

/** What one participant paid towards the bill, in the bill's minor units. */
@Serializable
data class Payment(val participantId: String, val amountMinor: Long)

/**
 * Who paid the bill. The payments of a finished split add up to exactly the bill total, like the
 * shares do.
 */
object Payments {

    /** [payerId] paid the whole bill. */
    fun single(payerId: String, totalMinor: Long): List<Payment> = listOf(Payment(payerId, totalMinor))

    /**
     * The payments after the bill total changed to [totalMinor]. A single payer paid the whole bill,
     * whatever it is; amounts that several people entered are theirs and stay as they are.
     */
    fun followTotal(payments: List<Payment>, totalMinor: Long): List<Payment> =
        if (payments.size == 1) single(payments.single().participantId, totalMinor) else payments

    /**
     * The payments of the people who are still on the bill. With nobody left, [fallbackPayerId]
     * paid it all; with one payer left, that payer did, as a single payer always does.
     */
    fun withoutAbsent(payments: List<Payment>, present: Set<String>, fallbackPayerId: String, totalMinor: Long): List<Payment> {
        val kept = payments.filter { it.participantId in present }
        return when (kept.size) {
            0 -> single(fallbackPayerId, totalMinor)
            1 -> single(kept.single().participantId, totalMinor)
            else -> kept
        }
    }

    /** What is wrong with [payments] for a bill of [totalMinor], or null when they add up. */
    fun problem(payments: List<Payment>, totalMinor: Long, currency: BillCurrency): String? {
        if (payments.isEmpty()) return "Choose who paid the bill."
        if (payments.any { it.amountMinor < 0 }) return "A payment cannot be less than zero."
        val paid = payments.sumOf { it.amountMinor }
        if (paid == totalMinor) return null
        val gap = totalMinor - paid
        val given = "Payments add up to ${currency.format(paid)}"
        val total = "the bill total of ${currency.format(totalMinor)}"
        return if (gap > 0) "$given, ${currency.format(gap)} short of $total." else "$given, ${currency.format(-gap)} more than $total."
    }
}
