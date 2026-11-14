package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Remembers whether the content of a screen has made its entrance. Rows that come into view later,
 * by scrolling, then simply appear instead of entering again.
 */
@Stable
class SvEntryState internal constructor(finished: Boolean) {
    internal var finished by mutableStateOf(finished)
}

/** Create one per screen and hand it to every [staggeredEntry] on that screen. */
@Composable
fun rememberSvEntryState(): SvEntryState {
    val reduced = LocalReducedMotion.current
    val state = remember { SvEntryState(finished = reduced) }
    LaunchedEffect(state) {
        delay(ENTRANCE_WINDOW_MS)
        state.finished = true
    }
    return state
}

/**
 * Lets a row slide up and fade in when its screen appears, a moment after the row above it, so
 * the eye is led down the list. [index] is the row's place in that order.
 */
@Composable
fun Modifier.staggeredEntry(state: SvEntryState, index: Int): Modifier {
    val progress = remember { Animatable(if (state.finished) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.coerceAtMost(LAST_DELAYED_ROW) * STEP_MS)
            progress.animateTo(1f, spec)
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * RISE.toPx()
    }
}

private val RISE = 24.dp
private const val STEP_MS = 40L
private const val LAST_DELAYED_ROW = 10
private const val ENTRANCE_WINDOW_MS = 700L
