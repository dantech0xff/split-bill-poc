package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.data.contacts.Contact
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvPersonPickRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton

/** Bottom sheet for choosing which contacts share the bill. Changes apply immediately. */
@Composable
fun PeoplePickerSheet(contacts: List<Contact>, selectedIds: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    SvBottomSheet(
        title = "Pick people to share with",
        onDismiss = onDismiss,
        actions = { SvPrimaryButton(text = "Done", onClick = onDismiss) },
    ) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(contacts, key = { it.id }) { contact ->
                SvPersonPickRow(
                    name = contact.name,
                    detail = "@${contact.handle}",
                    checked = contact.id in selectedIds,
                    onCheckedChange = { onToggle(contact.id) },
                )
            }
        }
    }
}

/**
 * Bottom sheet for choosing who shares one item.
 *
 * @param people everyone sharing the bill, as id to display name.
 * @param initiallySharing ids currently sharing this item.
 */
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

    SvBottomSheet(
        title = title,
        supportingText = "$amount, split equally between the people ticked below.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = { sharing = people.map { it.first }.toSet() }) { Text("Everyone") }
            SvPrimaryButton(
                text = "Done",
                onClick = { onConfirm(sharing) },
                modifier = Modifier.weight(1f),
                enabled = sharing.isNotEmpty(),
            )
        },
    ) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(people, key = { it.first }) { (id, name) ->
                val selected = id in sharing
                SvPersonPickRow(
                    name = name,
                    checked = selected,
                    onCheckedChange = { sharing = if (selected) sharing - id else sharing + id },
                )
            }
        }
    }
}
