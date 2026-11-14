package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.data.contacts.Contact
import com.dantech.splitvibe.ui.components.PersonAvatar

/** Bottom sheet for choosing which contacts share the bill. Changes apply immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeoplePickerSheet(
    contacts: List<Contact>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                "Pick people to share with",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(contacts, key = { it.id }) { contact ->
                    val selected = contact.id in selectedIds
                    ListItem(
                        headlineContent = { Text(contact.name) },
                        supportingContent = { Text("@${contact.handle}") },
                        leadingContent = { PersonAvatar(contact.name) },
                        trailingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle(contact.id) })
                            .padding(horizontal = 8.dp),
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) { Text("Done") }
        }
    }
}

/**
 * Bottom sheet for choosing who shares one item.
 *
 * @param people everyone sharing the bill, as id to display name.
 * @param initiallySharing ids currently sharing this item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSharersSheet(
    title: String,
    amount: String,
    people: List<Pair<String, String>>,
    initiallySharing: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var sharing by remember { mutableStateOf(initiallySharing) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "$amount, split equally between the people ticked below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(people, key = { it.first }) { (id, name) ->
                    val selected = id in sharing
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { PersonAvatar(name) },
                        trailingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { sharing = if (selected) sharing - id else sharing + id },
                            )
                            .padding(horizontal = 8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { sharing = people.map { it.first }.toSet() }) { Text("Everyone") }
                Button(
                    onClick = { onConfirm(sharing) },
                    enabled = sharing.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Done") }
            }
        }
    }
}
