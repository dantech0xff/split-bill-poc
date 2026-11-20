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
import androidx.compose.ui.unit.Dp
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

/**
 * The mark of what the AI does, such as beside what it understood of a bill or above the question
 * whether it may be used: the spark of [SvActionIcons.Spark] in ink on a lime circle, [size] across.
 * It stands still, and it is decorative: say in text what it marks.
 */
@Composable
fun SvAiMark(modifier: Modifier = Modifier, size: Dp = AI_MARK_SIZE) {
    val palette = SvTheme.palette
    Box(
        modifier = modifier
            .size(size)
            .background(palette.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SvActionIcons.Spark, contentDescription = null, modifier = Modifier.size(size * AI_ICON_SCALE), tint = palette.onAccent)
    }
}

private val SIZE = 72.dp
private val ICON_SIZE = 36.dp
private const val START_SCALE = 0.4f

private val AI_MARK_SIZE = 28.dp

/** The spark at the size the approved designs draw it in its circle: 16dp in 28dp. */
private const val AI_ICON_SCALE = 0.57f
