package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Shows one step of a flow at a time: the step on screen fades out, then the next one fades in
 * (see [SvMotion.stepFadeOut]). A surface marked with [sharedSurface] in one step grows or shrinks
 * into the surface with the same key in the step that follows, so a button can turn into what it
 * made.
 *
 * [step] carries what the step shows, so a step that is being left can still be drawn while it
 * fades out. [stepOf] names the step: a change of name is a new step, any other change of [step]
 * only updates the step on screen. The name is part of the key under which the state saved inside
 * the step comes back after the system ends the app, so it is a plain text that is the same in
 * every run of the app.
 */
@Composable
fun <S> SvSteps(step: S, stepOf: (S) -> String, modifier: Modifier = Modifier, content: @Composable (S) -> Unit) {
    val leave = SvMotion.stepFadeOut<Float>()
    val arrive = SvMotion.stepFadeIn<Float>()
    SharedTransitionLayout(modifier) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(arrive) togetherWith fadeOut(leave) },
            contentKey = stepOf,
            label = "steps",
        ) { shown ->
            val transition = remember(this@SharedTransitionLayout, this) { SvSharedTransition(this@SharedTransitionLayout, this) }
            CompositionLocalProvider(LocalSvSharedTransition provides transition) {
                content(shown)
            }
        }
    }
}
