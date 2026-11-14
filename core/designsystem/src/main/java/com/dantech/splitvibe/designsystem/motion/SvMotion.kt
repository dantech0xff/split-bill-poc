package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** Timing that is not part of Material's motion scheme. */
object SvMotion {

    /**
     * The timing of everything that happens while one screen turns into another: the fade, and a
     * surface growing into the next screen.
     *
     * It has a fixed length on purpose. A back swipe scrubs through the transition with the
     * finger, and only parts of equal length move together and follow the finger evenly. A spring
     * would be done after the first part of the swipe and leave nothing to preview.
     */
    fun <T> screenChange(): FiniteAnimationSpec<T> = tween(durationMillis = SCREEN_CHANGE_MILLIS, easing = FastOutSlowInEasing)

    private const val SCREEN_CHANGE_MILLIS = 400
}
