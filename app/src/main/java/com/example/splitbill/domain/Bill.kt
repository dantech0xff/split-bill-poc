package com.example.splitbill.domain

import java.math.BigDecimal
import java.util.Locale

enum class ChargeKind { TAX, SERVICE_CHARGE, TIP, DISCOUNT, ROUNDING, OTHER }

/** One purchased line. [amountMinor] is the full line total (quantity already applied). */
data class BillItem(
    val name: String,
    val quantity: BigDecimal,
    val unitPriceMinor: Long?,
    val amountMinor: Long,
)

/**
 * An amount added to (tax, service charge, tip) or subtracted from (discount, negative amount) the
 * item total. Charges are shared between people in proportion to what each person ordered.
 */
data class BillCharge(
    val label: String,
    val kind: ChargeKind,
    val amountMinor: Long,
)

/**
 * A bill that is ready to be split.
 *
 * Invariant: `itemsTotalMinor + chargesTotalMinor == totalMinor`. [BillReconciler] establishes it,
 * so the people's shares always add up to exactly [totalMinor].
 */
data class Bill(
    val merchant: String?,
    val date: String?,
    val currency: BillCurrency,
    val items: List<BillItem>,
    val charges: List<BillCharge>,
    val totalMinor: Long,
    /** Things the user should double-check, from the model or from reconciliation. */
    val warnings: List<String>,
) {
    val itemsTotalMinor: Long get() = items.sumOf { it.amountMinor }
    val chargesTotalMinor: Long get() = charges.sumOf { it.amountMinor }

    fun format(minor: Long): String = currency.format(minor)
}

/**
 * The note shown when a bill is not in the currency the user's payment app works in. The amounts
 * stay in the bill's currency: converting them would need an exchange rate the app does not have.
 */
fun Bill.currencyMismatchNote(localCurrency: String): String? {
    val local = localCurrency.trim().uppercase(Locale.ROOT)
    val billCurrency = currency.code
    if (local.isEmpty() || billCurrency.isEmpty() || billCurrency == local) return null
    return "This bill is in $billCurrency, but your app currency is $local. Amounts are shown in $billCurrency and are not converted."
}
