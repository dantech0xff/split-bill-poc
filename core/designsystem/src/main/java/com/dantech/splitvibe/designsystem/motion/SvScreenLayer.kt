package com.dantech.splitvibe.designsystem.motion

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The layer that shows one screen after another, such as the screens of the app's navigation or the
 * steps of a flow: a [SharedTransitionLayout], in which a surface of one screen grows into the next
 * through [sharedSurface], on the colour of the page. While one screen fades out and the next one
 * fades in, what shows between them is then the page, never the window behind the app, which is not
 * the page in every case, such as in the window of a test.
 */
@Composable
fun SvScreenLayer(modifier: Modifier = Modifier, content: @Composable SharedTransitionScope.() -> Unit) {
    SharedTransitionLayout(modifier.background(MaterialTheme.colorScheme.background), content)
}
