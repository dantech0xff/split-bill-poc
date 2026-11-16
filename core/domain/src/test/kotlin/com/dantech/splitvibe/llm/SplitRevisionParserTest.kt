package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.SplitEdit.AddCharge
import com.dantech.splitvibe.domain.SplitEdit.AddPerson
import com.dantech.splitvibe.domain.SplitEdit.ItemAmount
import com.dantech.splitvibe.domain.SplitEdit.ItemQuantity
import com.dantech.splitvibe.domain.SplitEdit.ItemSharers
import com.dantech.splitvibe.domain.SplitEdit.ItemShares
import com.dantech.splitvibe.domain.SplitEdit.RemovePerson
import com.dantech.splitvibe.domain.SplitEdit.SetMode
import com.dantech.splitvibe.domain.SplitEdit.SetPayer
import com.dantech.splitvibe.domain.SplitEdit.SetSharing
import com.dantech.splitvibe.domain.SplitEdit.WholeAmounts
import com.dantech.splitvibe.domain.SplitEdit.WholePercent
import com.dantech.splitvibe.domain.SplitEdit.WholeShares
import com.dantech.splitvibe.domain.SplitEdits
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.SplitPlan
import java.math.BigDecimal
import java.util.Locale
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitRevisionParserTest {

    private fun parse(text: String): SplitRevision = checkNotNull(SplitRevisionParser.parse(text)) { "no revision in $text" }

    /** An answer with [edits], written as JSON objects, and nothing else. */
    private fun edits(vararg edits: String): SplitRevision =
        parse("""{"edits": [${edits.joinToString(", ")}], "explanation": "", "question": null}""")

    @Test
    fun `the agreed answer is read as its edits, explanation and question`() {
        val revision = parse(
            """
            {
              "edits": [{ "op": "item_sharers", "itemId": "i1", "participantIds": ["me"] }],
              "explanation": "Lan no longer shares the beer.",
              "question": null
            }
            """.trimIndent(),
        )

        assertEquals(SplitRevision(listOf(ItemSharers("i1", listOf(ME_ID))), "Lan no longer shares the beer.", null, 0), revision)
    }

    @Test
    fun `every op of the contract is read with its fields`() {
        val revision = edits(
            """{"op": "item_sharers", "itemId": "i1", "participantIds": ["me", "p1"]}""",
            """{"op": "item_shares", "itemId": "i2", "shares": {"me": 2, "p1": 1}}""",
            """{"op": "split_mode", "mode": "by_percent"}""",
            """{"op": "whole_shares", "shares": {"me": 0, "p1": 3}}""",
            """{"op": "whole_percent", "basisPoints": {"me": 6000, "p1": 4000}}""",
            """{"op": "whole_amounts", "amounts": {"me": 100000, "p1": 54000}}""",
            """{"op": "add_person", "ref": "new1", "name": "Zoe"}""",
            """{"op": "remove_person", "participantId": "p1"}""",
            """{"op": "set_sharing", "participantId": "new1", "sharing": false}""",
            """{"op": "add_charge", "kind": "tip", "label": "Tip", "percentBasisPoints": 1000}""",
            """{"op": "add_charge", "kind": "service_charge", "label": "Service", "amountMinor": 15000, "percentBasisPoints": null}""",
            """{"op": "set_payer", "participantId": "new1"}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": 0}""",
            """{"op": "item_quantity", "itemId": "i1", "quantity": "2.5", "note": "a field nobody agreed on"}""",
        )

        assertEquals(
            listOf(
                ItemSharers("i1", listOf(ME_ID, "p1")),
                ItemShares("i2", mapOf(ME_ID to 2L, "p1" to 1L)),
                SetMode(SplitMode.BY_PERCENT),
                WholeShares(mapOf(ME_ID to 0L, "p1" to 3L)),
                WholePercent(mapOf(ME_ID to 6_000L, "p1" to 4_000L)),
                WholeAmounts(mapOf(ME_ID to 100_000L, "p1" to 54_000L)),
                AddPerson("new1", "Zoe"),
                RemovePerson("p1"),
                SetSharing("new1", false),
                AddCharge(ChargeKind.TIP, "Tip", percentBasisPoints = 1_000),
                AddCharge(ChargeKind.SERVICE_CHARGE, "Service", amountMinor = 15_000),
                SetPayer("new1"),
                ItemAmount("i1", 0),
                ItemQuantity("i1", BigDecimal("2.5")),
            ),
            revision.edits,
        )
        assertEquals(0, revision.unreadable)
    }

    @Test
    fun `an edit that cannot be read is left out and counted, and the others are kept in order`() {
        val revision = edits(
            """{"op": "set_payer", "participantId": "p1"}""",
            """{"op": "split_bill_by_vibes", "participantId": "p1"}""",
            """{"participantId": "p1"}""",
            """{"op": "set_payer"}""",
            """{"op": "item_sharers", "itemId": "i1", "participantIds": "me"}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": 1.5}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": "lots"}""",
            """{"op": "item_amount", "itemId": ["i1"], "amountMinor": 100}""",
            """{"op": "set_sharing", "participantId": "p1", "sharing": "maybe"}""",
            """{"op": "split_mode", "mode": "by_vibes"}""",
            """{"op": "add_charge", "kind": "bribe", "label": "Tip", "amountMinor": 100}""",
            """{"op": "item_quantity", "itemId": "i1", "quantity": "three"}""",
            """{"op": "item_quantity", "itemId": "i1", "quantity": 3}""",
            """{"op": {"nested": true}}""",
            """"set_payer"""",
            "null",
            "[1, 2]",
            """{"op": "SET_SHARING", "participantId": "p1", "sharing": true}""",
            """{"op": "split_mode", "mode": " BY_ITEM "}""",
        )

        assertEquals(listOf(SetPayer("p1"), SetMode(SplitMode.BY_ITEM)), revision.edits)
        assertEquals(17, revision.unreadable)
    }

    @Test
    fun `a number too large for its field is not read, and one that fits is left for the split to judge`() {
        val revision = edits(
            """{"op": "item_amount", "itemId": "i1", "amountMinor": 1e30}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": 99999999999999999999}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": -9223372036854775809}""",
            """{"op": "whole_amounts", "amounts": {"me": 1, "p1": 123456789012345678901234567890}}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": 9223372036854775807}""",
            """{"op": "whole_percent", "basisPoints": {"me": -5000, "p1": 15000}}""",
            """{"op": "item_quantity", "itemId": "i1", "quantity": "1E+999999999"}""",
        )

        assertEquals(
            listOf(
                ItemAmount("i1", Long.MAX_VALUE),
                WholePercent(mapOf(ME_ID to -5_000L, "p1" to 15_000L)),
                ItemQuantity("i1", BigDecimal("1E+999999999")),
            ),
            revision.edits,
        )
        assertEquals(4, revision.unreadable)
    }

    @Test
    fun `names, labels and sentences lose control and invisible characters and are cut to fit a screen`() {
        val family = "👩‍👩‍👧"
        val revision = parse(
            buildJsonObject {
                put(
                    "edits",
                    JsonArray(
                        listOf(
                            edit("add_person", "ref" to "new1", "name" to "  Zo\u0000e\n\tAnn  "),
                            edit("add_person", "ref" to "new2", "name" to "‮naz​"),
                            edit("add_person", "ref" to "new3", "name" to "$family Mum"),
                            edit("add_person", "ref" to "new4", "name" to "a".repeat(100)),
                            edit("add_person", "ref" to "new5", "name" to "\u0007\u0008"),
                            // Joiners alone show nothing, and a line separator breaks a line on screen.
                            edit("add_person", "ref" to "new6", "name" to "\u200D\u200C"),
                            edit("add_person", "ref" to "new7", "name" to "Lan\u2028Anh"),
                            edit("add_charge", "kind" to "tip", "label" to "Tip\r\n<b>", "amountMinor" to 100),
                        ),
                    ),
                )
                put("explanation", "Done.\n" + "x".repeat(300))
                put("question", " \u0000 ")
            }.toString(),
        )

        assertEquals(
            listOf("Zo e Ann", "naz", "$family Mum", "a".repeat(80), "", "", "Lan Anh"),
            revision.edits.filterIsInstance<AddPerson>().map { it.name },
        )
        assertEquals("Tip <b>", (revision.edits.last() as AddCharge).label)
        assertEquals(SplitRevisionParser.MAX_EXPLANATION, revision.explanation.length)
        assertTrue(revision.explanation.startsWith("Done. xxx"))
        assertNull(revision.question)
        assertEquals("", parse("""{"edits": [], "explanation": 42, "question": ["Who?"]}""").explanation)
    }

    @Test
    fun `an answer carries at most twenty edits, and the ones after them are counted as unreadable`() {
        val revision = edits(*Array(25) { """{"op": "set_payer", "participantId": "p$it"}""" })

        assertEquals((0 until 20).map { SetPayer("p$it") }, revision.edits)
        assertEquals(5, revision.unreadable)
    }

    @Test
    fun `an answer that is not an object is not a revision, and edits that are not a list are not read`() {
        listOf("", "Sorry, I cannot help with that.", "[]", "\"edits\"", "null", "42", "{\"edits\": [").forEach { text ->
            assertNull(text, SplitRevisionParser.parse(text))
        }
        assertEquals(
            SplitRevision(emptyList(), "", "Who is Lan?", 1),
            parse("""{"edits": "none", "question": "Who is Lan?"}"""),
        )
        assertEquals(
            SplitRevision(emptyList(), "", "Which beer, the Tiger or the Saigon?", 0),
            parse("""{"question": "Which beer, the Tiger or the Saigon?"}"""),
        )
    }

    @Test
    fun `no answer, however garbled, makes reading it throw, and whatever is read can be applied and described`() {
        val random = Random(20261116)
        val bill = contractBill()
        val plan = SplitPlan().withParticipantAdded("Lan").withBill(bill)
        repeat(3_000) {
            val elements = List(random.nextInt(0, 24)) { mutated(TEMPLATES.random(random), random) }
            val text = buildJsonObject {
                put("edits", if (random.nextInt(20) == 0) junk(random, 0) else JsonArray(elements))
                put("explanation", junk(random, 2))
                put("question", junk(random, 2))
            }.toString()

            val revision = checkNotNull(SplitRevisionParser.parse(text)) { text }
            val result = SplitEdits.apply(bill, plan, revision.edits)
            val sentences = SplitEdits.describe(revision.edits, bill, plan, "Dana", Locale.US)

            assertTrue(text, revision.edits.size <= SplitRevisionParser.MAX_EDITS)
            assertTrue(text, revision.explanation.codePointCount(0, revision.explanation.length) <= SplitRevisionParser.MAX_EXPLANATION)
            assertTrue(text, revision.edits.filterIsInstance<AddPerson>().all { person -> person.name.none { it.isISOControl() } })
            assertEquals(text, revision.edits.size, result.applied.size + result.rejected.size)
            assertEquals(text, revision.edits.size, sentences.size)
        }
    }

    @Test
    fun `the contract's example answer, applied to its example bill, leaves the beer to the user`() {
        val bill = contractBill()
        val plan = SplitPlan().withParticipantAdded("Lan").withBill(bill)
        val answer = """
            {"edits": [{"op": "item_sharers", "itemId": "i1", "participantIds": ["me"]}],
             "explanation": "Lan no longer shares the beer.", "question": null}
        """.trimIndent()

        val result = SplitEdits.apply(bill, plan, parse(answer).edits)

        // The beer is the only item, so the user now has all of it, and the service charge and the
        // 50,000 the lines are short of the printed total follow it.
        assertEquals(listOf(154_000L, 0L), result.plan.result(result.bill).shares.map { it.totalMinor })
        assertEquals(listOf(77_000L, 77_000L), plan.result(bill).shares.map { it.totalMinor })
    }

    /** The bill of the contract's example: three beers for 90,000 and a service charge of 14,000, on a printed total of 154,000. */
    private fun contractBill() = Bill(
        merchant = "Pho 24",
        date = null,
        currency = BillCurrency("VND", 0),
        items = listOf(BillItem("i1", "Bia", BigDecimal(3), 30_000, 90_000)),
        charges = listOf(BillCharge("c1", "Service charge", ChargeKind.SERVICE_CHARGE, 14_000)),
        printedTotalMinor = 154_000,
    )

    private fun edit(op: String, vararg fields: Pair<String, Any>): JsonObject = buildJsonObject {
        put("op", op)
        fields.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value)
                else -> error("unexpected $value")
            }
        }
    }

    /** [template] with a field taken out, changed to anything at all, or added, or as it is. */
    private fun mutated(template: JsonObject, random: Random): JsonElement {
        val fields = template.toMutableMap()
        repeat(random.nextInt(0, 3)) {
            when (random.nextInt(4)) {
                0 -> fields.keys.randomOrNull(random)?.let(fields::remove)
                1 -> fields.keys.randomOrNull(random)?.let { fields[it] = junk(random, 1) }
                2 -> fields[FIELDS.random(random)] = junk(random, 1)
                else -> Unit
            }
        }
        return if (random.nextInt(30) == 0) junk(random, 0) else JsonObject(fields)
    }

    /** Any JSON value at all, including numbers no field can hold and strings no screen should show. */
    private fun junk(random: Random, depth: Int): JsonElement = when (random.nextInt(if (depth > 2) 6 else 8)) {
        0 -> JsonNull
        1 -> JsonPrimitive(random.nextBoolean())
        2 -> JsonPrimitive(random.nextLong())
        3 -> JsonPrimitive(BigDecimal(listOf("1e400", "-1e-400", "0.5", "-0", "99999999999999999999", "1E+999999999").random(random)))
        4 -> JsonPrimitive(STRINGS.random(random))
        5 -> JsonPrimitive(random.nextDouble(-1e9, 1e9))
        6 -> JsonArray(List(random.nextInt(0, 4)) { junk(random, depth + 1) })
        else -> JsonObject(List(random.nextInt(0, 4)) { FIELDS.random(random) to junk(random, depth + 1) }.toMap())
    }

    private companion object {
        val TEMPLATES = listOf(
            """{"op": "item_sharers", "itemId": "i1", "participantIds": ["me", "p1"]}""",
            """{"op": "item_shares", "itemId": "i1", "shares": {"me": 2, "p1": 1}}""",
            """{"op": "split_mode", "mode": "by_amount"}""",
            """{"op": "whole_shares", "shares": {"me": 1, "p1": 3}}""",
            """{"op": "whole_percent", "basisPoints": {"me": 5000, "p1": 5000}}""",
            """{"op": "whole_amounts", "amounts": {"me": 100000, "p1": 54000}}""",
            """{"op": "add_person", "ref": "new1", "name": "Zoe"}""",
            """{"op": "remove_person", "participantId": "p1"}""",
            """{"op": "set_sharing", "participantId": "new1", "sharing": false}""",
            """{"op": "add_charge", "kind": "tip", "label": "Tip", "percentBasisPoints": 1000}""",
            """{"op": "add_charge", "kind": "discount", "label": "Voucher", "amountMinor": 15000}""",
            """{"op": "set_payer", "participantId": "new1"}""",
            """{"op": "item_amount", "itemId": "i1", "amountMinor": 120000}""",
            """{"op": "item_quantity", "itemId": "i1", "quantity": "4"}""",
        ).map { Json.parseToJsonElement(it).jsonObject }

        val FIELDS = listOf(
            "op", "itemId", "participantIds", "shares", "mode", "basisPoints", "amounts", "ref", "name",
            "participantId", "sharing", "kind", "label", "amountMinor", "percentBasisPoints", "quantity", "me", "p1",
        )

        val STRINGS = listOf(
            "", "me", "p1", "new1", "i1", "item_sharers", "add_person", "by_item", "tip", "3", "-2", "1e999999999",
            "\u0000‮‍", "\uD800", "Lan\nLan", "<script>", "x".repeat(1_000),
        )
    }
}
