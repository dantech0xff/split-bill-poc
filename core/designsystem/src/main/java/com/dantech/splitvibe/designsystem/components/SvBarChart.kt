package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/** One bar of an [SvBarChart]: the short [label] below it, such as "N" for November, and its [value]. */
@Immutable
class SvChartBar(val label: String, val value: Float)

/**
 * Values side by side as columns, such as what was spent in each of the last twelve months: each of
 * the [bars] rises to its share of the largest value, with its label below it. The bar at
 * [highlighted], such as the current month, stands out in violet and its label in bold. A bar of no
 * value still shows as a short stub, so that every column can be told apart.
 *
 * [description] says what the chart shows in words for a screen reader, such as which months were
 * the highest, as the chart itself is only drawn.
 */
@Composable
fun SvBarChart(bars: List<SvChartBar>, description: String, modifier: Modifier = Modifier, highlighted: Int? = null) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val largest = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
    ) {
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
                            .width(BAR_WIDTH)
                            .height(max(BAR_STUB, CHART_HEIGHT * share))
                            .background(if (index == highlighted) colors.primary else colors.onSurface, SvShapes.pill),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEachIndexed { index, bar ->
                val chosen = index == highlighted
                Text(
                    bar.label,
                    modifier = Modifier.weight(1f),
                    style = if (chosen) typography.labelSmallEmphasized else typography.labelSmall,
                    color = if (chosen) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val CHART_HEIGHT = 120.dp
private val BAR_WIDTH = 14.dp
private val BAR_STUB = 4.dp
