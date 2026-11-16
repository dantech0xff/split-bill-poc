package com.dantech.splitvibe.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillCategoryTest {

    @Test
    fun `the categories are the nine the proxy and the app agreed on, with their names on screen`() {
        val agreed = listOf(
            "food_and_drink" to "Food & drink",
            "groceries" to "Groceries",
            "transport" to "Transport",
            "lodging" to "Lodging",
            "entertainment" to "Entertainment",
            "shopping" to "Shopping",
            "utilities" to "Utilities",
            "rent" to "Rent",
            "other" to "Other",
        )

        assertEquals(agreed, BillCategory.entries.map { it.wireValue to it.label })
        BillCategory.entries.forEach { category ->
            assertEquals(JsonPrimitive(category.wireValue), Json.encodeToJsonElement(BillCategory.serializer(), category))
            assertEquals(category, BillCategory.fromWire(category.wireValue))
        }
        assertNull(BillCategory.fromWire("Food & drink"))
        assertNull(BillCategory.fromWire("FOOD_AND_DRINK"))
    }

    @Test
    fun `a bill keeps its category, and a bill saved before there were categories is other`() {
        val bill = Bill.empty(BillCurrency("VND", 0)).copy(category = BillCategory.LODGING)
        val stored = Json.encodeToString(Bill.serializer(), bill)
        val older = """
            {"merchant": null, "date": null, "currency": {"code": "VND", "fractionDigits": 0},
             "items": [], "charges": [], "printedTotalMinor": null}
        """.trimIndent()

        assertEquals(JsonPrimitive("lodging"), Json.parseToJsonElement(stored).jsonObject["category"])
        assertEquals(bill, Json.decodeFromString(Bill.serializer(), stored))
        assertEquals(BillCategory.OTHER, Json.decodeFromString(Bill.serializer(), older).category)
        assertEquals(BillCategory.OTHER, Bill.empty(BillCurrency("USD", 2)).category)
    }
}
