package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToLong

/**
 * An amount of money that moves to its value instead of jumping: it runs through the numbers in
 * between when the value changes, and with [countUp] it also counts up from zero when it first
 * appears.
 *
 * [amountMinor] is in the smallest unit of the currency and [format] turns such a number into the
 * text to show. A screen reader always gets the real amount, never a number from the middle of
 * the run.
 */
@Composable
fun SvAmount(amountMinor: Long, format: (Long) -> String, style: TextStyle, modifier: Modifier = Modifier, countUp: Boolean = false) {
    // The run is described as a share of the way from where it started to where it is going, so
    // large amounts keep their exact value at both ends.
    var from by remember { mutableLongStateOf(if (countUp) 0L else amountMinor) }
    var to by remember { mutableLongStateOf(amountMinor) }
    val progress = remember { Animatable(if (countUp) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(amountMinor) {
        if (progress.value < 1f || to != amountMinor) {
            // A new value may arrive in the middle of a run. The next run starts from the number
            // that is on screen at that moment, so nothing jumps.
            from = shownAmount(from, to, progress.value)
            to = amountMinor
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        }
    }

    Text(
        text = format(shownAmount(from, to, progress.value)),
        style = style,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(format(amountMinor)) },
    )
}

private fun shownAmount(from: Long, to: Long, progress: Float): Long = when {
    progress >= 1f -> to
    progress <= 0f -> from
    else -> from + ((to - from) * progress.toDouble()).roundToLong()
}
