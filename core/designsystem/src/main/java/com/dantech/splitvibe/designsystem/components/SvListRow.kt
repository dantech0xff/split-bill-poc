package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

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
 * One row of a group. The rows of a group sit close together on a tinted surface and only the
 * outer corners of the group are fully round, so the group reads as one block without any
 * divider lines. Each row can still be a separate item of a lazy list.
 *
 * With [onClick] the whole row is a button; [onClickLabel] tells a screen reader what it does.
 */
@Composable
fun SvListRow(
    position: SvRowPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val outer = 20.dp
    val inner = 4.dp
    val top = if (position == SvRowPosition.Single || position == SvRowPosition.First) outer else inner
    val bottom = if (position == SvRowPosition.Single || position == SvRowPosition.Last) outer else inner
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.lg, vertical = 1.dp),
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
