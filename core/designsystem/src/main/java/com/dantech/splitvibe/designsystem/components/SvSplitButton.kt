package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.keepSizeDuringTransition
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.surface

/** One option of a menu: what it says and what it does. */
class SvMenuOption(val label: String, val onClick: () -> Unit)

/**
 * A main action with a few close alternatives, across the full width. The pill does the main
 * action, violet on the page and lime in [SvBottomBar]; the round arrow beside it opens a menu with
 * the [options], and turns over while the menu is open. [moreLabel] tells a screen reader what the
 * arrow opens.
 */
@Composable
fun SvSplitButton(text: String, onClick: () -> Unit, moreLabel: String, options: List<SvMenuOption>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) HALF_TURN else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "arrow")
    val ground = LocalSvGround.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = MAIN_ACTION_HEIGHT),
            shape = SvShapes.pill,
            colors = mainActionColors(),
            contentPadding = LEADING_PADDING,
        ) {
            Text(
                text,
                modifier = Modifier.keepSizeDuringTransition(),
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
            )
        }
        // The menu is anchored to the arrow, so it opens at the end of the bar where the arrow is.
        Box {
            Surface(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier
                    .size(MAIN_ACTION_HEIGHT)
                    .semantics { contentDescription = moreLabel },
                shape = CircleShape,
                color = ground.quiet,
                contentColor = ground.onQuiet,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = turn },
                    )
                }
            }
            SvMenu(expanded = expanded, onDismiss = { expanded = false }, options = options)
        }
    }
}

/**
 * The ⋮ button of a row or a bar, which opens a menu of [options]. [label] says what the menu is
 * for, such as "More for VietQR", and is what a screen reader reads for the button.
 */
@Composable
fun SvOverflowMenu(label: String, options: List<SvMenuOption>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = label) }
        SvMenu(expanded = expanded, onDismiss = { expanded = false }, options = options)
    }
}

@Composable
private fun SvMenu(expanded: Boolean, onDismiss: () -> Unit, options: List<SvMenuOption>) {
    // A menu opens in a window of its own, where no shadow can be drawn around it. So it has an
    // outline instead of Material's own shadow, and the surface of an elevation level.
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = SvElevation.Level2.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onDismiss()
                    option.onClick()
                },
            )
        }
    }
}

private const val HALF_TURN = 180f

/** Less room at the sides than a lone main action has, as the arrow takes part of the width. */
private val LEADING_PADDING = PaddingValues(horizontal = SvSpacing.lg, vertical = SvSpacing.sm)
