package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.SplitEdit
import com.dantech.splitvibe.domain.SplitEdits
import com.dantech.splitvibe.domain.oneLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The AI's answer to a request to change a split by a sentence: the edits it proposes, in order, and what it says about them. */
data class SplitRevision(
    val edits: List<SplitEdit>,
    /** One short sentence on what the edits do, in the language of the instruction; empty when there is none. */
    val explanation: String,
    /** A short question back to the user when the instruction was too unclear to act on, when the edits are empty; null otherwise. */
    val question: String?,
    /** How many of the proposed edits could not be read and were left out. */
    val unreadable: Int,
)

/**
 * Reads the final answer of the proxy's `POST /v1/split-revision`:
 * `{"edits": [...], "explanation": "...", "question": null}`.
 *
 * The answer comes from a model, so none of it is trusted. An edit with an unknown op, a field that
 * is missing or of the wrong type, or a number too large for its field is left out and counted,
 * never thrown. Names, labels and sentences lose their control characters and the invisible
 * characters that could hide or reorder what they show, and are cut to a length a screen can hold.
 * Whether an edit makes sense for the bill is for [SplitEdits.apply] to decide.
 */
object SplitRevisionParser {

    /** The most edits an answer may carry; any after them are left out and counted as unreadable. */
    const val MAX_EDITS = 20

    /** The longest explanation, in code points. */
    const val MAX_EXPLANATION = 200

    private const val MAX_QUESTION = 500
    private const val MAX_NAME = 80

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "op"
    }

    /** The revision [text] holds, or null when [text] is not a JSON object at all, or nests deeper than any answer ([JsonNesting]). */
    fun parse(text: String): SplitRevision? {
        if (!JsonNesting.isWithin(text)) return null
        val answer = try {
            json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) { // includes SerializationException
            return null
        } as? JsonObject ?: return null

        val listed = answer["edits"]
        val elements = (listed as? JsonArray).orEmpty()
        val edits = elements.take(MAX_EDITS).mapNotNull(::edit)
        // Edits that are not a list at all count as one that could not be read.
        val notAList = if (listed == null || listed is JsonNull || listed is JsonArray) 0 else 1
        return SplitRevision(
            edits = edits,
            explanation = text(answer["explanation"], MAX_EXPLANATION).orEmpty(),
            question = text(answer["question"], MAX_QUESTION),
            unreadable = elements.size - edits.size + notAList,
        )
    }

    private fun edit(element: JsonElement): SplitEdit? {
        val edit = try {
            json.decodeFromJsonElement(SplitEdit.serializer(), element)
        } catch (e: IllegalArgumentException) { // includes SerializationException and a number that does not parse
            return null
        }
        return when (edit) {
            is SplitEdit.AddPerson -> edit.copy(name = line(edit.name, MAX_NAME))
            is SplitEdit.AddCharge -> edit.copy(label = line(edit.label, MAX_NAME))
            else -> edit
        }
    }

    /** The text of a JSON string, on one line and at most [max] code points long; null for anything else or for nothing left. */
    private fun text(element: JsonElement?, max: Int): String? {
        val primitive = element as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return line(primitive.content, max).ifEmpty { null }
    }

    /**
     * [text] on one line, as the proxy takes a name ([oneLine]): control characters and line breaks
     * become spaces and runs of spaces one; invisible formatting characters, such as those that
     * reverse the direction of text, go, while the zero-width joiner and non-joiner that emoji and
     * some scripts need stay; at most [max] code points, so no emoji is cut in half; and empty when it
     * would show nothing. What a model wrote is shown on the phone too, so every other space or line
     * break a screen would show becomes a plain space as well, and halves of broken characters go.
     */
    private fun line(text: String, max: Int): String {
        val spaced = StringBuilder(text.length)
        text.codePoints().forEach { point ->
            when {
                Character.isWhitespace(point) -> spaced.append(' ')
                Character.getType(point) == Character.SURROGATE.toInt() -> Unit
                else -> spaced.appendCodePoint(point)
            }
        }
        return spaced.toString().oneLine(max)
    }
}
