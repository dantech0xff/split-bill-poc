package com.dantech.splitvibe.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.data.groups.GroupSummary
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryFab
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.common.ProxyWork

/** The user's groups, such as housemates or a trip, each with balances across its bills. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    groups: List<GroupSummary>?,
    signedIn: Boolean,
    status: ProxyWork,
    myName: String,
    localCurrency: String,
    onCreate: (name: String, currency: String, myName: String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            if (signedIn) SvPrimaryFab(text = "New group", icon = Icons.Default.Add, onClick = { creating = true })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            if (!signedIn) {
                item {
                    SvMessageCard(
                        text = "Groups are shared with other people through Split Vibe's server, so they need a Google sign-in.",
                        isError = false,
                        action = "Sign in in Settings",
                        onAction = onOpenSettings,
                        modifier = Modifier.padding(SvSpacing.lg),
                    )
                }
            }
            status.message?.let { message ->
                item {
                    SvMessageCard(
                        text = message,
                        isError = true,
                        action = "OK",
                        onAction = onDismissMessage,
                        modifier = Modifier.padding(SvSpacing.lg),
                    )
                }
            }
            if (status.working && groups == null) item { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }
            if (signedIn && groups?.isEmpty() == true) {
                item {
                    SvEmptyState(
                        icon = Icons.Default.Person,
                        text = "Make a group for the people you share bills with, such as a trip or a shared flat, and see who owes whom.",
                    )
                }
            }
            val list = groups.orEmpty()
            itemsIndexed(list, key = { _, group -> group.id }) { index, group ->
                val detail = if (group.isPending) "Waiting for the owner to let you in" else group.currency.code
                SvListRow(
                    position = SvRowPosition.of(index, list.size),
                    label = "${group.name}, $detail",
                    onClickLabel = "Open",
                    onClick = { onOpen(group.id) },
                ) {
                    SvAvatar(group.name)
                    Spacer(Modifier.width(SvSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.name, style = MaterialTheme.typography.bodyLarge)
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(FAB_CLEARANCE)) }
        }
    }

    if (creating) {
        NewGroupSheet(
            myName = myName,
            localCurrency = localCurrency,
            onCreate = { name, currency, me ->
                creating = false
                onCreate(name, currency, me)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun NewGroupSheet(myName: String, localCurrency: String, onCreate: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(localCurrency) }
    var me by rememberSaveable { mutableStateOf(myName) }
    val valid = name.isNotBlank() && me.isNotBlank() && BillCurrency.isKnownCode(currency)
    SvBottomSheet(
        title = "New group",
        supportingText = "Bills in other currencies are brought into the group's currency at the day's rate.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = { onCreate(name, currency, me) }, enabled = valid) { Text("Make the group") }
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = SvSpacing.xl), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(NAME_LENGTH) },
                label = { Text("Name of the group") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase().take(3) },
                label = { Text("Currency") },
                singleLine = true,
                isError = !BillCurrency.isKnownCode(currency),
                supportingText = { Text("What balances are kept in, such as THB or EUR.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = me,
                onValueChange = { me = it.take(NAME_LENGTH) },
                label = { Text("Your name in the group") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal const val NAME_LENGTH = 60
private val FAB_CLEARANCE = 88.dp
