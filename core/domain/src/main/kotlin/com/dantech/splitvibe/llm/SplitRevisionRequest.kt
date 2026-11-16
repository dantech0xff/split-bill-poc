package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.codePointLength
import com.dantech.splitvibe.domain.oneLine
import com.dantech.splitvibe.domain.wireName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The body of the proxy's `POST /v1/split-revision`, all but the `requestId` the app adds: the
 * user's instruction, and the bill and its split as they are now, in the shape the proxy and the app
 * agreed on. The bill goes as its lines and total, never as the text read from its photo.
 *
 * Names and labels go as the proxy takes them, the way bill links send them ([oneLine]): on one line
 * and at most [MAX_NAME_CODE_POINTS] long. A name that would show nothing goes empty, which the proxy
 * takes, and a merchant that would goes as none.
 */
object SplitRevisionRequest {

    /** The longest instruction the proxy takes, in code points, so an emoji counts once. */
    const val MAX_INSTRUCTION_CODE_POINTS = 500

    /** The name the user goes by in the request when they have not given one, so the model still knows who "I" is. */
    const val DEFAULT_ME_NAME = "Me"

    /** The longest merchant, item name, charge label or participant name the proxy takes, in code points. */
    const val MAX_NAME_CODE_POINTS = 200

    // Equal divisions keep their empty lists, as the agreed shape has them.
    private val json = Json { encodeDefaults = true }

    /**
     * What [typed] goes as: on one line, as the proxy takes no line break, tab or other control
     * character in an instruction. Each of them is a space and each run of spaces one, the invisible
     * formatting characters the proxy takes out go, and the ends are trimmed; empty when nothing is
     * left to send. The field it was typed in may hold several lines.
     */
    fun instructionOf(typed: String): String = typed.oneLine(Int.MAX_VALUE)

    /**
     * @param instruction what the user typed or said, which as it goes ([instructionOf]) is 1 to
     *   [MAX_INSTRUCTION_CODE_POINTS] code points.
     * @param meName the user's own name, sent as the name of the participant "me".
     * @throws IllegalArgumentException when nothing of [instruction] is left to send, or it is too long.
     */
    fun body(instruction: String, bill: Bill, plan: SplitPlan, meName: String): JsonObject {
        val text = instructionOf(instruction)
        require(text.codePointLength() in 1..MAX_INSTRUCTION_CODE_POINTS) {
            "An instruction is 1 to $MAX_INSTRUCTION_CODE_POINTS characters long"
        }
        val itemIds = bill.items.map { it.id }.toSet()
        return buildJsonObject {
            put("instruction", text)
            putJsonObject("bill") {
                put("merchant", bill.merchant?.let(::name)?.ifEmpty { null })
                putJsonObject("currency") {
                    put("code", bill.currency.code)
                    put("fractionDigits", bill.currency.fractionDigits)
                }
                putJsonArray("items") {
                    bill.items.forEach { item ->
                        addJsonObject {
                            put("id", item.id)
                            put("name", name(item.name))
                            put("quantity", item.quantity.toPlainString())
                            put("amountMinor", item.amountMinor)
                        }
                    }
                }
                putJsonArray("charges") {
                    bill.charges.forEach { charge ->
                        addJsonObject {
                            put("id", charge.id)
                            put("label", name(charge.label))
                            put("kind", charge.kind.wireName())
                            put("amountMinor", charge.amountMinor)
                        }
                    }
                }
                put("totalMinor", bill.totalMinor)
            }
            putJsonObject("split") {
                putJsonArray("participants") {
                    plan.participants.forEach { participant ->
                        addJsonObject {
                            put("id", participant.id)
                            put("name", if (participant.isMe) name(meName).ifEmpty { DEFAULT_ME_NAME } else name(participant.name))
                        }
                    }
                }
                put("whole", plan.setup.whole?.let(::division) ?: JsonNull)
                // An item with no entry is shared equally by everyone; one no longer on the bill has nothing to say.
                putJsonObject("items") {
                    plan.setup.items.filterKeys { it in itemIds }.forEach { (itemId, division) -> put(itemId, division(division)) }
                }
                putJsonArray("notSharing") { plan.setup.notSharing.forEach { add(it) } }
                putJsonArray("payments") {
                    plan.payments.forEach { payment ->
                        addJsonObject {
                            put("participantId", payment.participantId)
                            put("amountMinor", payment.amountMinor)
                        }
                    }
                }
            }
        }
    }

    /** [text] as the proxy takes a name or a label. */
    private fun name(text: String): String = text.oneLine(MAX_NAME_CODE_POINTS)

    /** [division] as the domain writes it everywhere: `{"type": "equal", "among": [...], "except": [...]}` and so on. */
    private fun division(division: Division): JsonElement = json.encodeToJsonElement(Division.serializer(), division)
}
