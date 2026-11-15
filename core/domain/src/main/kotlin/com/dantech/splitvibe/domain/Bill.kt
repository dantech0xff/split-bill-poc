package com.dantech.splitvibe.domain

import java.math.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class ChargeKind { TAX, SERVICE_CHARGE, TIP, DISCOUNT, ROUNDING, OTHER }

/**
 * One purchased line. [amountMinor] is the full line total (quantity already applied) and is never
 * negative: a negative line is a deduction and lives among the charges. [id] stays the same while
 * the bill is edited, so splits and later the people who claim an item can refer to it.
 */
@Serializable
data class BillItem(
    val id: String,
    val name: String,
    @Serializable(with = BigDecimalAsString::class) val quantity: BigDecimal,
    val unitPriceMinor: Long?,
    val amountMinor: Long,
)

/**
 * An amount added to (tax, service charge, tip) or subtracted from (discount, negative amount) the
 * item total. Charges are shared between people in proportion to what each person ordered.
 */
@Serializable
data class BillCharge(val id: String, val label: String, val kind: ChargeKind, val amountMinor: Long)

/**
 * A bill that is ready to be split.
 *
 * [printedTotalMinor] is the total printed on the bill, which is what was paid. When the lines do
 * not add up to it, the gap ([differenceMinor]) is shared in proportion like a charge, so the
 * people's shares always add up to exactly [totalMinor]. A bill entered by hand may have no printed
 * total; its lines then make the total.
 */
@Serializable
data class Bill(
    val merchant: String?,
    val date: String?,
    val currency: BillCurrency,
    val items: List<BillItem>,
    val charges: List<BillCharge>,
    val printedTotalMinor: Long?,
    /** Things the user should double-check, as the model reported them. */
    val warnings: List<String> = emptyList(),
) {
    val itemsTotalMinor: Long get() = items.sumOf { it.amountMinor }
    val chargesTotalMinor: Long get() = charges.sumOf { it.amountMinor }
    val linesTotalMinor: Long get() = itemsTotalMinor + chargesTotalMinor

    /** What is paid, and what the shares add up to. */
    val totalMinor: Long get() = printedTotalMinor ?: linesTotalMinor

    /** The printed total minus what the lines add up to; zero when they agree or there is no printed total. */
    val differenceMinor: Long get() = totalMinor - linesTotalMinor

    /** Everything that is not an item, which is shared in proportion to the items: charges and any difference. */
    val sharedChargesMinor: Long get() = totalMinor - itemsTotalMinor

    fun format(minor: Long): String = currency.format(minor)

    /** Tells the user that the lines and the printed total disagree, and by how much; null when they agree. */
    fun differenceNote(): String? {
        if (differenceMinor == 0L) return null
        if (items.isEmpty()) return "No line items could be read, so only an equal split of the total is possible."
        return "Items and charges add up to ${format(linesTotalMinor)}, but the bill total is ${format(totalMinor)}. " +
            "The difference of ${format(differenceMinor)} is shared in proportion. Please check the items."
    }

    companion object {
        /** A new bill with nothing on it yet, to be entered by hand. */
        fun empty(currency: BillCurrency): Bill = Bill(null, null, currency, emptyList(), emptyList(), printedTotalMinor = null)
    }
}

/** Keeps a decimal exactly as written ("1.5"), never through a floating-point number. */
object BigDecimalAsString : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimalAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}
