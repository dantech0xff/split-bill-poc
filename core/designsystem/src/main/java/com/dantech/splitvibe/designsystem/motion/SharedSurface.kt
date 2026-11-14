package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * What a screen needs to take part in a transition where one surface grows into another.
 * Whatever moves between the screens provides it; here that is the navigation host.
 */
class SvSharedTransition(val scope: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalSvSharedTransition = compositionLocalOf<SvSharedTransition?> { null }

/**
 * Marks a surface that turns into the surface with the same [key] on the screen that follows: the
 * button that opens a screen grows into that screen, and shrinks back into the button on the way
 * back. That shows where the new screen came from.
 *
 * A whole screen is scaled while it changes size, which is cheap. A small surface such as a
 * button should pass [resize]: it is then laid out again at every size, so its label keeps its
 * size instead of being blown up with the surface.
 *
 * A screen shown on its own, as in a preview or a test of that one screen, has nothing to grow
 * from, and this does nothing.
 */
@Composable
fun Modifier.sharedSurface(key: String, resize: Boolean = false): Modifier {
    val transition = LocalSvSharedTransition.current ?: return this
    return with(transition.scope) {
        this@sharedSurface.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = transition.visibility,
            enter = fadeIn(SvMotion.screenChange()),
            exit = fadeOut(SvMotion.screenChange()),
            boundsTransform = BoundsTransform { _, _ -> SvMotion.screenChange() },
            resizeMode = if (resize) ResizeMode.RemeasureToBounds else ResizeMode.scaleToBounds(),
        )
    }
}

/**
 * Keeps content at the size it has outside a transition while the surface around it grows or
 * shrinks into another surface. A label is then cut off by the edge of a shrinking surface
 * instead of wrapping onto more lines at every size on the way.
 */
@Composable
fun Modifier.keepSizeDuringTransition(): Modifier {
    val transition = LocalSvSharedTransition.current ?: return this
    return with(transition.scope) { this@keepSizeDuringTransition.skipToLookaheadSize() }
}
