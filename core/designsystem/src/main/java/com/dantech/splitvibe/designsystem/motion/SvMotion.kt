package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
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

    /**
     * How a step of a flow fades out while the next one comes, in the first part of a
     * [screenChange]. The next step fades in over the rest ([stepFadeIn]), so two steps that look
     * nothing alike are never seen on top of each other, and the whole change takes as long as a
     * surface needs to grow from one step into the next.
     */
    fun <T> stepFadeOut(): FiniteAnimationSpec<T> = tween(durationMillis = STEP_FADE_OUT_MILLIS, easing = FastOutLinearInEasing)

    /** How the next step of a flow fades in after [stepFadeOut]. */
    fun <T> stepFadeIn(): FiniteAnimationSpec<T> = tween(
        durationMillis = SCREEN_CHANGE_MILLIS - STEP_FADE_OUT_MILLIS,
        delayMillis = STEP_FADE_OUT_MILLIS,
        easing = LinearOutSlowInEasing,
    )

    private const val SCREEN_CHANGE_MILLIS = 400
    private const val STEP_FADE_OUT_MILLIS = 140
}
