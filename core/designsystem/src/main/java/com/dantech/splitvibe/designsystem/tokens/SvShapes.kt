package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material's shape scale with the rounder corners of Split Vibe. Screens read it from
 * `MaterialTheme.shapes`. The three larger steps that Material's expressive components use are set
 * too, so that each step stays rounder than the one before it.
 */
internal val SvShapeScale = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(36.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** Shapes that Material's shape scale does not have. */
object SvShapes {
    /** Fully rounded ends: buttons, tags, the floating bars. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** A small square tile, such as the one behind a category's icon or a person's initials. */
    val tile: Shape = RoundedCornerShape(14.dp)

    /** The outer corners of a group of rows: the top of its first row and the bottom of its last. */
    val groupOuterCorner = 24.dp

    /** The corners where two rows of a group meet: none, as the rows of a group form one card. */
    val groupInnerCorner = 0.dp
}
