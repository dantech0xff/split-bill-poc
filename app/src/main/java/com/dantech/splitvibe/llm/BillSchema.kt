package com.dantech.splitvibe.llm

/**
 * The JSON Schema the model's answer must follow (Claude structured outputs).
 *
 * Kept within what structured outputs support: every object lists all of its keys as required and
 * forbids extra ones, there are no numeric or string length constraints, and only two fields are
 * nullable. [BillAnalysisParser] reads exactly this shape.
 */
internal object BillSchema {

    val SCHEMA: Map<String, Any> = obj(
        "status" to enumOf("ok", "not_a_bill", "unreadable"),
        "merchant" to string("Business name, or an empty string when it is not visible."),
        "date" to string("Bill date as YYYY-MM-DD, or an empty string when it is not visible."),
        "currency" to string("ISO 4217 code such as VND or USD."),
        "amount_decimals" to integer("Decimal places the bill prints its prices with: 0 for \"70.000 đ\" or \"Rp 33.000\", 2 for \"$12.50\"."),
        "items" to array(
            obj(
                "name" to string("Item name as printed."),
                "quantity" to number("Units bought; 1 when the bill does not show a quantity."),
                "unit_price" to nullable("number", "Price of one unit, or null when the bill does not show it."),
                "amount" to number("Line total for this item."),
            ),
        ),
        "charges" to array(
            obj(
                "label" to string("Label as printed, such as \"VAT 8%\"."),
                "kind" to enumOf("tax", "service_charge", "tip", "discount", "rounding", "other"),
                "amount" to number("Positive when it is added to the bill, negative when it is taken off."),
            ),
        ),
        "total" to number("Final amount to pay."),
        "split" to obj(
            "people" to array(string("Another person named in the user's instruction, as the user wrote it. Never the user.")),
            "user_included" to boolean("False only when the user says they are not taking part."),
            "people_count" to nullable("integer", "Total number of people including the user, or null when the user does not say."),
            "item_assignments" to array(
                obj(
                    "item_index" to integer("Zero-based position of the item in items."),
                    "rule" to enumOf("only", "all_except"),
                    "people" to array(string("Another person the rule names, as the user wrote the name. Never the user.")),
                    "includes_user" to boolean("True when the user is one of the people the rule names."),
                ),
            ),
        ),
        "warnings" to array(string("A short note about something the user should double-check.")),
        "message" to string("One short sentence for the user."),
    )

    private fun obj(vararg properties: Pair<String, Any>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to linkedMapOf(*properties),
        "required" to properties.map { it.first },
        "additionalProperties" to false,
    )

    private fun array(items: Map<String, Any>): Map<String, Any> = mapOf("type" to "array", "items" to items)

    private fun string(description: String): Map<String, Any> = mapOf("type" to "string", "description" to description)

    private fun number(description: String): Map<String, Any> = mapOf("type" to "number", "description" to description)

    private fun integer(description: String): Map<String, Any> = mapOf("type" to "integer", "description" to description)

    private fun boolean(description: String): Map<String, Any> = mapOf("type" to "boolean", "description" to description)

    private fun nullable(type: String, description: String): Map<String, Any> =
        mapOf("type" to listOf(type, "null"), "description" to description)

    private fun enumOf(vararg values: String): Map<String, Any> = mapOf("type" to "string", "enum" to values.toList())
}
