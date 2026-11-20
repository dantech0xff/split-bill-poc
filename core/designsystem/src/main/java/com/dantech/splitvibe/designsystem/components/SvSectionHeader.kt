package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * The title above a group of content, with an optional line that says what can be done there.
 * A screen reader can jump from one title to the next.
 */
@Composable
fun SvSectionHeader(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.xl, bottom = SvSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.xs),
    ) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
