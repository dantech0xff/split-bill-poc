package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Gives an element a quick springy bounce each time [value] changes, so the person sees which
 * element their tap changed, such as a chip that was just switched on or off. Nothing moves when
 * the element first appears, and nothing moves when animations are off.
 */
@Composable
fun Modifier.bounceOnChange(value: Any?): Modifier {
    val scale = remember { Animatable(1f) }
    val last = remember { arrayOf(value) }
    val reduced = LocalReducedMotion.current
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(value) {
        if (value == last[0]) return@LaunchedEffect
        last[0] = value
        if (reduced) return@LaunchedEffect
        scale.snapTo(PRESSED_SCALE)
        scale.animateTo(1f, spring)
    }
    return scaledBy(scale)
}

/**
 * Pops an element in with the same bounce when it is added to a screen that is already shown,
 * such as a person added to a split. Elements that are there when the screen appears take part
 * in its entrance through [entry] instead, and do not pop.
 */
@Composable
fun Modifier.popIn(entry: SvEntryState): Modifier {
    // Saved, so an element in a list that scrolls out of view and back in does not pop in again.
    var shown by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (entry.finished && !reduced && !shown) ARRIVING_SCALE else 1f) }
    val spring = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(scale) {
        shown = true
        scale.animateTo(1f, spring)
    }
    return scaledBy(scale)
}

private fun Modifier.scaledBy(scale: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

private const val PRESSED_SCALE = 0.85f
private const val ARRIVING_SCALE = 0.6f
