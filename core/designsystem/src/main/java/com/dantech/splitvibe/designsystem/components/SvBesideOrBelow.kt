package com.dantech.splitvibe.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * Two parts of one line side by side while they fit, and the [second] below the [first] when they do
 * not, as with text at twice its size, so that no word of either is ever broken in the middle. One
 * part keeps the width it would like, such as the buttons of a card, or the label of a detail; the
 * other takes the room that is left and goes on to more lines in it, between words.
 *
 * Side by side, [first] stands at the start and [second] at the end, the two centred on each other,
 * with [gap] between them. They stand so while the part that keeps its width, the [second] unless
 * [firstKeepsItsWidth], leaves the other room for its longest word. Otherwise [first] takes the whole
 * width and [second] stands below it at [secondBelowAt], [stackedGap] lower, such as buttons at the
 * end below the text of a card, or a value at the start below its label. A part with nothing in it
 * leaves the other the whole width.
 */
@Composable
fun SvBesideOrBelow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    firstKeepsItsWidth: Boolean = false,
    secondBelowAt: Alignment.Horizontal = Alignment.End,
    gap: Dp = SvSpacing.md,
    stackedGap: Dp = gap,
) {
    Layout(contents = listOf(first, second), modifier = modifier) { (firstParts, secondParts), constraints ->
        val start = firstParts.single()
        val end = secondParts.single()
        val space = gap.roundToPx()
        // Without a bounded width, as in a row that scrolls sideways, the parts stand side by side whole.
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            start.maxIntrinsicWidth(Constraints.Infinity) + space + end.maxIntrinsicWidth(Constraints.Infinity)
        }
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val keeper = if (firstKeepsItsWidth) start else end
        val taker = if (firstKeepsItsWidth) end else start
        val kept = keeper.maxIntrinsicWidth(Constraints.Infinity)
        if (kept == 0 || kept + space + taker.minIntrinsicWidth(Constraints.Infinity) <= width) {
            val keeperPlaced = keeper.measure(loose)
            val room = if (keeperPlaced.width == 0) width else (width - keeperPlaced.width - space).coerceAtLeast(0)
            val takerPlaced = taker.measure(loose.copy(maxWidth = room))
            val startPlaced = if (firstKeepsItsWidth) keeperPlaced else takerPlaced
            val endPlaced = if (firstKeepsItsWidth) takerPlaced else keeperPlaced
            val height = maxOf(startPlaced.height, endPlaced.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                startPlaced.placeRelative(0, (height - startPlaced.height) / 2)
                endPlaced.placeRelative(width - endPlaced.width, (height - endPlaced.height) / 2)
            }
        } else {
            val startPlaced = start.measure(loose)
            val endPlaced = end.measure(loose)
            val below = startPlaced.height + stackedGap.roundToPx()
            val height = (below + endPlaced.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                startPlaced.placeRelative(0, 0)
                endPlaced.place(secondBelowAt.align(endPlaced.width, width, layoutDirection), below)
            }
        }
    }
}
