package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme

/**
 * How far a surface floats above the one below it.
 *
 * In the light theme a level is a soft shadow tinted with the accent colour, made of two layers:
 * a wide faint one for the light all around, and a tighter one below for the light from above.
 * In the dark theme shadows are invisible, so a level is a lighter surface instead and nothing
 * is drawn around it.
 */
enum class SvElevation(internal val ambient: ShadowLayer, internal val key: ShadowLayer) {
    /** Rests on the page: a list card. */
    Level1(ambient = ShadowLayer(6.dp, 0.dp, 0.06f), key = ShadowLayer(2.dp, 1.dp, 0.10f)),

    /** Slightly lifted: a card that can be pressed. */
    Level2(ambient = ShadowLayer(12.dp, 0.dp, 0.07f), key = ShadowLayer(4.dp, 2.dp, 0.12f)),

    /** Floats over content: a bottom bar or a floating button. */
    Level3(ambient = ShadowLayer(20.dp, 0.dp, 0.08f), key = ShadowLayer(8.dp, 4.dp, 0.14f)),

    /** On top of everything: a dragged item or a dialog. */
    Level4(ambient = ShadowLayer(32.dp, 0.dp, 0.10f), key = ShadowLayer(12.dp, 8.dp, 0.16f)),
}

internal class ShadowLayer(val radius: Dp, val offsetY: Dp, val alpha: Float)

/** The colour of a surface at this level. Light surfaces are all white; dark ones get lighter with each level. */
val SvElevation.surface: Color
    @Composable
    @ReadOnlyComposable
    get() {
        val colors = MaterialTheme.colorScheme
        if (!LocalDarkTheme.current) return colors.surfaceContainerLowest
        return when (this) {
            SvElevation.Level1 -> colors.surfaceContainerLow
            SvElevation.Level2 -> colors.surfaceContainer
            SvElevation.Level3 -> colors.surfaceContainerHigh
            SvElevation.Level4 -> colors.surfaceContainerHighest
        }
    }

/**
 * Draws the shadow of [level] around [shape]. Put it before the background of the same shape.
 *
 * [strength] fades the shadow in and out: 0 is flat on the page, 1 is the full level. It is read
 * while drawing, so animating it costs no recomposition, and it only moves the shadow's opacity
 * and offset. Those two are cheap to change every frame. The blur radius is not: it is fixed per
 * level on purpose, so to animate between two levels, cross-fade their strengths.
 */
@Composable
fun Modifier.elevation(level: SvElevation, shape: Shape, strength: () -> Float = { 1f }): Modifier {
    if (LocalDarkTheme.current) return this
    // The darkest step of the accent ramp, so the shadow carries a hint of the brand colour.
    val tint = MaterialTheme.colorScheme.onPrimaryFixed
    return this
        .shadowLayer(level.ambient, shape, tint, strength)
        .shadowLayer(level.key, shape, tint, strength)
}

private fun Modifier.shadowLayer(layer: ShadowLayer, shape: Shape, tint: Color, strength: () -> Float) = dropShadow(shape) {
    val amount = strength().coerceIn(0f, 1f)
    radius = layer.radius.toPx()
    color = tint
    alpha = layer.alpha * amount
    offset = Offset(0f, layer.offsetY.toPx() * amount)
}
