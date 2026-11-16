package com.dantech.splitvibe.export

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.YOU
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.ME_ID
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * How a spreadsheet app in the user's locale reads a CSV file: what separates the fields and what
 * the decimal mark is. Where a comma is the decimal mark, as in Vietnamese, spreadsheet apps expect
 * semicolons between the fields instead.
 */
enum class CsvConvention(val separator: Char, val decimalMark: Char) {
    DECIMAL_POINT(',', '.'),
    DECIMAL_COMMA(';', ','),
    ;

    companion object {
        fun of(locale: Locale): CsvConvention =
            if (DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') DECIMAL_COMMA else DECIMAL_POINT
    }
}

/**
 * The bills as three CSV files a spreadsheet app opens: `bills.csv` with one row per bill,
 * `items.csv` with one row per item and charge, and `shares.csv` with one row per person on a bill.
 * A bill's id is in every row, so the files can be joined.
 *
 * Each file is UTF-8 with a byte order mark, so that spreadsheet apps read Vietnamese names right,
 * and is quoted as RFC 4180 says, with CRLF line ends. An amount is written in major units with its
 * currency's own number of decimals, without grouping or symbol, and its currency's code is in a
 * column of its own. Text that a spreadsheet app would take for a formula, such as a name someone
 * typed into a bill's link starting with "=", starts with an apostrophe instead.
 */
object CsvExport {

    const val BILLS = "bills.csv"
    const val ITEMS = "items.csv"
    const val SHARES = "shares.csv"
    const val MIME_TYPE = "text/csv"

    private val BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** The three files for [bills], in this order, written for [convention]. */
    fun files(bills: List<SavedBill>, convention: CsvConvention): List<ExportFile> = listOf(
        ExportFile(BILLS, MIME_TYPE, write(convention, billRows(bills, convention))),
        ExportFile(ITEMS, MIME_TYPE, write(convention, itemRows(bills, convention))),
        ExportFile(SHARES, MIME_TYPE, write(convention, shareRows(bills, convention))),
    )

    private fun billRows(bills: List<SavedBill>, convention: CsvConvention): List<List<String>> =
        listOf(listOf("Bill ID", "Date", "Place", "Category", "Group", "Currency", "Total", "Your share")) +
            bills.map { saved ->
                val bill = saved.bill
                listOf(
                    text(saved.id),
                    text(bill.date.orEmpty()),
                    text(bill.merchant.orEmpty()),
                    text(bill.category.label),
                    text(saved.group?.groupName.orEmpty()),
                    text(bill.currency.code),
                    amount(bill.totalMinor, bill.currency, convention),
                    amount(saved.myShareMinor, bill.currency, convention),
                )
            }

    private fun itemRows(bills: List<SavedBill>, convention: CsvConvention): List<List<String>> =
        listOf(listOf("Bill ID", "Date", "Place", "Line", "Kind", "Quantity", "Unit price", "Amount", "Currency")) +
            bills.flatMap { saved ->
                val bill = saved.bill
                val start = listOf(text(saved.id), text(bill.date.orEmpty()), text(bill.merchant.orEmpty()))
                val items = bill.items.map { item ->
                    start + listOf(
                        text(item.name),
                        "Item",
                        number(item.quantity.stripTrailingZeros(), convention),
                        item.unitPriceMinor?.let { amount(it, bill.currency, convention) }.orEmpty(),
                        amount(item.amountMinor, bill.currency, convention),
                        text(bill.currency.code),
                    )
                }
                val charges = bill.charges.map { charge ->
                    start + listOf(
                        text(charge.label),
                        BillEditor.defaultLabel(charge.kind),
                        "",
                        "",
                        amount(charge.amountMinor, bill.currency, convention),
                        text(bill.currency.code),
                    )
                }
                items + charges
            }

    private fun shareRows(bills: List<SavedBill>, convention: CsvConvention): List<List<String>> =
        listOf(listOf("Bill ID", "Date", "Place", "Person", "Share", "Paid", "Currency")) +
            bills.flatMap { saved ->
                val bill = saved.bill
                saved.shares.map { share ->
                    val participant = saved.plan.participant(share.personId)
                    val name = if (share.personId == ME_ID) YOU else participant?.name ?: share.personId
                    val paid = saved.plan.payments.filter { it.participantId == share.personId }.sumOf { it.amountMinor }
                    listOf(
                        text(saved.id),
                        text(bill.date.orEmpty()),
                        text(bill.merchant.orEmpty()),
                        text(name),
                        amount(share.totalMinor, bill.currency, convention),
                        amount(paid, bill.currency, convention),
                        text(bill.currency.code),
                    )
                }
            }

    /** [minor] in major units with the currency's own number of decimals, such as "12.50", or "12,50" with a decimal comma. */
    private fun amount(minor: Long, currency: BillCurrency, convention: CsvConvention): String = number(currency.toMajor(minor), convention)

    private fun number(value: BigDecimal, convention: CsvConvention): String = value.toPlainString().replace('.', convention.decimalMark)

    /** [value] as text, kept from being read as a formula. */
    private fun text(value: String): String = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    private fun write(convention: CsvConvention, rows: List<List<String>>): ByteArray {
        val text = rows.joinToString(separator = "\r\n", postfix = "\r\n") { row ->
            row.joinToString(convention.separator.toString()) { field -> quoted(field, convention.separator) }
        }
        return BYTE_ORDER_MARK + text.toByteArray(Charsets.UTF_8)
    }

    /** [field] in double quotes, its own quotes doubled, when it holds the separator, a quote or a line break (RFC 4180). */
    private fun quoted(field: String, separator: Char): String =
        if (field.any { it == separator || it == '"' || it == '\r' || it == '\n' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}

/** A file to export: its name, its type and what it holds. */
class ExportFile(val name: String, val mimeType: String, val bytes: ByteArray)
