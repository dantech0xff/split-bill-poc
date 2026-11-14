package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion

/**
 * The photo of a bill in its frame.
 *
 * A new photo settles into the frame: it grows from a slightly smaller and rounder card to the
 * frame's size and corners, so it reads as the photo that was just picked landing here. While
 * [scanning], a band of light runs down the photo and back up to show that its text is being
 * read. With animations off the photo simply appears and the band is not drawn; the status line
 * beside the photo says the same in words.
 */
@Composable
fun SvBillPhoto(photo: ImageBitmap, contentDescription: String, scanning: Boolean, modifier: Modifier = Modifier) {
    // Saved, so turning the phone does not land the same photo a second time.
    var landed by rememberSaveable(photo) { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val arrival = remember(photo) { Animatable(if (landed || reduced) 1f else 0f) }
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(arrival) {
        landed = true
        arrival.animateTo(1f, settle)
    }

    val arrivingCorner = MaterialTheme.shapes.extraLarge.topStart
    val corner = MaterialTheme.shapes.medium.topStart
    Image(
        bitmap = photo,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .heightIn(max = MAX_HEIGHT)
            // The frame takes the shape of the photo, so the round corners are the photo's own.
            .aspectRatio(photo.width.toFloat() / photo.height, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                // The spring may overshoot a little; the size may, the corners and opacity may not.
                val progress = arrival.value
                val settled = progress.coerceIn(0f, 1f)
                val scale = lerp(ARRIVING_SCALE, 1f, progress)
                scaleX = scale
                scaleY = scale
                alpha = settled
                shape = RoundedCornerShape(lerp(arrivingCorner.toPx(size, this), corner.toPx(size, this), settled))
                clip = true
            }
            .scanBand(scanning),
    )
}

/** The band of light that runs over the photo while its text is read. */
@Composable
private fun Modifier.scanBand(scanning: Boolean): Modifier {
    if (!scanning || LocalReducedMotion.current) return this
    val color = MaterialTheme.colorScheme.primary
    val position by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scan band",
    )
    // Read while drawing, so the band moves without measuring or composing the photo again.
    return drawWithContent {
        drawContent()
        val y = position * size.height
        val band = BAND_HEIGHT.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0f), color.copy(alpha = BAND_ALPHA), color.copy(alpha = 0f)),
                startY = y - band,
                endY = y + band,
            ),
            topLeft = Offset(0f, y - band),
            size = Size(size.width, band * 2),
        )
        drawLine(color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = LINE_WIDTH.toPx())
    }
}

private val MAX_HEIGHT = 320.dp
private const val ARRIVING_SCALE = 0.85f
private const val SWEEP_MS = 1_600
private val BAND_HEIGHT = 40.dp
private const val BAND_ALPHA = 0.3f
private val LINE_WIDTH = 2.dp
