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
 * In the light theme cards lie flat on the page, told apart by their white against the grey
 * ground, and only what floats over content casts a shadow: a soft ink shadow below it. In the
 * dark theme shadows are invisible, so a level is a lighter surface instead and nothing is drawn
 * around it.
 */
enum class SvElevation(internal val layers: List<ShadowLayer>) {
    /** Rests on the page: a list card. Flat. */
    Level1(layers = emptyList()),

    /** Slightly lifted: a card that can be pressed. Flat in the light theme too. */
    Level2(layers = emptyList()),

    /** Floats over content: the floating bars and a floating button. */
    Level3(layers = listOf(ShadowLayer(radius = 28.dp, offsetY = 12.dp, alpha = 0.28f))),

    /** On top of everything: an item being dragged. */
    Level4(layers = listOf(ShadowLayer(radius = 36.dp, offsetY = 16.dp, alpha = 0.30f))),
}

internal class ShadowLayer(val radius: Dp, val offsetY: Dp, val alpha: Float)

/** The colour of every shadow: ink, the colour of the text. */
internal val ShadowColor = Swatch.Ink

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
    return level.layers.fold(this) { modifier, layer -> modifier.shadowLayer(layer, shape, strength) }
}

private fun Modifier.shadowLayer(layer: ShadowLayer, shape: Shape, strength: () -> Float) = dropShadow(shape) {
    val amount = strength().coerceIn(0f, 1f)
    radius = layer.radius.toPx()
    color = ShadowColor
    alpha = layer.alpha * amount
    offset = Offset(0f, layer.offsetY.toPx() * amount)
}
