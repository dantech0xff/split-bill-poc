package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A surface that floats at [elevation] above the page. Its content is laid out in a column.
 *
 * Pass [color] for a card that carries meaning through colour, such as the accent container.
 * Without it the card takes the surface colour of its level.
 */
@Composable
fun SvCard(
    modifier: Modifier = Modifier,
    elevation: SvElevation = SvElevation.Level1,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    color: Color = elevation.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.elevation(elevation, shape), shape = shape, color = color) {
        Column(content = content)
    }
}
