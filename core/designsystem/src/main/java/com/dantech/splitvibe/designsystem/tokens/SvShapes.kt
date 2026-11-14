package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.ui.unit.dp

/**
 * Corner sizes that Material's shape scale does not have. Everything else takes its shape from
 * `MaterialTheme.shapes`.
 */
object SvShapes {
    /** The outer corners of a group of rows: the top of its first row and the bottom of its last. */
    val groupOuterCorner = 20.dp

    /** The corners where two rows of a group meet. Just enough to show that they are separate rows. */
    val groupInnerCorner = 4.dp
}
