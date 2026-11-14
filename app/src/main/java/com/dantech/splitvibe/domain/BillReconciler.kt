package com.dantech.splitvibe.domain

/**
 * Makes the extracted numbers internally consistent before anything is split.
 *
 * The model reads the bill; this code does the arithmetic.
 *
 * - If the items and charges the model found do not add up to the total on the bill, the printed
 *   total wins (it is what was actually paid) and the gap becomes an explicit, visible line instead
 *   of silently distorting someone's share.
 * - A line with a negative amount (a voucher, a void, a return) is treated as a deduction on the
 *   whole bill. Left as an item it could give someone a negative share that nobody would ever
 *   settle; as a deduction it lowers everyone's share in proportion.
 */
object BillReconciler {

    const val DIFFERENCE_LABEL = "Unallocated difference"

    fun reconcile(
        merchant: String?,
        date: String?,
        currency: BillCurrency,
        items: List<BillItem>,
        charges: List<BillCharge>,
        totalMinor: Long,
        warnings: List<String>,
    ): Bill {
        val (purchases, deductions) = items.partition { it.amountMinor >= 0 }
        val allCharges = charges + deductions.map { BillCharge(it.name, ChargeKind.DISCOUNT, it.amountMinor) }

        val computed = purchases.sumOf { it.amountMinor } + allCharges.sumOf { it.amountMinor }
        val difference = totalMinor - computed
        if (difference == 0L) {
            return Bill(merchant, date, currency, purchases, allCharges, totalMinor, warnings)
        }

        val note = if (purchases.isEmpty()) {
            "No line items could be read, so only an equal split of the total is possible."
        } else {
            "Items and charges add up to ${currency.format(computed)}, but the bill total is " +
                "${currency.format(totalMinor)}. The difference of ${currency.format(difference)} " +
                "is shared in proportion. Please check the items."
        }
        return Bill(
            merchant = merchant,
            date = date,
            currency = currency,
            items = purchases,
            charges = allCharges + BillCharge(DIFFERENCE_LABEL, ChargeKind.OTHER, difference),
            totalMinor = totalMinor,
            warnings = warnings + note,
        )
    }
}
