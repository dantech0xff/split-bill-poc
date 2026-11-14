package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.keepSizeDuringTransition
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * The main action of a screen as a large button across the full width. It is fully round, so it
 * can turn into a round surface such as [SvSuccessMark]; its label then keeps its size and is cut
 * off by the shrinking edge instead of wrapping onto more lines.
 */
@Composable
fun SvPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(text, modifier = Modifier.keepSizeDuringTransition(), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The bar at the bottom of a screen that holds its main action. It floats above the scrolling
 * content and stays clear of the system navigation bar.
 */
@Composable
fun SvBottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val level = SvElevation.Level3
    Surface(
        modifier = modifier.elevation(level, RectangleShape),
        color = level.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.md),
            content = content,
        )
    }
}
