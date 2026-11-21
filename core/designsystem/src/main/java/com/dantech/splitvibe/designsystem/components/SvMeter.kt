package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min

/**
 * How much of an allowance is left, as a row of segments that fill from the start, such as the AI
 * calls left this month: [value] out of [max]. While [max] is small each segment stands for one
 * unit, and part of a unit fills part of a segment; a [max] of more than ten is shared out over ten
 * segments. [description] says the same in words for a screen reader, such as "9 of 10 calls left",
 * as the meter itself is only drawn.
 *
 * On a card the empty segments take the card's quiet shade. On the page they are a shade darker than
 * the page, as the white that a control takes there would hardly show in a meter this thin.
 */
@Composable
fun SvMeter(value: Float, max: Float, description: String, modifier: Modifier = Modifier) {
    val ground = LocalSvGround.current
    val filled = ground.active
    val empty = if (ground == SvGround.Page) MaterialTheme.colorScheme.outlineVariant else ground.quiet
    // Nothing to count yet shows as one empty segment rather than as nothing at all.
    val segments = if (max <= 0f || max.isNaN()) 1 else min(ceil(max).toInt(), MAX_METER_SEGMENTS)
    val units = if (max <= 0f || max.isNaN()) 0f else value.coerceIn(0f, max) / max * segments
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(METER_HEIGHT)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val gap = SEGMENT_GAP.toPx()
        val width = (size.width - gap * (segments - 1)) / segments
        val ltr = layoutDirection == LayoutDirection.Ltr
        for (index in 0 until segments) {
            // The first segment to fill is at the start of the reading direction.
            val left = if (ltr) index * (width + gap) else size.width - width - index * (width + gap)
            pill(empty, left, width)
            val share = (units - index).coerceIn(0f, 1f)
            if (share > 0f) {
                // The filled part is cut from a whole segment, so it keeps the segment's round ends.
                val fillLeft = if (ltr) left else left + width * (1f - share)
                clipRect(left = fillLeft, right = fillLeft + width * share) { pill(filled, left, width) }
            }
        }
    }
}

/**
 * One value's part of the largest of its kind, as a thin bar that fills from the start, such as what
 * was spent on one category against the category spent on most: [fraction] from 0 to 1, a part too
 * small to see still showing as a short stub. [color] is the bar's own colour, such as the colour of
 * the category; by default it is the colour of text. With [fromEnd] it fills from the end, for an
 * amount that goes the other way, such as what someone owes beside what others get back.
 *
 * [description] says what the bar shows in words, for a screen reader. Pass null only where the text
 * beside the bar already says it, such as an amount in the same row; the bar is then left out.
 */
@Composable
fun SvStatBar(
    fraction: Float,
    description: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fromEnd: Boolean = false,
) {
    val track = LocalSvGround.current.quiet
    val share = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(STAT_BAR_HEIGHT)
            .clearAndSetSemantics { if (description != null) contentDescription = description },
    ) {
        pill(track, 0f, size.width)
        if (share > 0f) {
            val length = (size.width * share).coerceIn(min(STAT_BAR_STUB.toPx(), size.width), size.width)
            // The bar fills from the start of the reading direction, or from its end with fromEnd.
            val fromLeft = (layoutDirection == LayoutDirection.Ltr) != fromEnd
            pill(color, if (fromLeft) 0f else size.width - length, length)
        }
    }
}

/** A pill of [width] from [left], as tall as the canvas. */
private fun DrawScope.pill(color: Color, left: Float, width: Float) {
    val radius = size.height / 2
    drawRoundRect(color, topLeft = Offset(left, 0f), size = Size(width, size.height), cornerRadius = CornerRadius(radius, radius))
}

/** The most segments a meter draws; a larger allowance is shared out over them. */
private const val MAX_METER_SEGMENTS = 10

private val METER_HEIGHT = 12.dp
private val SEGMENT_GAP = 4.dp
private val STAT_BAR_HEIGHT = 8.dp

/** As long as the bar is tall, so the smallest part is a round dot. */
private val STAT_BAR_STUB = 8.dp
