package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.LocalSvSharedTransition
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A sheet that rises from the bottom for a short task on top of a screen, such as picking people.
 * It follows the finger when dragged, and a back swipe shows the screen behind it before it
 * closes. [content] goes between the [title] and the [actions] at the bottom; a list in it should
 * take `Modifier.weight(1f, fill = false)` so the actions stay in view. What is in the sheet takes
 * the quiet colours of a card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SvBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Opened all the way, as tall as what it holds: a sheet stopped halfway would hide its actions.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        containerColor = SvElevation.Level1.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(width = HANDLE_WIDTH, height = HANDLE_HEIGHT, color = MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        // The sheet lies in a window of its own, outside every transition between screens or
        // steps, so nothing in it may take part in one.
        CompositionLocalProvider(LocalSvSharedTransition provides null, LocalSvGround provides SvGround.Card) {
            SheetContent(title, supportingText, actions, content)
        }
    }
}

@Composable
private fun SheetContent(
    title: String,
    supportingText: String?,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Column(
            modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.md, bottom = SvSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * A person who can be ticked in a list, such as in a sheet that picks who shares something: the
 * person's colour, name and an optional [detail], and a tick box. The whole row is one tick box
 * for a screen reader, and carries its label itself so it can be read even when part of it is
 * scrolled out of view.
 */
@Composable
fun SvPersonPickRow(
    name: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    PersonRow(
        name = name,
        detail = detail,
        chosen = checked,
        modifier = modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    )
}

/**
 * A person who can be chosen as the one out of a list, such as the one who paid: the person's
 * colour, name and an optional [detail], and the same box as [SvPersonPickRow], ticked for the
 * chosen person. The whole row is one radio button for a screen reader.
 */
@Composable
fun SvPersonChoiceRow(name: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    PersonRow(
        name = name,
        detail = detail,
        chosen = selected,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}

@Composable
private fun PersonRow(name: String, detail: String?, chosen: Boolean, modifier: Modifier) {
    Row(
        modifier = modifier
            .semantics { contentDescription = listOfNotNull(name, detail).joinToString(", ") }
            .fillMaxWidth()
            .heightIn(min = PERSON_ROW_HEIGHT)
            .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
    ) {
        SvAvatar(name)
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMediumEmphasized)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TickBox(ticked = chosen)
    }
}

/**
 * The box of a choice that can be ticked: an outline while it is not ticked, and once ticked a
 * box in the inverse of the page with a tick, lime in the light theme and ink in the dark one,
 * where lime would not show on the light box. Decorative: the row it belongs to says its state.
 */
@Composable
internal fun TickBox(ticked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = modifier
            .size(TICK_BOX_SIZE)
            .background(if (ticked) colors.inverseSurface else Color.Transparent, shape)
            .border(TICK_BOX_OUTLINE, if (ticked) colors.inverseSurface else colors.onSurface, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (ticked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(TICK_SIZE),
                tint = if (LocalDarkTheme.current) colors.inverseOnSurface else SvTheme.palette.accent,
            )
        }
    }
}

private val HANDLE_WIDTH = 40.dp
private val HANDLE_HEIGHT = 5.dp
private val PERSON_ROW_HEIGHT = 56.dp
private val TICK_BOX_SIZE = 24.dp
private val TICK_BOX_OUTLINE = 2.dp
private val TICK_SIZE = 18.dp
