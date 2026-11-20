package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import kotlin.math.roundToInt

/**
 * A choice of one option out of a few, as pills of equal width in one row, such as the period of
 * the insights. The chosen pill is dark on the light page and light on the dark one. A screen
 * reader reads each option as a radio button.
 */
@Composable
fun SvChoiceGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val colors = choiceColors(chosen)
            Surface(
                checked = chosen,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shape = SvShapes.pill,
                color = colors.container,
                contentColor = colors.content,
            ) {
                ChoiceLabel(option, colors.style)
            }
        }
    }
}

/**
 * A choice of one option out of more than fit in a row of equal pills, such as the five ways to
 * split a bill, as pills that wrap onto the next line. With [singleRow] they stay on one line that
 * scrolls sideways instead, which suits a screen where the choice should take little height; the
 * row then shows the chosen pill whole when it first appears and whenever the choice changes. The
 * chosen pill is dark on the light page and light on the dark one. A screen reader reads each
 * option as a radio button. [leading] draws a decoration before the label of the option at the index
 * it is given, such as the icon of a category; the label says it all.
 *
 * [contentPadding] lies inside a row that scrolls, so that the row can run to the edges of the
 * screen while its first and last pills line up with the content around it.
 */
@Composable
fun SvChoiceChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    singleRow: Boolean = false,
    leading: (@Composable (index: Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val chip = @Composable { index: Int, placement: Modifier ->
        val chosen = index == selected
        val colors = choiceColors(chosen)
        Surface(
            selected = chosen,
            onClick = { onSelect(index) },
            modifier = placement.semantics { role = Role.RadioButton },
            shape = SvShapes.pill,
            color = colors.container,
            contentColor = colors.content,
        ) {
            if (leading == null) {
                ChoiceLabel(options[index], colors.style, modifier = Modifier.padding(horizontal = SvSpacing.sm))
            } else {
                Row(
                    modifier = Modifier.padding(start = LEADING_START, end = SvSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leading(index)
                    ChoiceLabel(options[index], colors.style, modifier = Modifier.padding(start = SvSpacing.xs))
                }
            }
        }
    }
    if (singleRow) {
        val scroll = rememberScrollState()
        // Where each chip lies along the row, counted from the start of the row.
        val spans = remember { mutableStateMapOf<Int, IntRange>() }
        val direction = LocalLayoutDirection.current
        val density = LocalDensity.current
        // A chip lies inside the padding, which scrolls with the chips. The chosen one shows whole with
        // as much room on either side as the padding leaves the first and the last chip.
        val before = with(density) { contentPadding.calculateStartPadding(direction).roundToPx() }
        val after = with(density) { contentPadding.calculateEndPadding(direction).roundToPx() }
        ShowWhole(scroll, spans[selected]?.let { span -> span.first until span.last + 1 + before + after })
        Row(
            modifier = modifier
                .selectableGroup()
                .horizontalScroll(scroll)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            options.indices.forEach { index ->
                val place = Modifier.onPlaced { coordinates ->
                    val span = coordinates.spanFromStart(direction)
                    // Written only when it moved, so that placing the chips again does not compose the row again.
                    if (spans[index] != span) spans[index] = span
                }
                chip(index, place)
            }
        }
    } else {
        FlowRow(
            modifier = modifier
                .selectableGroup()
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            options.indices.forEach { index -> chip(index, Modifier) }
        }
    }
}

/**
 * Scrolls a row that scrolls sideways so that [span], the part of the row counted from its start
 * that the chosen chip takes, shows whole. The row jumps there when it first appears, and glides
 * there when the choice changes, unless animations are off.
 */
@Composable
private fun ShowWhole(scroll: ScrollState, span: IntRange?) {
    val reduced = LocalReducedMotion.current
    val glide = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Whether the row has been brought to its chosen chip before; only the effect below reads it.
    var shown by remember { mutableStateOf(false) }
    val viewport = scroll.viewportSize
    LaunchedEffect(span, viewport) {
        if (span == null || viewport == 0) return@LaunchedEffect
        val target = when {
            // A chip wider than the row shows its start, where its label begins.
            span.first < scroll.value || span.last - span.first + 1 > viewport -> span.first

            span.last >= scroll.value + viewport -> span.last + 1 - viewport

            else -> scroll.value
        }
        if (target != scroll.value) {
            if (shown && !reduced) scroll.animateScrollTo(target, glide) else scroll.scrollTo(target)
        }
        shown = true
    }
}

/** The part of the row that this chip takes, counted from the start of the row, which is its right edge in a right-to-left layout. */
private fun LayoutCoordinates.spanFromStart(direction: LayoutDirection): IntRange {
    val left = positionInParent().x.roundToInt()
    val width = size.width
    val start = if (direction == LayoutDirection.Ltr) left else (parentLayoutCoordinates?.size?.width ?: 0) - left - width
    return start until start + width
}

@Composable
private fun ChoiceLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = CHOICE_HEIGHT)
            .padding(horizontal = SvSpacing.sm, vertical = SvSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, textAlign = TextAlign.Center)
    }
}

private class ChoiceColors(val container: Color, val content: Color, val style: TextStyle)

/** A chosen option is the inverse of the page; the others take the quiet colour of what they sit on. */
@Composable
private fun choiceColors(chosen: Boolean): ChoiceColors {
    val typography = MaterialTheme.typography
    if (chosen) {
        val colors = MaterialTheme.colorScheme
        return ChoiceColors(colors.inverseSurface, colors.inverseOnSurface, typography.labelLargeEmphasized)
    }
    val ground = LocalSvGround.current
    return ChoiceColors(ground.quiet, ground.onQuiet, typography.labelLarge)
}

private val CHOICE_HEIGHT = 44.dp

/** Less room before a decoration than before a label, as the approved design leaves. */
private val LEADING_START = 9.dp
