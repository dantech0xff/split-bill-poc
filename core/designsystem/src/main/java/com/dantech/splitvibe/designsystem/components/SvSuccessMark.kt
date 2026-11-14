package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Marks that something has just succeeded. The first time it is shown it pops in and the phone
 * gives one short confirming vibration. It is decorative: say what succeeded in text beside it.
 */
@Composable
fun SvSuccessMark(icon: ImageVector, modifier: Modifier = Modifier) {
    // Saved, so turning the phone does not celebrate a second time.
    var celebrated by rememberSaveable { mutableStateOf(false) }
    val scale = remember { Animatable(if (celebrated) 1f else START_SCALE) }
    val pop = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        if (!celebrated) {
            celebrated = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scale.animateTo(1f, pop)
        }
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        tint = MaterialTheme.colorScheme.primary,
    )
}

private const val START_SCALE = 0.4f
