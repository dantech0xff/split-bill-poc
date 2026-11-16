package com.dantech.splitvibe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a bill was spent on, from a fixed list so spending can be added up by it. The model suggests
 * one when it reads the bill, and the user can change it. A bill saved before bills had a category,
 * or read from an answer without a usable one, is [OTHER].
 */
@Serializable
enum class BillCategory(
    /** The name shown on screen. */
    val label: String,
) {
    @SerialName("food_and_drink")
    FOOD_AND_DRINK("Food & drink"),

    @SerialName("groceries")
    GROCERIES("Groceries"),

    @SerialName("transport")
    TRANSPORT("Transport"),

    @SerialName("lodging")
    LODGING("Lodging"),

    @SerialName("entertainment")
    ENTERTAINMENT("Entertainment"),

    @SerialName("shopping")
    SHOPPING("Shopping"),

    @SerialName("utilities")
    UTILITIES("Utilities"),

    @SerialName("rent")
    RENT("Rent"),

    @SerialName("other")
    OTHER("Other"),
    ;

    /** The value the proxy and stored bills use, such as "food_and_drink": the name the category is serialized under. */
    val wireValue: String get() = serializer().descriptor.getElementName(ordinal)

    companion object {
        /** The category whose [wireValue] is [value]; null for anything else. */
        fun fromWire(value: String): BillCategory? = entries.firstOrNull { it.wireValue == value }
    }
}
