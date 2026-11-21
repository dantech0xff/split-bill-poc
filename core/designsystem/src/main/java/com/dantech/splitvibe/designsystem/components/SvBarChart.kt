package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import kotlin.math.ceil

/** One bar of an [SvBarChart]: the short [label] below it, such as "N" for November, and its [value]. */
@Immutable
class SvChartBar(val label: String, val value: Float)

/**
 * Values side by side as columns, such as what was spent in each of the last twelve months: each of
 * the [bars] rises to its share of the largest value, with its label below it. The bar at
 * [highlighted], such as the current month, stands out in violet and its label in bold. A bar of no
 * value still shows as a short stub, so that every column can be told apart.
 *
 * Every bar has a column of the same width. With more bars than fit at their full width, such as
 * every month of a few years, the bars grow thinner so that they stay apart, and when the labels no
 * longer fit their columns only every second or third bar keeps its label, counted from the
 * highlighted bar, or from the last one, so that no two labels run into each other.
 *
 * [description] says what the chart shows in words for a screen reader, such as which months were
 * the highest, as the chart itself is only drawn.
 */
@Composable
fun SvBarChart(bars: List<SvChartBar>, description: String, modifier: Modifier = Modifier, highlighted: Int? = null) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val largest = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val column = if (bars.isEmpty()) maxWidth else maxWidth / bars.size
        val barWidth = min(BAR_WIDTH, column * BAR_SHARE)
        val labelEvery = labelStep(bars.map { it.label }, column)
        val counted = highlighted ?: bars.lastIndex
        Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT),
            ) {
                bars.forEachIndexed { index, bar ->
                    val share = if (bar.value.isNaN()) 0f else (bar.value / largest).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .width(barWidth)
                                .height(max(BAR_STUB, CHART_HEIGHT * share))
                                .background(if (index == highlighted) colors.primary else colors.onSurface, SvShapes.pill),
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                bars.forEachIndexed { index, bar ->
                    val chosen = index == highlighted
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if ((counted - index) % labelEvery == 0) {
                            // A label wider than its column reaches into the columns beside it, which keep none.
                            Text(
                                bar.label,
                                modifier = Modifier.wrapContentWidth(unbounded = true),
                                style = if (chosen) typography.labelSmallEmphasized else typography.labelSmall,
                                color = if (chosen) colors.onSurface else colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Every how many bars a label can stand, so that the widest of [labels], in the bold style of the
 * highlighted bar and with a little room on either side, fits the width of the columns it takes.
 */
@Composable
private fun labelStep(labels: List<String>, column: Dp): Int {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmallEmphasized
    val density = LocalDensity.current
    return remember(labels, style, column, measurer, density) {
        val room = with(density) { column.toPx() }
        if (!room.isFinite() || room <= 0f) return@remember 1
        val widest = labels.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
        val needs = widest + with(density) { LABEL_GAP.toPx() }
        ceil(needs / room).toInt().coerceAtLeast(1)
    }
}

private val CHART_HEIGHT = 120.dp
private val BAR_WIDTH = 14.dp
private val BAR_STUB = 4.dp

/** The most of its column a bar takes, so that thin bars still have a gap between them. */
private const val BAR_SHARE = 0.6f

/** The least room between two labels. */
private val LABEL_GAP = 4.dp
