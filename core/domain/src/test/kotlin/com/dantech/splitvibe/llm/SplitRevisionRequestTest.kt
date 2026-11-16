package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.SplitPlan
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SplitRevisionRequestTest {

    /** The bill of the contract's example: three beers for 90,000 and a service charge of 14,000, on a printed total of 154,000. */
    private val bill = Bill(
        merchant = "Pho 24",
        date = "2026-11-16",
        currency = BillCurrency("VND", 0),
        items = listOf(BillItem("i1", "Bia", BigDecimal(3), 30_000, 90_000)),
        charges = listOf(BillCharge("c1", "Service charge", ChargeKind.SERVICE_CHARGE, 14_000)),
        printedTotalMinor = 154_000,
    )

    /** The user and Lan; the beer is the user's alone, and the user paid. */
    private val plan = SplitPlan()
        .withParticipantAdded("Lan")
        .withMode(SplitMode.BY_ITEM, bill)
        .withItemSharers("i1", setOf(ME_ID))
        .withBill(bill)

    @Test
    fun `the body is the contract's example, without the request id the app adds`() {
        val body = SplitRevisionRequest.body("  Lan không uống bia ", bill, plan, meName = "Dana")

        val contract = Json.parseToJsonElement(
            """
            {
              "instruction": "Lan không uống bia",
              "bill": {
                "merchant": "Pho 24",
                "currency": { "code": "VND", "fractionDigits": 0 },
                "items": [{ "id": "i1", "name": "Bia", "quantity": "3", "amountMinor": 90000 }],
                "charges": [{ "id": "c1", "label": "Service charge", "kind": "service_charge", "amountMinor": 14000 }],
                "totalMinor": 154000
              },
              "split": {
                "participants": [{ "id": "me", "name": "Dana" }, { "id": "p1", "name": "Lan" }],
                "whole": null,
                "items": { "i1": { "type": "equal", "among": ["me"], "except": [] } },
                "notSharing": [],
                "payments": [{ "participantId": "me", "amountMinor": 154000 }]
              }
            }
            """.trimIndent(),
        )
        assertEquals(contract, body)
    }

    @Test
    fun `a whole-bill split goes in the shape the domain writes every division in`() {
        fun whole(division: Division): JsonElement? {
            val body = SplitRevisionRequest.body("x", bill, plan.withWholeDivision(division), "Dana")
            return body["split"]?.jsonObject?.get("whole")
        }

        assertEquals(
            Json.parseToJsonElement("""{"type": "equal", "among": [], "except": ["p1"]}"""),
            whole(Division.Equally(except = setOf("p1"))),
        )
        assertEquals(
            Json.parseToJsonElement("""{"type": "shares", "shares": {"me": 2, "p1": 1}}"""),
            whole(Division.ByShares(mapOf(ME_ID to 2L, "p1" to 1L))),
        )
        assertEquals(
            Json.parseToJsonElement("""{"type": "percent", "basisPoints": {"me": 6000, "p1": 4000}}"""),
            whole(Division.ByPercent(mapOf(ME_ID to 6_000L, "p1" to 4_000L))),
        )
        assertEquals(
            Json.parseToJsonElement("""{"type": "amount", "amounts": {"me": 100000, "p1": 54000}}"""),
            whole(Division.ByAmount(mapOf(ME_ID to 100_000L, "p1" to 54_000L))),
        )
    }

    @Test
    fun `while the whole bill is divided, the divisions the screen remembers for its items are not sent`() {
        val body = SplitRevisionRequest.body("x", bill, plan.withMode(SplitMode.EQUAL, bill), "Dana")

        val split = body["split"]!!.jsonObject
        assertEquals(Json.parseToJsonElement("""{"type": "equal", "among": [], "except": []}"""), split["whole"])
        assertEquals(JsonObject(emptyMap()), split["items"])
    }

    @Test
    fun `people who only take part, several payers and a bill without a merchant go as they are`() {
        // Typed in by hand: no merchant and no printed total, so the lines make the total of 179,000.
        val typed = bill.copy(
            merchant = null,
            items = bill.items + BillItem("i2", "Pho", BigDecimal("1.5"), null, 75_000),
            printedTotalMinor = null,
        )
        val split = plan.withBill(typed)
            .withParticipantAdded("Khoa")
            .withSharing("p2", sharing = false)
            .withPayments(listOf(Payment(ME_ID, 100_000), Payment("p2", 79_000)))
            .withItemDivision("i9", Division.ByShares(mapOf(ME_ID to 1L)))

        val body = SplitRevisionRequest.body("Khoa paid the rest", typed, split, meName = " ")

        val sent = body["split"]!!.jsonObject
        assertEquals(JsonNull, body["bill"]!!.jsonObject["merchant"])
        assertEquals(JsonPrimitive(179_000), body["bill"]!!.jsonObject["totalMinor"])
        assertEquals(JsonPrimitive("1.5"), body["bill"]!!.jsonObject["items"]!!.jsonArray[1].jsonObject["quantity"])
        assertEquals(JsonPrimitive(SplitRevisionRequest.DEFAULT_ME_NAME), sent["participants"]!!.jsonArray[0].jsonObject["name"])
        assertEquals(Json.parseToJsonElement("""["p2"]"""), sent["notSharing"])
        assertEquals(
            Json.parseToJsonElement("""[{"participantId": "me", "amountMinor": 100000}, {"participantId": "p2", "amountMinor": 79000}]"""),
            sent["payments"],
        )
        // A division of an item that is no longer on the bill says nothing about it.
        assertEquals(setOf("i1"), sent["items"]!!.jsonObject.keys)
    }

    @Test
    fun `names, labels and the merchant go as the proxy takes them, as bill links send them`() {
        val smile = "\uD83D\uDE00"
        // A receipt line printed over two lines, a tab and a reversal of the text, and a merchant that shows nothing.
        val read = bill.copy(
            merchant = "\u200B\u2060",
            items = listOf(BillItem("i1", "Bia Saigon\nLon 330ml", BigDecimal(3), 30_000, 90_000)),
            charges = listOf(BillCharge("c1", "Service\tcharge\u202E", ChargeKind.SERVICE_CHARGE, 14_000)),
        )
        val people = plan.withParticipantAdded(smile.repeat(201)).withParticipantAdded("\uFEFF\u200D").withBill(read)

        val body = SplitRevisionRequest.body("x", read, people, meName = "Dana\n")

        val sent = body["bill"]!!.jsonObject
        assertEquals(JsonNull, sent["merchant"])
        assertEquals(JsonPrimitive("Bia Saigon Lon 330ml"), sent["items"]!!.jsonArray[0].jsonObject["name"])
        assertEquals(JsonPrimitive("Service charge"), sent["charges"]!!.jsonArray[0].jsonObject["label"])
        assertEquals(
            listOf("Dana", "Lan", smile.repeat(SplitRevisionRequest.MAX_NAME_CODE_POINTS), ""),
            body["split"]!!.jsonObject["participants"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
        val named = bill.copy(merchant = "M".repeat(250))
        assertEquals(
            JsonPrimitive("M".repeat(SplitRevisionRequest.MAX_NAME_CODE_POINTS)),
            SplitRevisionRequest.body("x", named, plan, "Dana")["bill"]!!.jsonObject["merchant"],
        )
    }

    @Test
    fun `an instruction goes on one line, as the proxy takes no line break or tab in it`() {
        val body = SplitRevisionRequest.body("Lan didn't have beer\n\tand add  a 10% tip \r\n", bill, plan, "Dana")

        assertEquals(JsonPrimitive("Lan didn't have beer and add a 10% tip"), body["instruction"])
        // Nothing but control and invisible characters is nothing to send.
        assertEquals("", SplitRevisionRequest.instructionOf("\u0007\u200B \n"))
        assertThrows(IllegalArgumentException::class.java) { SplitRevisionRequest.body("\u0007\u200B", bill, plan, "Dana") }
    }

    @Test
    fun `an instruction is one to five hundred characters, an emoji counting as one`() {
        val emoji = "🍺"

        SplitRevisionRequest.body(emoji.repeat(SplitRevisionRequest.MAX_INSTRUCTION_CODE_POINTS), bill, plan, "Dana")

        assertThrows(IllegalArgumentException::class.java) { SplitRevisionRequest.body(" \n ", bill, plan, "Dana") }
        assertThrows(IllegalArgumentException::class.java) {
            SplitRevisionRequest.body("a".repeat(SplitRevisionRequest.MAX_INSTRUCTION_CODE_POINTS + 1), bill, plan, "Dana")
        }
    }
}
