package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * The title above a group of content, with an optional line that says what can be done there, and
 * an optional [action] at the end of the title's line that leads on from the group, such as an
 * [SvTextAction] "See all". A screen reader can jump from one title to the next.
 */
@Composable
fun SvSectionHeader(title: String, modifier: Modifier = Modifier, hint: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        // An action brings a touch target wider than its text, which takes the room of the margin at the end.
        modifier = modifier.padding(
            start = SvSpacing.screen,
            end = if (action == null) SvSpacing.screen else SvSpacing.sm,
            top = SvSpacing.xl,
            bottom = SvSpacing.sm,
        ),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            action?.invoke()
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
