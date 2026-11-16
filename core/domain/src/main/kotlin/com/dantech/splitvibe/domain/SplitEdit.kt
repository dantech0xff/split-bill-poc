package com.dantech.splitvibe.domain

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

/**
 * One change to a split, as the AI proposes it from a sentence the user typed or said. The model
 * never says what anyone owes: it names edits, and [SplitEdits.apply] checks each one against the
 * bill and works the shares out again. Each edit is one `op` of the answer of the proxy's
 * `/v1/split-revision`, under the same name and with the same fields.
 *
 * Participants are named by their id on the bill ([ME_ID], "p1", and so on) or by the ref of someone
 * an earlier [AddPerson] of the same list adds ("new1" to "new9").
 */
@Serializable
sealed interface SplitEdit {

    /** The item is shared equally by exactly these people. */
    @Serializable
    @SerialName("item_sharers")
    data class ItemSharers(val itemId: String, val participantIds: List<String>) : SplitEdit

    /** The item is shared in proportion to whole-number shares of 1 or more; people without one do not share it. */
    @Serializable
    @SerialName("item_shares")
    data class ItemShares(val itemId: String, val shares: Map<String, Long>) : SplitEdit

    /**
     * The whole bill is split this way, starting from an even split, or the bill is split item by
     * item, each item shared by everyone to start with.
     */
    @Serializable
    @SerialName("split_mode")
    data class SetMode(@Serializable(with = SplitModeAsWire::class) val mode: SplitMode) : SplitEdit

    /** The whole bill is split in proportion to whole-number shares of 0 or more. */
    @Serializable
    @SerialName("whole_shares")
    data class WholeShares(val shares: Map<String, Long>) : SplitEdit

    /** The whole bill is split by percentages, in hundredths of a percent, that add up to exactly 100%. */
    @Serializable
    @SerialName("whole_percent")
    data class WholePercent(val basisPoints: Map<String, Long>) : SplitEdit

    /** The whole bill is split by exact amounts that add up to exactly the bill total. */
    @Serializable
    @SerialName("whole_amounts")
    data class WholeAmounts(val amounts: Map<String, Long>) : SplitEdit

    /** Someone new joins the bill; the edits after this one name them by [ref]. */
    @Serializable
    @SerialName("add_person")
    data class AddPerson(val ref: String, val name: String) : SplitEdit

    /** Takes someone other than the user off the bill. */
    @Serializable
    @SerialName("remove_person")
    data class RemovePerson(val participantId: String) : SplitEdit

    /** Whether someone shares the bill, or only takes part in it, for example as the one who paid. */
    @Serializable
    @SerialName("set_sharing")
    data class SetSharing(val participantId: String, val sharing: Boolean) : SplitEdit

    /**
     * Adds a charge of [amountMinor], or of [percentBasisPoints] of what the items add up to: exactly
     * one of the two. A discount lowers the bill; every other kind raises it.
     */
    @Serializable
    @SerialName("add_charge")
    data class AddCharge(
        @Serializable(with = ChargeKindAsWire::class) val kind: ChargeKind,
        val label: String,
        val amountMinor: Long? = null,
        val percentBasisPoints: Long? = null,
    ) : SplitEdit

    /** This participant paid the whole bill. */
    @Serializable
    @SerialName("set_payer")
    data class SetPayer(val participantId: String) : SplitEdit

    /** The item's line amount, its quantity already applied. */
    @Serializable
    @SerialName("item_amount")
    data class ItemAmount(val itemId: String, val amountMinor: Long) : SplitEdit

    /** The item's quantity; with a unit price, the line amount follows it. */
    @Serializable
    @SerialName("item_quantity")
    data class ItemQuantity(val itemId: String, @Serializable(with = BigDecimalAsString::class) val quantity: BigDecimal) : SplitEdit
}

/** The name an enum value has on the proxy's wire: its own name in lower case, such as "by_item" or "service_charge". */
internal fun Enum<*>.wireName(): String = name.lowercase(Locale.ROOT)

/** Reads and writes an enum by its [wireName], whatever the case it was written in. */
internal open class WireNameSerializer<E : Enum<E>>(serialName: String, private val values: List<E>) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.wireName())

    override fun deserialize(decoder: Decoder): E {
        val text = decoder.decodeString().trim().lowercase(Locale.ROOT)
        return values.firstOrNull { it.wireName() == text } ?: throw SerializationException("Not one of the agreed values")
    }
}

internal object SplitModeAsWire : WireNameSerializer<SplitMode>("SplitModeAsWire", SplitMode.entries)

internal object ChargeKindAsWire : WireNameSerializer<ChargeKind>("ChargeKindAsWire", ChargeKind.entries)
