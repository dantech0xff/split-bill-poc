package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/** The title above a group of content, with an optional line that says what can be done there. */
@Composable
fun SvSectionHeader(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier = modifier.padding(start = SvSpacing.lg, end = SvSpacing.lg, top = SvSpacing.xl, bottom = SvSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
