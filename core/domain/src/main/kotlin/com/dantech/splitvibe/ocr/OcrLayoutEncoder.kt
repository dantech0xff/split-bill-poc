package com.dantech.splitvibe.ocr

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A piece of text with its box in layout coordinates (0..[OcrLayoutEncoder.SCALE] on both axes). */
data class LayoutFragment(val text: String, val x1: Int, val y1: Int, val x2: Int, val y2: Int)

/** Fragments that sit on the same visual row of the bill, left to right. */
data class LayoutRow(val fragments: List<LayoutFragment>)

/**
 * Turns raw OCR output into the "text + position" document that is sent to the model.
 *
 * OCR engines return text in blocks: on a receipt, the item names often come back as one block and
 * the prices as another, so the reading order alone does not say which price belongs to which item.
 * This step straightens the page, groups text into visual rows, and writes every fragment with its
 * coordinates so the model can line up columns the way a person would.
 */
object OcrLayoutEncoder {

    const val SCALE = 1000

    /** Rows of the bill, top to bottom. */
    fun rows(ocr: OcrResult): List<LayoutRow> {
        val lines = ocr.lines.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val skew = dominantAngle(lines)
        val boxes = lines.map { straighten(it, skew) }

        val minX = boxes.minOf { it.left }
        val maxX = boxes.maxOf { it.right }
        val minY = boxes.minOf { it.top }
        val maxY = boxes.maxOf { it.bottom }
        val width = (maxX - minX).coerceAtLeast(1f)
        val height = (maxY - minY).coerceAtLeast(1f)

        return groupIntoRows(boxes).map { row ->
            LayoutRow(
                row.sortedBy { it.left }.map { box ->
                    LayoutFragment(
                        text = box.text,
                        x1 = scale(box.left - minX, width),
                        y1 = scale(box.top - minY, height),
                        x2 = scale(box.right - minX, width),
                        y2 = scale(box.bottom - minY, height),
                    )
                },
            )
        }
    }

    /**
     * The document sent to the model. One row per line:
     *
     * `12 | [40,410,420,440] "Ca phe sua da" | [520,410,560,440] "2" | [800,410,960,440] "70.000"`
     */
    fun encode(ocr: OcrResult): String {
        val rows = rows(ocr)
        return buildString {
            append("<bill_ocr rows=\"").append(rows.size).append("\">\n")
            rows.forEachIndexed { index, row ->
                append(index + 1)
                for (fragment in row.fragments) {
                    append(" | [")
                    append(fragment.x1).append(',').append(fragment.y1).append(',')
                    append(fragment.x2).append(',').append(fragment.y2)
                    append("] ").append(quote(fragment.text))
                }
                append('\n')
            }
            append("</bill_ocr>")
        }
    }

    private class Box(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerY: Float get() = (top + bottom) / 2
        val height: Float get() = bottom - top
    }

    /**
     * The direction the text runs in, in radians. A length-weighted circular mean, so long lines
     * count more than a stray word and angles near +/-180 degrees do not cancel out.
     */
    private fun dominantAngle(lines: List<OcrLine>): Double {
        var sumCos = 0.0
        var sumSin = 0.0
        for (line in lines) {
            val (topLeft, topRight) = line.corners
            // The top edge is the text direction scaled by the line's length, so adding the edges
            // up is the length-weighted mean direction.
            sumCos += topRight.x - topLeft.x
            sumSin += topRight.y - topLeft.y
        }
        if (sumSin == 0.0 && sumCos == 0.0) return 0.0
        return atan2(sumSin, sumCos)
    }

    /** Rotates a line by -[angle] so its text runs left to right, and returns its upright box. */
    private fun straighten(line: OcrLine, angle: Double): Box {
        val cosA = cos(angle)
        val sinA = sin(angle)
        val xs = line.corners.map { (it.x * cosA + it.y * sinA).toFloat() }
        val ys = line.corners.map { (-it.x * sinA + it.y * cosA).toFloat() }
        return Box(line.text.trim(), xs.min(), ys.min(), xs.max(), ys.max())
    }

    /**
     * Groups boxes whose vertical centers are within half a line height of each other. Neighbouring
     * rows on a receipt are about a full line height apart, so this separates them reliably.
     */
    private fun groupIntoRows(boxes: List<Box>): List<List<Box>> {
        val rows = mutableListOf<MutableList<Box>>()
        for (box in boxes.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null) {
                val rowCenter = row.map { it.centerY }.average().toFloat()
                val rowHeight = row.map { it.height }.average().toFloat()
                val tolerance = ROW_TOLERANCE * min(box.height, rowHeight).coerceAtLeast(1f)
                if (abs(box.centerY - rowCenter) <= tolerance) {
                    row += box
                    continue
                }
            }
            rows += mutableListOf(box)
        }
        return rows
    }

    private fun scale(offset: Float, extent: Float): Int = (offset / extent * SCALE).roundToInt().coerceIn(0, SCALE)

    /**
     * Quotes one fragment. Angle brackets printed on the bill are swapped for look-alikes so that
     * text on a receipt can never close the bill section or open one that looks like the user's
     * instruction.
     */
    private fun quote(text: String): String {
        val cleaned = text
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('<', '\u2039')
            .replace('>', '\u203A')
        return "\"" + cleaned.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private const val ROW_TOLERANCE = 0.5f
}
