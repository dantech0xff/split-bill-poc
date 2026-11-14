package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.BillReconciler
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.ItemAssignmentHint
import com.dantech.splitvibe.domain.ShareRule
import com.dantech.splitvibe.domain.SplitHint
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import java.math.BigDecimal
import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts the model's JSON answer (its shape is the schema in the AI proxy's `src/schema.ts`) into
 * the app's [BillAnalysis].
 *
 * The model's numbers are treated as readings of the bill, not as trusted arithmetic: amounts are
 * converted to integer minor units here and [BillReconciler] checks that they add up.
 */
object BillAnalysisParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param localCurrency the currency of the user's payment app. A bill whose currency could not
     *   be told is taken to be in it.
     */
    fun parse(text: String, localCurrency: String = ""): BillAnalysis {
        val answer = try {
            json.decodeFromString<BillAnswer>(text)
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw BillAnalyzerException(Kind.BAD_RESPONSE, "Claude's answer was not in the expected format. Please try again.", e)
        }
        return try {
            toAnalysis(answer, localCurrency)
        } catch (e: ArithmeticException) {
            throw BillAnalyzerException(Kind.BAD_RESPONSE, "Claude returned an amount that is not a plausible price. Please try again.", e)
        }
    }

    private fun toAnalysis(answer: BillAnswer, localCurrency: String): BillAnalysis {
        if (!answer.status.trim().equals("ok", ignoreCase = true)) {
            return BillAnalysis.Rejected(
                answer.message.trim().ifEmpty {
                    "This photo could not be read as a bill. Try a sharper, well-lit photo."
                },
            )
        }

        val amounts = answer.items.map { it.amount } + answer.charges.map { it.amount } + answer.total
        amounts.forEach(::requirePlausible)
        val reportedCode = answer.currency.trim()
        val code = if (reportedCode.isEmpty() ||
            reportedCode.equals(BillCurrency.NO_CURRENCY, ignoreCase = true)
        ) {
            localCurrency
        } else {
            reportedCode
        }
        val currency = BillCurrency.resolve(code, answer.amountDecimals, amounts)

        val items = answer.items.map { item ->
            BillItem(
                name = item.name.trim().ifEmpty { "Item" },
                quantity = item.quantity,
                unitPriceMinor = item.unitPrice?.also(::requirePlausible)?.let(currency::toMinor),
                amountMinor = currency.toMinor(item.amount),
            )
        }
        val charges = answer.charges.mapNotNull { charge ->
            val kind = chargeKind(charge.kind)
            val amount = currency.toMinor(charge.amount)
            // A discount always reduces the bill, whichever sign the model wrote it with.
            val signed = if (kind == ChargeKind.DISCOUNT && amount > 0) -amount else amount
            if (signed == 0L) null else BillCharge(charge.label.trim().ifEmpty { defaultLabel(kind) }, kind, signed)
        }

        val warnings = answer.warnings.map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        var total = currency.toMinor(answer.total)
        if (total <= 0) {
            val computed = items.sumOf { it.amountMinor } + charges.sumOf { it.amountMinor }
            if (computed <= 0) {
                return BillAnalysis.Rejected("No amounts could be read from this bill. Try a sharper, well-lit photo.")
            }
            total = computed
            warnings += "The bill total could not be read, so the sum of the items is used instead."
        }

        val bill = BillReconciler.reconcile(
            merchant = answer.merchant.trim().ifEmpty { null },
            date = answer.date.trim().ifEmpty { null },
            currency = currency,
            items = items,
            charges = charges,
            totalMinor = total,
            warnings = warnings,
        )
        return BillAnalysis.Ready(bill, splitHint(answer.split, items.size), answer.message.trim())
    }

    private fun splitHint(split: SplitAnswer, itemCount: Int): SplitHint = SplitHint(
        people = cleanNames(split.people),
        userIncluded = split.userIncluded,
        peopleCount = split.peopleCount?.takeIf { it in 1..MAX_PEOPLE },
        itemAssignments = split.itemAssignments
            .filter { it.itemIndex in 0 until itemCount }
            .map { ItemAssignmentHint(it.itemIndex, shareRule(it.rule), cleanNames(it.people), it.includesUser) }
            // A rule that names nobody says nothing about who shares the item.
            .filter { it.people.isNotEmpty() || it.includesUser },
    )

    private fun cleanNames(names: List<String>): List<String> = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun shareRule(value: String): ShareRule =
        if (value.trim().equals("all_except", ignoreCase = true)) ShareRule.ALL_EXCEPT else ShareRule.ONLY

    private fun chargeKind(value: String): ChargeKind = when (value.trim().lowercase(Locale.ROOT)) {
        "tax" -> ChargeKind.TAX
        "service_charge" -> ChargeKind.SERVICE_CHARGE
        "tip" -> ChargeKind.TIP
        "discount" -> ChargeKind.DISCOUNT
        "rounding" -> ChargeKind.ROUNDING
        else -> ChargeKind.OTHER
    }

    private fun defaultLabel(kind: ChargeKind): String = when (kind) {
        ChargeKind.TAX -> "Tax"
        ChargeKind.SERVICE_CHARGE -> "Service charge"
        ChargeKind.TIP -> "Tip"
        ChargeKind.DISCOUNT -> "Discount"
        ChargeKind.ROUNDING -> "Rounding"
        ChargeKind.OTHER -> "Other"
    }

    /** Rejects numbers no bill contains before they are scaled to minor units. */
    private fun requirePlausible(amount: BigDecimal) {
        if (amount.abs() >= MAX_AMOUNT) throw ArithmeticException("Amount out of range: $amount")
    }

    private const val MAX_PEOPLE = 100

    // Far above any real bill, and low enough that even the longest answer the model can produce
    // cannot add up to more than a Long holds once amounts are in minor units.
    private val MAX_AMOUNT = BigDecimal("1e12")
}

@Serializable
internal data class BillAnswer(
    val status: String,
    val merchant: String = "",
    val date: String = "",
    val currency: String = "",
    @SerialName("amount_decimals") val amountDecimals: Int? = null,
    val items: List<ItemAnswer> = emptyList(),
    val charges: List<ChargeAnswer> = emptyList(),
    @Serializable(with = JsonNumberAsBigDecimal::class) val total: BigDecimal = BigDecimal.ZERO,
    val split: SplitAnswer = SplitAnswer(),
    val warnings: List<String> = emptyList(),
    val message: String = "",
)

@Serializable
internal data class ItemAnswer(
    val name: String,
    @Serializable(with = JsonNumberAsBigDecimal::class) val quantity: BigDecimal = BigDecimal.ONE,
    @SerialName("unit_price") @Serializable(with = JsonNumberAsBigDecimal::class) val unitPrice: BigDecimal? = null,
    @Serializable(with = JsonNumberAsBigDecimal::class) val amount: BigDecimal,
)

@Serializable
internal data class ChargeAnswer(
    val label: String = "",
    val kind: String = "other",
    @Serializable(with = JsonNumberAsBigDecimal::class) val amount: BigDecimal,
)

@Serializable
internal data class SplitAnswer(
    val people: List<String> = emptyList(),
    @SerialName("user_included") val userIncluded: Boolean = true,
    @SerialName("people_count") val peopleCount: Int? = null,
    @SerialName("item_assignments") val itemAssignments: List<ItemAssignmentAnswer> = emptyList(),
)

@Serializable
internal data class ItemAssignmentAnswer(
    @SerialName("item_index") val itemIndex: Int,
    val rule: String = "only",
    val people: List<String> = emptyList(),
    @SerialName("includes_user") val includesUser: Boolean = false,
)

/**
 * Reads a JSON number from its literal text ("12.50"), never through a Double, so prices keep
 * exactly the digits the model wrote.
 */
internal object JsonNumberAsBigDecimal : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("JsonNumberAsBigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val literal = if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content else decoder.decodeString()
        return literal.trim().toBigDecimalOrNull() ?: throw SerializationException("Not a number: $literal")
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
}
