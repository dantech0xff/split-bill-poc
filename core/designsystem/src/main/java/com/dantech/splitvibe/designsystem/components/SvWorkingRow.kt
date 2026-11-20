package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * Takes the place of the main action while work is running: what is happening right now, and a
 * way to stop it. When [status] changes, the old line fades into the new one. In [SvBottomBar] it
 * takes the colours of the bar.
 */
@Composable
fun SvWorkingRow(status: String, cancelLabel: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // On the floating bar the indicator stays clear of the bar's round end; the cancel button is
    // round itself and sits at the bar's usual margin.
    val start = if (LocalSvGround.current == SvGround.Bar) BAR_START else 0.dp
    Row(
        modifier = modifier
            .heightIn(min = MAIN_ACTION_HEIGHT)
            .padding(start = start),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
    ) {
        SvLoadingIndicator(modifier = Modifier.size(INDICATOR_SIZE))
        AnimatedContent(
            targetState = status,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "status",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
        SvQuietButton(cancelLabel, onClick = onCancel)
    }
}

private val INDICATOR_SIZE = 32.dp

/** Added to the bar's own margin, so the indicator starts where the bar's round end allows. */
private val BAR_START = 8.dp
