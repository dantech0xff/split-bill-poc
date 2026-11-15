package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * A choice of one option out of a few, as buttons joined into one row, such as how a bill is
 * split. The chosen option is filled and its corners turn round. A screen reader reads each
 * option as a radio button.
 */
@Composable
fun SvChoiceGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = index == selected,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(option)
            }
        }
    }
}

/**
 * A choice of one option out of more than fit in a row of buttons, such as the five ways to split
 * a bill, as chips that wrap onto the next line. The chosen chip is filled. A screen reader reads
 * each option as a radio button.
 */
@Composable
fun SvChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(option) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}
