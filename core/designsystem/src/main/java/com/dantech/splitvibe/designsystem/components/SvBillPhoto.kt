package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.elevation

/**
 * The photo of a bill, lying slightly turned on a sand-coloured tray across the width.
 *
 * A new photo settles onto the tray: it grows from a slightly smaller and rounder card to its
 * size and corners, so it reads as the photo that was just picked landing here. While [scanning],
 * a lime line runs down the photo and back up to show that its text is being read. With
 * animations off the photo simply appears and the line is not drawn; the status line beside the
 * photo says the same in words.
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
    val corner = MaterialTheme.shapes.small.topStart
    // Not clipped, so the photo's shadow can fall past the edge of the tray as a real one would.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SvTheme.palette.photoZone, MaterialTheme.shapes.largeIncreased),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = photo,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(TRAY_MARGIN)
                .heightIn(max = MAX_HEIGHT)
                // The photo keeps its own proportions, so the round corners are the photo's own.
                .aspectRatio(photo.width.toFloat() / photo.height, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    // The spring may overshoot a little; the size may, the opacity may not.
                    val progress = arrival.value
                    val scale = lerp(ARRIVING_SCALE, 1f, progress)
                    scaleX = scale
                    scaleY = scale
                    alpha = progress.coerceIn(0f, 1f)
                    rotationZ = TILT_DEGREES
                }
                // The shadow and the line lie in the turned layer, so they turn with the photo, but
                // outside its clip, so the shadow can fall around it and the line run past its edges.
                .elevation(SvElevation.Level3, MaterialTheme.shapes.small)
                .scanLine(scanning)
                .graphicsLayer {
                    // The corners shrink from round to the photo's own while it lands, without overshooting.
                    val settled = arrival.value.coerceIn(0f, 1f)
                    shape = RoundedCornerShape(lerp(arrivingCorner.toPx(size, this), corner.toPx(size, this), settled))
                    clip = true
                },
        )
    }
}

/**
 * The tray of [SvBillPhoto] before a photo is picked: the outline of a bill, the [title], such as "Add
 * the bill", and the [text] below it, which says how to add one. The outline is decorative. With
 * larger text the tray grows with what it says.
 */
@Composable
fun SvBillPhotoPlaceholder(title: String, text: String, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PLACEHOLDER_HEIGHT)
            .background(SvTheme.palette.photoZone, MaterialTheme.shapes.largeIncreased)
            .padding(PLACEHOLDER_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SvSpacing.sm, Alignment.CenterVertically),
    ) {
        Spacer(
            Modifier
                .size(OUTLINE_WIDTH, OUTLINE_HEIGHT)
                .drawBehind { drawBillOutline(ink) },
        )
        Text(
            title,
            modifier = Modifier.padding(top = SvSpacing.xs),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A bill drawn as the approved design draws it: a dashed page with three lines of text on it. */
private fun DrawScope.drawBillOutline(color: Color) {
    val unit = size.width / OUTLINE_GRID_WIDTH
    val edge = OUTLINE_STROKE * unit
    drawRoundRect(
        color = color,
        topLeft = Offset(edge, edge),
        size = Size(size.width - 2 * edge, size.height - 2 * edge),
        cornerRadius = CornerRadius(OUTLINE_CORNER * unit),
        style = Stroke(width = edge, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH * unit, GAP * unit))),
    )
    val line = LINE_STROKE * unit
    listOf(22f to 40f, 32f to 40f, 42f to 30f).forEach { (y, end) ->
        drawLine(color, start = Offset(16f * unit, y * unit), end = Offset(end * unit, y * unit), strokeWidth = line, cap = StrokeCap.Round)
    }
}

/** The line of light that runs over the photo while its text is read. */
@Composable
private fun Modifier.scanLine(scanning: Boolean): Modifier {
    if (!scanning || LocalReducedMotion.current) return this
    val color = SvTheme.palette.accent
    val position by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scan line",
    )
    // The line is made once for the size of the photo and only moved while it runs. Its place is
    // read while drawing, so the photo is neither measured nor composed again.
    return drawWithCache {
        val glow = GLOW_HEIGHT.toPx()
        val overhang = LINE_OVERHANG.toPx()
        val width = size.width + overhang * 2
        val band = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0f), color.copy(alpha = GLOW_ALPHA), color.copy(alpha = 0f)),
            startY = -glow,
            endY = glow,
        )
        val line = LINE_WIDTH.toPx()
        onDrawWithContent {
            drawContent()
            translate(left = -overhang, top = position * size.height) {
                drawRect(band, topLeft = Offset(0f, -glow), size = Size(width, glow * 2))
                drawLine(color, start = Offset.Zero, end = Offset(width, 0f), strokeWidth = line)
            }
        }
    }
}

/** The least space between the photo and the edges of its tray; the line reaches past the photo by less. */
private val TRAY_MARGIN = 24.dp
private val MAX_HEIGHT = 232.dp
private const val ARRIVING_SCALE = 0.85f

/** A slight turn, so the photo reads as a sheet of paper put down on the tray. */
private const val TILT_DEGREES = -3f
private const val SWEEP_MS = 1_600
private val GLOW_HEIGHT = 16.dp
private const val GLOW_ALPHA = 0.6f
private val LINE_WIDTH = 4.dp

/** How far the line reaches past each side of the photo. */
private val LINE_OVERHANG = 16.dp

private val PLACEHOLDER_HEIGHT = 232.dp
private val PLACEHOLDER_PADDING = 20.dp

// The outline of a bill in the placeholder, on the approved design's grid of 56 by 64.
private val OUTLINE_WIDTH = 56.dp
private val OUTLINE_HEIGHT = 64.dp
private const val OUTLINE_GRID_WIDTH = 56f
private const val OUTLINE_STROKE = 2f
private const val OUTLINE_CORNER = 10f
private const val DASH = 6f
private const val GAP = 5f
private const val LINE_STROKE = 2.4f
