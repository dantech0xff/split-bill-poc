package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A short row of actions that floats over the content, for a screen that offers a few actions of
 * equal weight at the same time. Put an icon button with a label in it for each action. It
 * floats at the third elevation level like the other floating parts of the app.
 */
@Composable
fun SvFloatingToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val level = SvElevation.Level3
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.elevation(level, CircleShape),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = level.surface),
        shape = CircleShape,
        // Depth comes from the elevation level above, not from Material's own shadow.
        expandedShadowElevation = 0.dp,
        collapsedShadowElevation = 0.dp,
        content = content,
    )
}
