package com.dantech.splitvibe.export

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withTranslation
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.dayOf
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.dateText
import com.dantech.splitvibe.text.labelRes
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.text
import java.io.ByteArrayOutputStream

/**
 * The PDF summary of an export, on A4 pages: the period, what the bills add up to in the local
 * currency, by month, category, person and group, and then every bill in its own currency. It is
 * black and grey on white only, so it prints well anywhere. Its words come from [resources], in the
 * app's language, and its numbers, amounts and dates are written as the app's texts write them.
 *
 * The pages are laid out first and drawn on any canvas afterwards, which is how the tests look at
 * them; [bytes] draws them into an [android.graphics.pdf.PdfDocument].
 */
internal class PdfSummary(private val content: ExportContent, private val resources: Resources) {

    private val local = content.insights.currency
    private val titleText = paint(TITLE_SIZE, Typeface.DEFAULT_BOLD)
    private val headingText = paint(HEADING_SIZE, Typeface.DEFAULT_BOLD)
    private val bodyText = paint(BODY_SIZE, Typeface.DEFAULT)
    private val strongText = paint(BODY_SIZE, Typeface.DEFAULT_BOLD)
    private val quietText = paint(SMALL_SIZE, Typeface.DEFAULT).apply { alpha = QUIET_ALPHA }
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        alpha = RULE_ALPHA
        strokeWidth = RULE_WIDTH
    }

    /** The blocks on each page, each with where its top is on the page. */
    val pages: List<List<Pair<Float, Block>>> = paginate(blocks())

    /** Draws page [index] on [canvas], which is [PAGE_WIDTH] by [PAGE_HEIGHT] points. */
    fun draw(index: Int, canvas: Canvas) {
        pages[index].forEach { (top, block) -> block.draw(canvas, top) }
        val baseline = PAGE_HEIGHT - MARGIN + FOOTER_GAP
        canvas.drawText(footer(), MARGIN, baseline, quietText)
        val number = pageNumber(index)
        canvas.drawText(number, MARGIN + WIDTH - quietText.measureText(number), baseline, quietText)
    }

    /** What the pages say, a line for each block and a table's cells between bars, as plain text to compare. */
    fun text(): String = pages.mapIndexed { index, page ->
        (page.map { (_, block) -> block.text } + "${footer()} | ${pageNumber(index)}").joinToString("\n")
    }.joinToString("\n\n")

    private fun footer() = words(text(R.string.pdf_footer, content.period))

    private fun pageNumber(index: Int) = words(text(R.string.pdf_page, index + 1, pages.size))

    /** The summary as the bytes of a PDF file. */
    fun bytes(): ByteArray {
        val document = PdfDocument()
        try {
            pages.indices.forEach { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                draw(index, page.canvas)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also(document::writeTo).toByteArray()
        } finally {
            document.close()
        }
    }

    private fun blocks(): List<Block> = buildList {
        val insights = content.insights
        val overall = insights.overall
        add(Paragraph(words(text(R.string.pdf_title)), titleText))
        add(Paragraph(words(text(R.string.pdf_period, content.period, local.code)), bodyText, above = SMALL_GAP))
        val came = plural(R.plurals.pdf_overall, overall.bills, overall.bills, money(overall.totalMinor), money(overall.myShareMinor))
        add(Paragraph(words(came), strongText, above = GAP))
        content.notes.forEach { add(Paragraph(it, quietText, above = SMALL_GAP)) }

        val bills = words(text(R.string.pdf_bills))
        val total = words(text(R.string.pdf_total))
        val yourShare = words(text(R.string.pdf_your_share))
        if (insights.byMonth.isNotEmpty()) {
            table(
                words(text(R.string.insights_by_month)),
                listOf(words(text(R.string.pdf_month)), bills, total, yourShare),
                SUMMARY_COLUMNS,
                insights.byMonth.entries.reversed().map { (month, spending) ->
                    listOf(Exporter.monthName(month), "${spending.bills}", money(spending.totalMinor), money(spending.myShareMinor))
                },
            )
        }
        if (insights.byCategory.isNotEmpty()) {
            table(
                words(text(R.string.insights_by_category)),
                listOf(words(text(R.string.pdf_category)), bills, total, yourShare),
                SUMMARY_COLUMNS,
                insights.byCategory.map { (category, spending) ->
                    listOf(words(text(category.labelRes)), "${spending.bills}", money(spending.totalMinor), money(spending.myShareMinor))
                },
            )
        }
        if (insights.byPerson.isNotEmpty()) {
            table(
                words(text(R.string.insights_by_person)),
                listOf(words(text(R.string.pdf_person)), bills, total, words(text(R.string.pdf_their_share))),
                SUMMARY_COLUMNS,
                insights.byPerson.map { person ->
                    listOf(person.name, "${person.bills}", money(person.totalMinor), money(person.shareMinor))
                },
            )
        }
        if (insights.byGroup.isNotEmpty()) {
            table(
                words(text(R.string.insights_by_group)),
                listOf(words(text(R.string.pdf_group)), bills, total, yourShare),
                SUMMARY_COLUMNS,
                insights.byGroup.map { (group, spending) ->
                    val name = group?.name ?: words(text(R.string.insights_no_group))
                    listOf(name, "${spending.bills}", money(spending.totalMinor), money(spending.myShareMinor))
                },
            )
        }
        if (content.bills.isNotEmpty()) {
            table(
                words(text(R.string.pdf_bills_heading)),
                listOf(
                    words(text(R.string.pdf_date)),
                    words(text(R.string.pdf_place)),
                    words(text(R.string.pdf_category)),
                    total,
                    yourShare,
                ),
                BILL_COLUMNS,
                content.bills.map { saved ->
                    val bill = saved.bill
                    listOf(
                        day(bill.date),
                        bill.merchant?.takeIf { it.isNotBlank() } ?: words(text(R.string.common_bill)),
                        words(text(bill.category.labelRes)),
                        bill.currency.format(bill.totalMinor),
                        bill.currency.format(saved.myShareMinor),
                    )
                },
            )
        }
    }

    private fun words(text: UiText): String = text.resolve(resources)

    /** A table under [title]: a row of [headers], then [rows], laid out in [columns]. */
    private fun MutableList<Block>.table(title: String, headers: List<String>, columns: List<Column>, rows: List<List<String>>) {
        add(Paragraph(title, headingText, above = SECTION_GAP, keepWithNext = true))
        val layout = TableLayout(columns, headers, rows)
        val header = Row(headers, layout, strongText, isHeader = true)
        add(header)
        rows.forEach { add(Row(it, layout, bodyText, isHeader = false, header = header)) }
    }

    /**
     * Places [blocks] from the top of the first page down, on a new page whenever the next one does
     * not fit. A heading stays with what it heads, and a table that goes on over a page break starts
     * the new page with its header again.
     */
    private fun paginate(blocks: List<Block>): List<List<Pair<Float, Block>>> {
        val pages = mutableListOf<MutableList<Pair<Float, Block>>>(mutableListOf())
        var top = MARGIN
        blocks.forEachIndexed { index, block ->
            // The block, and every block after it that it stays with.
            val together = blocks.drop(index).let { rest -> rest.take(rest.indexOfFirst { !it.keepWithNext } + 1).ifEmpty { rest } }
            if (top + together.sumOf { it.height.toDouble() } > BOTTOM && pages.last().isNotEmpty()) {
                pages += mutableListOf<Pair<Float, Block>>()
                top = MARGIN
                val header = (block as? Row)?.header
                if (header != null) {
                    pages.last() += top to header
                    top += header.height
                }
            }
            pages.last() += top to block
            top += block.height
        }
        return pages
    }

    private fun money(minor: Long): String = local.format(minor)

    /** A bill's date as people read it, such as "Nov 13, 2026", or as written when it is no day. */
    private fun day(date: String?): String {
        if (date.isNullOrBlank()) return words(text(R.string.pdf_no_date))
        return dayOf(date)?.let(::dateText) ?: date
    }

    /** Something drawn on a page, [height] points tall, which says [text]. */
    sealed interface Block {
        val height: Float
        val text: String

        /** Whether the block stays on a page with the one after it, as a heading does with what it heads. */
        val keepWithNext: Boolean

        fun draw(canvas: Canvas, top: Float)
    }

    /** Text across the width of the page, on as many lines as it takes, [above] points below what is above it. */
    private class Paragraph(
        override val text: String,
        paint: TextPaint,
        private val above: Float = 0f,
        override val keepWithNext: Boolean = false,
    ) : Block {
        private val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        override val height: Float = above + layout.height

        override fun draw(canvas: Canvas, top: Float) {
            canvas.withTranslation(MARGIN, top + above) { layout.draw(this) }
        }
    }

    /**
     * What a column of a table holds. A [NAME], such as a place or a person, is cut short with "…"
     * when it is longer than its column. The others never are, as a cut amount reads as another
     * number, and a [NUMBER] ends at its column's right edge.
     */
    private enum class Kind { NAME, TEXT, NUMBER }

    /** A column of a table: what it holds, and its share of the page's width while its cells fit in it. */
    private class Column(val kind: Kind, val share: Float)

    /**
     * The widths of a table's [columns], which have one name, for its [headers] and [rows]. Each column
     * that is never cut takes its share, or what its widest cell needs when that is more, and the name
     * takes the rest. Only when those cells would leave the name less than [MIN_NAME_WIDTH], as the
     * largest amounts with a long currency code can, is their text drawn smaller until they leave it.
     */
    private inner class TableLayout(val columns: List<Column>, headers: List<String>, rows: List<List<String>>) {
        /** How much smaller than the rest of the table the columns that are never cut are drawn: 1 but for such amounts. */
        val scale: Float
        val widths: List<Float>

        init {
            require(columns.count { it.kind == Kind.NAME } == 1) { "A table has one column that is cut short" }
            val needed = columns.mapIndexed { index, column ->
                if (column.kind == Kind.NAME) {
                    0f
                } else {
                    maxOf(strongText.measureText(headers[index]), rows.maxOfOrNull { bodyText.measureText(it[index]) } ?: 0f) + CELL_GAP
                }
            }
            val room = WIDTH - MIN_NAME_WIDTH
            val preferred = columns.mapIndexed { index, column ->
                if (column.kind == Kind.NAME) 0f else maxOf(needed[index], WIDTH * column.share)
            }
            val fitted = if (preferred.sum() <= room) preferred else needed
            scale = minOf(1f, room / fitted.sum())
            val scaled = fitted.map { it * scale }
            widths = columns.mapIndexed { index, column -> if (column.kind == Kind.NAME) WIDTH - scaled.sum() else scaled[index] }
        }

        /** [paint] as a cell of column [index] is drawn with. */
        fun paintOf(index: Int, paint: TextPaint): TextPaint =
            if (columns[index].kind == Kind.NAME || scale == 1f) paint else TextPaint(paint).apply { textSize = paint.textSize * scale }
    }

    /** One line of a table, laid out in [layout]: only a name too long for its column is cut short, with "…". */
    private inner class Row(
        cells: List<String>,
        private val layout: TableLayout,
        paint: TextPaint,
        private val isHeader: Boolean,
        /** The header of the table the row belongs to, which a new page repeats. */
        val header: Row? = null,
    ) : Block {
        private val paints = cells.indices.map { layout.paintOf(it, paint) }

        /** The cells as they are drawn. */
        private val shown = cells.mapIndexed { index, text ->
            if (layout.columns[index].kind != Kind.NAME) {
                text
            } else {
                TextUtils.ellipsize(text, paints[index], layout.widths[index] - CELL_GAP, TextUtils.TruncateAt.END).toString()
            }
        }
        private val baselineOffset = (ROW_HEIGHT - paint.ascent() - paint.descent()) / 2
        override val height: Float = ROW_HEIGHT
        override val keepWithNext: Boolean = isHeader
        override val text: String = shown.joinToString(" | ")

        override fun draw(canvas: Canvas, top: Float) {
            var left = MARGIN
            shown.forEachIndexed { index, text ->
                val width = layout.widths[index]
                val x = if (layout.columns[index].kind == Kind.NUMBER) left + width - paints[index].measureText(text) else left
                canvas.drawText(text, x, top + baselineOffset, paints[index])
                left += width
            }
            if (isHeader) canvas.drawLine(MARGIN, top + ROW_HEIGHT, MARGIN + WIDTH, top + ROW_HEIGHT, rule)
        }
    }

    companion object {
        /** An A4 page, in points. */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842

        /** The white space around the text of every page, in points. */
        const val MARGIN = 48f
        private const val WIDTH = PAGE_WIDTH - 2 * MARGIN
        private const val FOOTER_GAP = 20f
        private const val BOTTOM = PAGE_HEIGHT - MARGIN - FOOTER_GAP
        private const val TITLE_SIZE = 20f
        private const val HEADING_SIZE = 13f
        private const val BODY_SIZE = 9.5f
        private const val SMALL_SIZE = 8.5f
        private const val ROW_HEIGHT = 17f
        private const val SMALL_GAP = 4f
        private const val GAP = 12f
        private const val SECTION_GAP = 18f
        private const val CELL_GAP = 8f

        /** The least a table leaves the column of names, which is a quarter of the page. */
        private const val MIN_NAME_WIDTH = WIDTH / 4
        private const val RULE_WIDTH = 0.5f

        /** Grey, as black that lets the white page through. */
        private const val QUIET_ALPHA = 160
        private const val RULE_ALPHA = 96

        private val SUMMARY_COLUMNS = listOf(
            Column(Kind.NAME, 0.46f),
            Column(Kind.NUMBER, 0.12f),
            Column(Kind.NUMBER, 0.21f),
            Column(Kind.NUMBER, 0.21f),
        )
        private val BILL_COLUMNS = listOf(
            Column(Kind.TEXT, 0.15f),
            Column(Kind.NAME, 0.31f),
            Column(Kind.TEXT, 0.18f),
            Column(Kind.NUMBER, 0.18f),
            Column(Kind.NUMBER, 0.18f),
        )

        private fun paint(size: Float, typeface: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.typeface = typeface
        }
    }
}
