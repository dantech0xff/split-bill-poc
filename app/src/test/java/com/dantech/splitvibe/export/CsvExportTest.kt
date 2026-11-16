package com.dantech.splitvibe.export

import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The CSV files of an export, byte for byte, in both conventions: commas between the fields with a
 * decimal point, and semicolons with a decimal comma. The reference files are in
 * `src/test/exports`; after an intended change, record them again with `-Pscreenshots=record`,
 * like the screenshots, and look at the difference.
 *
 * A spreadsheet app reads each file as an app in that locale would: the fields split on its
 * separator, as RFC 4180 quotes them, and the numbers read with the locale's own number format.
 */
class CsvExportTest {

    private val vietnamese = Locale.forLanguageTag("vi-VN")

    @Test
    fun `a locale whose decimal mark is a comma gets semicolons between the fields`() {
        assertEquals(CsvConvention.DECIMAL_COMMA, CsvConvention.of(vietnamese))
        assertEquals(CsvConvention.DECIMAL_COMMA, CsvConvention.of(Locale.GERMANY))
        assertEquals(CsvConvention.DECIMAL_POINT, CsvConvention.of(Locale.US))
        assertEquals(CsvConvention.DECIMAL_POINT, CsvConvention.of(Locale.forLanguageTag("th-TH")))
    }

    @Test
    fun `with a decimal point the files are the reference files, byte for byte`() =
        matchReferences(CsvConvention.DECIMAL_POINT, "decimal-point")

    @Test
    fun `with a decimal comma the files are the reference files, byte for byte`() =
        matchReferences(CsvConvention.DECIMAL_COMMA, "decimal-comma")

    @Test
    fun `a spreadsheet app with a decimal point reads every amount as the bill has it`() = readBack(CsvConvention.DECIMAL_POINT, Locale.US)

    @Test
    fun `a spreadsheet app with a decimal comma reads every amount as the bill has it`() = readBack(CsvConvention.DECIMAL_COMMA, vietnamese)

    @Test
    fun `each file starts with a byte order mark, ends its lines with CRLF and keeps text from being read as a formula`() {
        CsvExport.files(ExportFixtures.bills, CsvConvention.DECIMAL_POINT).forEach { file ->
            assertEquals(file.name, listOf(0xEF, 0xBB, 0xBF), file.bytes.take(3).map { it.toInt() and 0xFF })
            val text = file.bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
            assertEquals(file.name, text.count { it == '\n' }, Regex("\r\n").findAll(text).count())
        }
        val shares = rows(CsvExport.files(ExportFixtures.bills, CsvConvention.DECIMAL_POINT)[2], ',')
        assertEquals(listOf("You", "Minh", "Lan", "You", "Lan", "'=1+2", "You"), shares.drop(1).map { it[3] })
    }

    @Test
    fun `text in every column of text starts with an apostrophe when a spreadsheet app would take it for a formula`() {
        listOf('=', '+', '-', '@', '\t', '\r').forEach { start ->
            val bill = SpendingFixtures.finished(
                id = "${start}id",
                bill = Bill(
                    merchant = "${start}place",
                    date = "${start}date",
                    currency = BillCurrency("${start}C", 2),
                    items = listOf(BillItem("i1", "${start}item", BigDecimal.ONE, null, 1_000)),
                    charges = listOf(BillCharge("c1", "${start}charge", ChargeKind.TIP, 100)),
                    printedTotalMinor = null,
                ),
                people = listOf("person"),
                group = "${start}group",
            )
            // A bill keeps its people's names trimmed, so the guard is tried on a name set as it is.
            val people = bill.plan.participants.map { if (it.isMe) it else it.copy(name = "${start}person") }
            val named = bill.copy(plan = bill.plan.copy(participants = people))

            val (bills, items, shares) = CsvExport.files(listOf(named), CsvConvention.DECIMAL_POINT).map { rows(it, ',') }

            fun guarded(text: String) = "'$start$text"
            val name = "start ${start.code}"
            assertEquals(name, listOf("id", "date", "place", "group", "C").map(::guarded), bills[1].slice(listOf(0, 1, 2, 4, 5)))
            listOf(items[1] to "item", items[2] to "charge").forEach { (row, line) ->
                assertEquals(name, listOf("id", "date", "place", line, "C").map(::guarded), row.slice(listOf(0, 1, 2, 3, 8)))
            }
            // The user, then the person on the bill.
            assertEquals(name, listOf("id", "date", "place", "person", "C").map(::guarded), shares[2].slice(listOf(0, 1, 2, 3, 6)))
        }
    }

    private fun matchReferences(convention: CsvConvention, folder: String) {
        val files = CsvExport.files(ExportFixtures.bills, convention)

        assertEquals(listOf(CsvExport.BILLS, CsvExport.ITEMS, CsvExport.SHARES), files.map { it.name })
        files.forEach { file ->
            val reference = File("$REFERENCES/$folder/${file.name}")
            if (System.getProperty(RECORD) == "true") {
                reference.parentFile?.mkdirs()
                reference.writeBytes(file.bytes)
            } else {
                assertArrayEquals("$reference differs. It was:\n${file.bytes.toString(Charsets.UTF_8)}", reference.readBytes(), file.bytes)
            }
        }
    }

    /** Reads the amounts back as a spreadsheet app in [locale] would, and compares them with the bills. */
    private fun readBack(convention: CsvConvention, locale: Locale) {
        val (bills, items, shares) = CsvExport.files(ExportFixtures.bills, convention).map { rows(it, convention.separator) }
        // Each number as the plain digits of its value, so 30.00 and 30 are the same, but 12.50 and 1250 are not.
        fun column(rows: List<List<String>>, index: Int) = rows.drop(1).map { row -> row[index].ifEmpty { null }?.let { read(it, locale) } }

        assertEquals(listOf("Bill ID", "Date", "Place", "Category", "Group", "Currency", "Total", "Your share"), bills[0])
        assertEquals(listOf("a1", "b1", "c1"), bills.drop(1).map { it[0] })
        assertEquals("Bowling \"Strike\", Inc.", bills[2][2])
        assertEquals(listOf("243000", "30", "412.5"), column(bills, 6))
        assertEquals(listOf("81000", "10", "412.5"), column(bills, 7))

        assertEquals(
            listOf("Ốc hương rang muối", "Bia Saigon", "VAT 8%", "Lane, 2 games", "Shoes", "Member discount", "Tip", "Groceries"),
            items.drop(1).map { it[3] },
        )
        assertEquals(listOf("1", "3", null, "2", "1.5", null, null, "1"), column(items, 5))
        assertEquals(listOf("165000", "20000", null, "12.5", "4", null, null, null), column(items, 6))
        assertEquals(listOf("165000", "60000", "18000", "25", "6", "-3.5", "2.5", "412.5"), column(items, 7))
        // The lines of each bill add up to its total.
        val lineTotals = items.drop(1).groupBy { it[0] }.values.map { lines ->
            lines.sumOf { BigDecimal(read(it[7], locale)) }.stripTrailingZeros().toPlainString()
        }
        assertEquals(column(bills, 6), lineTotals)

        assertEquals(listOf("81000", "81000", "81000", "10", "10", "10", "412.5"), column(shares, 4))
        assertEquals(listOf("243000", "0", "0", "0", "30", "0", "412.5"), column(shares, 5))
        assertEquals(listOf("VND", "VND", "VND", "USD", "USD", "USD", "THB"), shares.drop(1).map { it[6] })
    }

    /** [cell] as a spreadsheet app in [locale] reads a number, in plain digits; it must take the whole cell, as such an app does. */
    private fun read(cell: String, locale: Locale): String {
        val position = ParsePosition(0)
        val parsed = checkNotNull(NumberFormat.getNumberInstance(locale).parse(cell, position)) { "\"$cell\" is no number in $locale" }
        assertEquals("\"$cell\" is read only in part in $locale", cell.length, position.index)
        return BigDecimal(parsed.toString()).stripTrailingZeros().toPlainString()
    }

    /** The rows of [file] as RFC 4180 reads them, with [separator] between the fields. */
    private fun rows(file: ExportFile, separator: Char): List<List<String>> {
        val text = file.bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }

                char == '"' -> quoted = !quoted

                !quoted && char == separator -> {
                    row += field.toString()
                    field.clear()
                }

                !quoted && char == '\r' && text.getOrNull(index + 1) == '\n' -> {
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                    index++
                }

                else -> field.append(char)
            }
            index++
        }
        return rows
    }

    private companion object {
        /** Relative to the module directory the tests run in. */
        const val REFERENCES = "src/test/exports"

        /** What `-Pscreenshots=record` sets, to record the reference files again. */
        const val RECORD = "roborazzi.test.record"
    }
}
