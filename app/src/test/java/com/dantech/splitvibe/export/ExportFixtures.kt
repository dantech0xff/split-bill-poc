package com.dantech.splitvibe.export

import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import java.math.BigDecimal

/**
 * Three bills to export, with what makes a CSV file hard to get right: Vietnamese names, amounts in
 * dong, in cents and in satang, a quantity with decimals, a discount, a place with a comma and
 * quotes in its name, a bill without a date and a person who joined from a link with a name that a
 * spreadsheet app would take for a formula.
 */
object ExportFixtures {

    val bills = listOf(
        SpendingFixtures.finished(
            id = "a1",
            bill = Bill(
                merchant = "Quán Ốc Ba Miển",
                date = "2026-11-12",
                currency = SpendingFixtures.vnd,
                items = listOf(
                    BillItem("i1", "Ốc hương rang muối", BigDecimal.ONE, 165_000, 165_000),
                    BillItem("i2", "Bia Saigon", BigDecimal(3), 20_000, 60_000),
                ),
                charges = listOf(BillCharge("c1", "VAT 8%", ChargeKind.TAX, 18_000)),
                printedTotalMinor = null,
            ).copy(category = BillCategory.FOOD_AND_DRINK),
            people = listOf("Minh", "Lan"),
            group = SpendingFixtures.FLAT,
        ),
        SpendingFixtures.finished(
            id = "b1",
            bill = Bill(
                merchant = "Bowling \"Strike\", Inc.",
                date = "2026-10-20",
                currency = SpendingFixtures.usd,
                items = listOf(
                    BillItem("i1", "Lane, 2 games", BigDecimal(2), 1_250, 2_500),
                    BillItem("i2", "Shoes", BigDecimal("1.5"), 400, 600),
                ),
                charges = listOf(
                    BillCharge("c1", "Member discount", ChargeKind.DISCOUNT, -350),
                    BillCharge("c2", "Tip", ChargeKind.TIP, 250),
                ),
                printedTotalMinor = null,
            ).copy(category = BillCategory.ENTERTAINMENT),
            people = listOf("Lan"),
            rate = SpendingFixtures.usdToVnd("25400"),
            strangers = listOf("=1+2"),
            payer = "Lan",
        ),
        SpendingFixtures.finished(
            id = "c1",
            bill = Bill(
                merchant = "Tops Market",
                date = null,
                currency = SpendingFixtures.thb,
                items = listOf(BillItem("i1", "Groceries", BigDecimal.ONE, null, 41_250)),
                charges = emptyList(),
                printedTotalMinor = null,
            ).copy(category = BillCategory.GROCERIES),
            people = emptyList(),
        ),
    )
}
