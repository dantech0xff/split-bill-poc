package com.dantech.splitvibe.domain

/**
 * Turns the lines read from a bill into a [Bill] that can be split.
 *
 * A line with a negative amount (a voucher, a void, a return) becomes a deduction on the whole
 * bill. Left as an item it could give someone a negative share that nobody would ever settle; as a
 * deduction it lowers everyone's share in proportion. Lines that do not add up to the printed total
 * are left as they are: the bill shows the difference and shares it in proportion until the user
 * fixes it.
 */
object BillReconciler {

    fun reconcile(
        merchant: String?,
        date: String?,
        currency: BillCurrency,
        items: List<BillItem>,
        charges: List<BillCharge>,
        printedTotalMinor: Long?,
        warnings: List<String>,
    ): Bill {
        val (purchases, deductions) = items.partition { it.amountMinor >= 0 }
        val moved = deductions.mapIndexed { index, line ->
            BillCharge(id = "c${charges.size + index + 1}", label = line.name, kind = ChargeKind.DISCOUNT, amountMinor = line.amountMinor)
        }
        return Bill(merchant, date, currency, purchases, charges + moved, printedTotalMinor, warnings)
    }
}
