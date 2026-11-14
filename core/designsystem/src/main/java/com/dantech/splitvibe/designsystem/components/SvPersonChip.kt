package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

/**
 * A person who can be switched on or off for something, as a chip with the person's colour and
 * name. [selected] says whether the person takes part.
 */
@Composable
fun SvPersonChip(name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name) },
        modifier = modifier,
        leadingIcon = { SvAvatar(name, size = AVATAR_SIZE) },
    )
}

/**
 * A person who was added to something, as a chip with the person's colour and name and a cross.
 * A tap removes the person. A screen reader reads the chip as one button that says [removeLabel],
 * not as the selected tick box Material makes of it.
 */
@Composable
fun SvPersonChip(name: String, removeLabel: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(name) },
        modifier = modifier.clearAndSetSemantics {
            contentDescription = removeLabel
            role = Role.Button
            onClick(label = removeLabel) {
                onRemove()
                true
            }
        },
        avatar = { SvAvatar(name, size = AVATAR_SIZE) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(CROSS_SIZE)) },
    )
}

private val AVATAR_SIZE = 24.dp
private val CROSS_SIZE = 18.dp
