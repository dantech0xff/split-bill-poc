package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * Takes the place of the main action while work is running: what is happening right now, and a
 * way to stop it. When [status] changes, the old line fades into the new one.
 */
@Composable
fun SvWorkingRow(status: String, cancelLabel: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Row(modifier = modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        SvLoadingIndicator(modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(SvSpacing.md))
        AnimatedContent(
            targetState = status,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "status",
        ) { text ->
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onCancel) { Text(cancelLabel) }
    }
}
