package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.tokens.SvTheme

/**
 * Marks that something has just succeeded: a round lime badge with [icon] on it in ink.
 * The first time it is shown the icon pops in and the phone gives one short confirming vibration.
 * Because the badge is round, a fully round button can turn into it through `Modifier.sharedSurface`.
 * It is decorative: say what succeeded in text beside it.
 */
@Composable
fun SvSuccessMark(icon: ImageVector, modifier: Modifier = Modifier) {
    // Saved, so turning the phone does not celebrate a second time.
    var celebrated by rememberSaveable { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (celebrated || reduced) 1f else START_SCALE) }
    val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val haptics = LocalHapticFeedback.current
    val palette = SvTheme.palette

    LaunchedEffect(Unit) {
        if (!celebrated) {
            celebrated = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scale.animateTo(1f, pop)
        }
    }

    Box(
        modifier = modifier
            .size(SIZE)
            .background(palette.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(ICON_SIZE)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            tint = palette.onAccent,
        )
    }
}

private val SIZE = 72.dp
private val ICON_SIZE = 36.dp
private const val START_SCALE = 0.4f
