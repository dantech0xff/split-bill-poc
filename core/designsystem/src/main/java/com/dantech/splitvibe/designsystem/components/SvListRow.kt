package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.surface

/** Where a row sits in its group, which decides which of its corners are round. */
enum class SvRowPosition {
    Single,
    First,
    Middle,
    Last,
    ;

    companion object {
        /** The position of the row at [index] in a group of [count] rows. */
        fun of(index: Int, count: Int): SvRowPosition = when {
            count <= 1 -> Single
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }
    }
}

/**
 * One row of a group. The rows of a group follow each other without a gap or a line between them
 * and together form one card, white in the light theme, with round corners only at the outside
 * of the group. Each row can still be a separate item of a lazy list.
 */
@Composable
fun SvListRow(position: SvRowPosition, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    GroupRow(position = position, modifier = modifier, action = Modifier, content = content)

/**
 * A row of a group that can be tapped. A screen reader reads the whole row as one button: [label]
 * is what the row says, in one sentence, and [onClickLabel] is what a tap does.
 *
 * The row carries its label itself because a screen reader cannot read the text inside a row
 * while that text is scrolled out of view or lies under a bar, even when the edge of the row
 * still shows.
 */
@Composable
fun SvListRow(
    position: SvRowPosition,
    label: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) = GroupRow(
    position = position,
    modifier = modifier,
    action = Modifier
        .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label },
    content = content,
)

@Composable
private fun GroupRow(position: SvRowPosition, modifier: Modifier, action: Modifier, content: @Composable RowScope.() -> Unit) {
    val outer = SvShapes.groupOuterCorner
    val inner = SvShapes.groupInnerCorner
    val first = position == SvRowPosition.Single || position == SvRowPosition.First
    val last = position == SvRowPosition.Single || position == SvRowPosition.Last
    val top = if (first) outer else inner
    val bottom = if (last) outer else inner
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.screen),
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = SvElevation.Level1.surface,
    ) {
        CompositionLocalProvider(LocalSvGround provides SvGround.Card) {
            // The card keeps a margin around its rows, and a pressed row lights up as a rounded
            // shape inside that margin, so the rows of the card never touch its edge.
            Row(
                modifier = Modifier
                    .padding(
                        start = CARD_MARGIN,
                        end = CARD_MARGIN,
                        top = if (first) CARD_MARGIN else 0.dp,
                        bottom = if (last) CARD_MARGIN else 0.dp,
                    )
                    .clip(MaterialTheme.shapes.medium)
                    .then(action)
                    .heightIn(min = ROW_MIN_HEIGHT)
                    .padding(horizontal = SvSpacing.sm, vertical = SvSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** The margin of the card around its rows. */
private val CARD_MARGIN = SvSpacing.sm

private val ROW_MIN_HEIGHT = 56.dp
