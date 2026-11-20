package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

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
 * scrolls sideways instead, which suits a screen where the choice should take little height. The
 * chosen pill is dark on the light page and light on the dark one. A screen reader reads each
 * option as a radio button.
 */
@Composable
fun SvChoiceChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    singleRow: Boolean = false,
) {
    val chips = @Composable {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val colors = choiceColors(chosen)
            Surface(
                selected = chosen,
                onClick = { onSelect(index) },
                modifier = Modifier.semantics { role = Role.RadioButton },
                shape = SvShapes.pill,
                color = colors.container,
                contentColor = colors.content,
            ) {
                ChoiceLabel(option, colors.style, modifier = Modifier.padding(horizontal = SvSpacing.sm))
            }
        }
    }
    if (singleRow) {
        Row(
            modifier = modifier
                .selectableGroup()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) { chips() }
    } else {
        FlowRow(
            modifier = modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) { chips() }
    }
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
