package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.elevation

/** One action of a [SvFabMenu]. */
class SvFabMenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The main action as a floating button that opens a short menu, for a screen where the main
 * action can start in a few ways. While the menu opens, the button turns from a rounded square
 * into a round close button. [label] tells a screen reader what the button opens.
 *
 * The screen keeps [expanded], because back belongs to the screen: close the menu there with
 * `BackHandler(enabled = expanded) { onExpandedChange(false) }`.
 */
@Composable
fun SvFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: String,
    icon: ImageVector,
    items: List<SvFabMenuItem>,
    modifier: Modifier = Modifier,
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = { MenuButton(expanded = expanded, onExpandedChange = onExpandedChange, label = label, icon = icon) },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    item.onClick()
                },
                icon = { Icon(item.icon, contentDescription = null) },
                text = { Text(item.label) },
            )
        }
    }
}

/**
 * The button of the menu. Material's own toggle button draws a shadow of its own, so this one
 * makes the same change itself and takes its depth from the elevation levels like the rest of
 * the app.
 */
@Composable
private fun MenuButton(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, label: String, icon: ImageVector) {
    val progress = animateFloatAsState(if (expanded) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "menu button")
    // The change itself is read while drawing, so the button is composed again only twice: when
    // the change passes its middle on the way open and on the way back.
    val open = { progress.value.coerceIn(0f, 1f) }
    val pastMiddle by remember { derivedStateOf { open() > HALF_WAY } }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(BUTTON_SIZE)
            // The shadow keeps one of two fixed shapes, so it is not blurred again at every step of the change.
            .elevation(SvElevation.Level3, if (pastMiddle) OPEN_SHAPE else CLOSED_SHAPE)
            .graphicsLayer {
                shape = RoundedCornerShape(lerp(CLOSED_CORNER, OPEN_CORNER, open()))
                clip = true
            }
            .drawBehind { drawRect(lerp(colors.primaryContainer, colors.primary, open())) }
            .toggleable(value = expanded, role = Role.Button, onValueChange = onExpandedChange)
            .semantics {
                contentDescription = label
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (pastMiddle) Icons.Default.Close else icon,
            contentDescription = null,
            tint = if (pastMiddle) colors.onPrimary else colors.onPrimaryContainer,
            modifier = Modifier.graphicsLayer { rotationZ = open() * QUARTER_TURN },
        )
    }
}

private val BUTTON_SIZE = 56.dp
private val CLOSED_CORNER = 16.dp
private val OPEN_CORNER = 28.dp
private val CLOSED_SHAPE = RoundedCornerShape(CLOSED_CORNER)
private val OPEN_SHAPE = RoundedCornerShape(OPEN_CORNER)
private const val HALF_WAY = 0.5f
private const val QUARTER_TURN = 90f
