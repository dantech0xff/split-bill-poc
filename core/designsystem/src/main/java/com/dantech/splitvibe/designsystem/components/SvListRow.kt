package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
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
 *
 * [trailing] is a part of the row that the tap leaves alone, such as a field for a person's part
 * beside their name; it is read and used on its own. It stands at the end of the row while what it
 * holds fits [trailingWidth], and below the rest of the row, across its width, when it does not, as
 * with large text.
 */
@Composable
fun SvListRow(
    position: SvRowPosition,
    label: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    trailingWidth: Dp = Dp.Unspecified,
    content: @Composable RowScope.() -> Unit,
) = GroupRow(
    position = position,
    modifier = modifier,
    action = Modifier
        .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label },
    trailing = trailing,
    trailingWidth = trailingWidth,
    content = content,
)

/**
 * A row of a group that a tap switches on or off, such as whether the user shares a bill. A screen
 * reader reads the row as one switch: [label] is what the row says, in one sentence, and
 * [stateDescription] its state in words, such as "You share the bill". [trailing] and
 * [trailingWidth] work as for a row that can be tapped.
 */
@Composable
fun SvListRow(
    position: SvRowPosition,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    stateDescription: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    trailingWidth: Dp = Dp.Unspecified,
    content: @Composable RowScope.() -> Unit,
) = GroupRow(
    position = position,
    modifier = modifier,
    action = Modifier
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics {
            contentDescription = label
            this.stateDescription = stateDescription
        },
    trailing = trailing,
    trailingWidth = trailingWidth,
    content = content,
)

/**
 * A row of a group opened to change what it holds, such as the item of a bill being edited: its
 * [content], usually fields, set into the card in the colour of the page as an [SvInset], so that it
 * stands apart from the closed rows around it.
 */
@Composable
fun SvOpenListRow(position: SvRowPosition, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GroupSurface(position = position, modifier = modifier) { first, last ->
        SvInset(
            modifier = Modifier
                .fillMaxWidth()
                .then(cardMargins(first, last)),
            content = content,
        )
    }
}

@Composable
private fun GroupRow(
    position: SvRowPosition,
    modifier: Modifier,
    action: Modifier,
    trailing: (@Composable () -> Unit)? = null,
    trailingWidth: Dp = Dp.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    GroupSurface(position = position, modifier = modifier) { first, last ->
        // The card keeps a margin around its rows, and a pressed row lights up as a rounded
        // shape inside that margin, so the rows of the card never touch its edge.
        val area = @Composable { margins: Modifier ->
            Row(
                modifier = margins
                    .clip(MaterialTheme.shapes.medium)
                    .then(action)
                    .heightIn(min = ROW_MIN_HEIGHT)
                    .padding(horizontal = SvSpacing.sm, vertical = SvSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
        if (trailing == null) {
            area(cardMargins(first, last))
        } else {
            require(trailingWidth != Dp.Unspecified) { "A row with a trailing part needs its width" }
            SvFieldRow(
                widths = listOf(SvFieldWidth.Share(1f, mustFit = false), SvFieldWidth.Fixed(trailingWidth + SvSpacing.sm * 2)),
                modifier = cardMargins(first, last),
                alignment = Alignment.CenterVertically,
                gap = 0.dp,
            ) {
                area(Modifier)
                // As far from the edge of the card as the content of the row, beside it and below it.
                Box(modifier = Modifier.padding(horizontal = SvSpacing.sm, vertical = SvSpacing.xs)) { trailing() }
            }
        }
    }
}

/** The card of a group around one row, with the round corners of its [position] and the ground of a card. */
@Composable
private fun GroupSurface(position: SvRowPosition, modifier: Modifier, content: @Composable (first: Boolean, last: Boolean) -> Unit) {
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
        CompositionLocalProvider(LocalSvGround provides SvGround.Card) { content(first, last) }
    }
}

/** The margin of the card around a row: at the sides always, at the top and the bottom of the group only. */
private fun cardMargins(first: Boolean, last: Boolean): Modifier = Modifier.padding(
    start = CARD_MARGIN,
    end = CARD_MARGIN,
    top = if (first) CARD_MARGIN else 0.dp,
    bottom = if (last) CARD_MARGIN else 0.dp,
)

/** The margin of the card around its rows. */
private val CARD_MARGIN = SvSpacing.sm

private val ROW_MIN_HEIGHT = 56.dp
