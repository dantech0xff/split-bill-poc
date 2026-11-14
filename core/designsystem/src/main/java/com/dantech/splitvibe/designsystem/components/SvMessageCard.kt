package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * A notice inside the page: something the user should know, or with [isError] something that went
 * wrong. [action] adds one button that lets the user act on it.
 */
@Composable
fun SvMessageCard(text: String, isError: Boolean, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isError) colors.errorContainer else colors.tertiaryContainer,
        contentColor = if (isError) colors.onErrorContainer else colors.onTertiaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.md)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
            }
        }
    }
}
