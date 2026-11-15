package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The changes a person can make to a bill: everything the model may have read wrong, and a whole
 * bill typed in by hand. Each change returns the new bill. The lines and the printed total may
 * disagree while the person edits; [Bill.differenceMinor] says by how much, and [withLinesAsTotal]
 * and [withDifferenceAsCharge] are the two ways to settle it.
 */
object BillEditor {

    /** The label of the line [withDifferenceAsCharge] adds. */
    const val ADJUSTMENT_LABEL = "Adjustment"

    fun withMerchant(bill: Bill, merchant: String): Bill = bill.copy(merchant = merchant.trim().ifEmpty { null })

    fun withDate(bill: Bill, date: String): Bill = bill.copy(date = date.trim().ifEmpty { null })

    /** Moves the bill to another currency, keeping every amount as written: 12.50 stays 12.50. */
    fun withCurrency(bill: Bill, currency: BillCurrency): Bill {
        if (currency == bill.currency) return bill
        fun convert(minor: Long): Long = currency.toMinor(bill.currency.toMajor(minor))
        return bill.copy(
            currency = currency,
            items = bill.items.map { it.copy(unitPriceMinor = it.unitPriceMinor?.let(::convert), amountMinor = convert(it.amountMinor)) },
            charges = bill.charges.map { it.copy(amountMinor = convert(it.amountMinor)) },
            printedTotalMinor = bill.printedTotalMinor?.let(::convert),
        )
    }

    /** Adds an item at the end. Without an amount, it is the quantity times the unit price. */
    fun withItemAdded(bill: Bill, name: String, quantity: BigDecimal = BigDecimal.ONE, unitPriceMinor: Long? = null, amountMinor: Long? = null): Bill {
        val amount = amountMinor ?: unitPriceMinor?.let { lineAmount(quantity, it) } ?: 0L
        val item = BillItem(nextId("i", bill.items.map { it.id }), name.trim(), quantity, unitPriceMinor, amount.coerceAtLeast(0))
        return bill.copy(items = bill.items + item)
    }

    fun withItemName(bill: Bill, itemId: String, name: String): Bill = updateItem(bill, itemId) { it.copy(name = name.trim()) }

    /** A new quantity; with a unit price, the line amount follows. */
    fun withItemQuantity(bill: Bill, itemId: String, quantity: BigDecimal): Bill = updateItem(bill, itemId) { item ->
        val positive = quantity.max(BigDecimal.ZERO)
        val amount = item.unitPriceMinor?.let { lineAmount(positive, it) } ?: item.amountMinor
        item.copy(quantity = positive, amountMinor = amount)
    }

    /** A new unit price, or none; with one, the line amount is the quantity times it. */
    fun withItemUnitPrice(bill: Bill, itemId: String, unitPriceMinor: Long?): Bill = updateItem(bill, itemId) { item ->
        val price = unitPriceMinor?.coerceAtLeast(0)
        item.copy(unitPriceMinor = price, amountMinor = price?.let { lineAmount(item.quantity, it) } ?: item.amountMinor)
    }

    /** A new line amount. A unit price that no longer agrees with it is dropped rather than kept wrong. */
    fun withItemAmount(bill: Bill, itemId: String, amountMinor: Long): Bill = updateItem(bill, itemId) { item ->
        val amount = amountMinor.coerceAtLeast(0)
        val price = item.unitPriceMinor?.takeIf { lineAmount(item.quantity, it) == amount }
        item.copy(unitPriceMinor = price, amountMinor = amount)
    }

    fun withoutItem(bill: Bill, itemId: String): Bill = bill.copy(items = bill.items.filter { it.id != itemId })

    /** Adds a charge at the end; [amountMinor] is signed, negative for a deduction (see [signedCharge]). */
    fun withChargeAdded(bill: Bill, label: String, kind: ChargeKind, amountMinor: Long): Bill {
        val charge = BillCharge(nextId("c", bill.charges.map { it.id }), label.trim().ifEmpty { defaultLabel(kind) }, kind, amountMinor)
        return bill.copy(charges = bill.charges + charge)
    }

    fun withCharge(bill: Bill, chargeId: String, label: String, kind: ChargeKind, amountMinor: Long): Bill = bill.copy(
        charges = bill.charges.map {
            if (it.id == chargeId) it.copy(label = label.trim().ifEmpty { defaultLabel(kind) }, kind = kind, amountMinor = amountMinor) else it
        },
    )

    fun withoutCharge(bill: Bill, chargeId: String): Bill = bill.copy(charges = bill.charges.filter { it.id != chargeId })

    /** The total printed on the bill, or none: the lines then make the total. */
    fun withPrintedTotal(bill: Bill, totalMinor: Long?): Bill = bill.copy(printedTotalMinor = totalMinor)

    /** Settles a difference by trusting the lines: there is no printed total any more, and the lines make the total. */
    fun withLinesAsTotal(bill: Bill): Bill = bill.copy(printedTotalMinor = null)

    /** Settles a difference by trusting the printed total: the difference becomes a line of its own that the user can see and edit. */
    fun withDifferenceAsCharge(bill: Bill): Bill {
        val difference = bill.differenceMinor
        if (difference == 0L) return bill
        return withChargeAdded(bill, ADJUSTMENT_LABEL, ChargeKind.OTHER, difference)
    }

    /**
     * A charge amount as the user typed it, signed the way the bill uses it: a discount always
     * lowers the bill, tax, service and tip always raise it, and rounding or anything else keeps
     * the sign the user gave it.
     */
    fun signedCharge(kind: ChargeKind, enteredMinor: Long): Long = when (kind) {
        ChargeKind.DISCOUNT -> -Math.abs(enteredMinor)
        ChargeKind.TAX, ChargeKind.SERVICE_CHARGE, ChargeKind.TIP -> Math.abs(enteredMinor)
        ChargeKind.ROUNDING, ChargeKind.OTHER -> enteredMinor
    }

    fun defaultLabel(kind: ChargeKind): String = when (kind) {
        ChargeKind.TAX -> "Tax"
        ChargeKind.SERVICE_CHARGE -> "Service charge"
        ChargeKind.TIP -> "Tip"
        ChargeKind.DISCOUNT -> "Discount"
        ChargeKind.ROUNDING -> "Rounding"
        ChargeKind.OTHER -> "Other"
    }

    /** The quantity times the unit price, to the nearest minor unit. */
    fun lineAmount(quantity: BigDecimal, unitPriceMinor: Long): Long =
        BigDecimal.valueOf(unitPriceMinor).multiply(quantity).setScale(0, RoundingMode.HALF_UP).longValueExact()

    private fun updateItem(bill: Bill, itemId: String, change: (BillItem) -> BillItem): Bill =
        bill.copy(items = bill.items.map { if (it.id == itemId) change(it) else it })

    /** The id after the highest one with [prefix], so an id is never handed out twice on one bill. */
    private fun nextId(prefix: String, ids: List<String>): String {
        val highest = ids.mapNotNull { if (it.startsWith(prefix)) it.drop(prefix.length).toIntOrNull() else null }.maxOrNull() ?: 0
        return "$prefix${highest + 1}"
    }
}
