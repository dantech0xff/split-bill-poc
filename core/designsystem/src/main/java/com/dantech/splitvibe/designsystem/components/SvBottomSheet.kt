package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * A sheet that rises from the bottom for a short task on top of a screen, such as picking people.
 * It follows the finger when dragged, and a back swipe shows the screen behind it before it
 * closes. [content] goes between the [title] and the [actions] at the bottom; a list in it should
 * take `Modifier.weight(1f, fill = false)` so the actions stay in view.
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
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (supportingText != null) {
                    Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SvSpacing.xl, vertical = SvSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
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
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = { SvAvatar(name) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = listOfNotNull(name, detail).joinToString(", ") }
            .padding(horizontal = SvSpacing.sm),
    )
}
