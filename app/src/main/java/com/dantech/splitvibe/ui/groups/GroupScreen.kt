package com.dantech.splitvibe.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dantech.splitvibe.data.groups.GroupBillDto
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.LogEntryDto
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.domain.Transfer
import com.dantech.splitvibe.ui.common.ProxyWork
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** What the group screen can ask for. */
class GroupActions(
    val onInvite: () -> Unit,
    val onRevokeInvites: () -> Unit,
    val onRename: (String) -> Unit,
    val onAddPlaceholder: (String) -> Unit,
    val onApprove: (String) -> Unit,
    val onRemoveMember: (String) -> Unit,
    val onReject: (String) -> Unit,
    val onDeleteBill: (String) -> Unit,
    val onMarkReceived: (Transfer, settlementId: String) -> Unit,
    val onAskFor: (Transfer) -> Unit,
    val onLeave: () -> Unit,
    val onRefresh: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * A group: who is owed and who owes, the fewest payments that settle it, its people and its bills.
 * Paying back is recorded by whoever got the money, and nothing that was paid back can be undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: GroupState?,
    status: ProxyWork,
    actions: GroupActions,
    /** The user's open requests in the group, by the member they ask. */
    openRequests: Map<String, RequestView> = emptyMap(),
) {
    var dialog by rememberSaveable { mutableStateOf<GroupDialog?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.group?.name ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state != null) {
                        SvOverflowMenu(
                            label = "More for this group",
                            options = listOfNotNull(
                                SvMenuOption("Reload") { actions.onRefresh() },
                                SvMenuOption("Rename the group") { dialog = GroupDialog.Rename }.takeIf { state.isOwner },
                                SvMenuOption("Stop the invite links") { actions.onRevokeInvites() }.takeIf { state.isOwner },
                                SvMenuOption("Leave the group") { dialog = GroupDialog.Leave },
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            status.message?.let { message ->
                item {
                    SvMessageCard(
                        text = message,
                        isError = true,
                        action = "OK",
                        onAction = actions.onDismissMessage,
                        modifier = Modifier.padding(SvSpacing.lg),
                    )
                }
            }
            if (state == null) {
                item { if (status.working) SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }
                return@LazyColumn
            }
            balances(
                state,
                working = status.working,
                // The id of the payment back is made once, when the user chooses to record it.
                onMarkReceived = { dialog = GroupDialog.Received(it.fromMemberId, it.amountMinor, UUID.randomUUID().toString()) },
                onAskFor = actions.onAskFor,
            )
            members(
                state,
                actions,
                onAddPlaceholder = { dialog = GroupDialog.Placeholder },
                onRemove = { dialog = GroupDialog.Remove(it) },
            )
            bills(state, onDelete = actions.onDeleteBill)
            activity(state)
            item { Spacer(Modifier.height(SvSpacing.xxl)) }
        }
    }

    when (val shown = dialog) {
        GroupDialog.Rename -> NameDialog(
            title = "Rename the group",
            initial = state?.group?.name.orEmpty(),
            confirm = "Rename",
            onConfirm = {
                dialog = null
                actions.onRename(it)
            },
            onDismiss = { dialog = null },
        )

        GroupDialog.Placeholder -> NameDialog(
            title = "Keep a place for someone",
            supporting = "For someone without the app. When they join with an invite link, they can ask for this place, and you decide.",
            initial = "",
            confirm = "Add",
            onConfirm = {
                dialog = null
                actions.onAddPlaceholder(it)
            },
            onDismiss = { dialog = null },
        )

        is GroupDialog.Remove -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Take ${state?.nameOf(shown.memberId)} out of the group?") },
            text = {
                Text(
                    "They will no longer see the group. What they paid and owe stays under their name, so nobody's " +
                        "balance changes, and their place can be given back through an invite.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        actions.onRemoveMember(shown.memberId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Take out") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        GroupDialog.Leave -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Leave the group?") },
            text = { Text("What you paid and owe stays in the group under your name, so the others' balances do not change.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        actions.onLeave()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )

        is GroupDialog.Received -> {
            val transfer = Transfer(shown.fromMemberId, state?.myMemberId.orEmpty(), shown.amountMinor)
            // Money asked for with a request is recorded by confirming that request, for its amount.
            val asked = openRequests[shown.fromMemberId]
            val amount = asked?.amountMinor ?: shown.amountMinor
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Did you get ${state?.currency?.format(amount)}?") },
                text = {
                    Text(
                        if (asked != null) {
                            "This confirms the request you sent ${state?.nameOf(
                                shown.fromMemberId,
                            )}, which records the payment. It cannot be undone."
                        } else {
                            "This records that ${state?.nameOf(shown.fromMemberId)} paid you back. It cannot be undone."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialog = null
                            actions.onMarkReceived(transfer, shown.settlementId)
                        },
                    ) { Text("I got it") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }

        null -> Unit
    }
}

private fun LazyListScope.balances(state: GroupState, working: Boolean, onMarkReceived: (Transfer) -> Unit, onAskFor: (Transfer) -> Unit) {
    item {
        SvSectionHeader("Balances", hint = "In ${state.currency.code}, across every bill and payment back.")
        val balances = state.balances
        SvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = SvSpacing.lg)) {
            Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                if (balances == null) {
                    Text("These numbers are too large to add up.", style = MaterialTheme.typography.bodyMedium)
                }
                state.members.forEach { member ->
                    val balance = balances?.get(member.id) ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvAvatar(member.name)
                        Spacer(Modifier.width(SvSpacing.md))
                        Text(state.nameOf(member.id), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                balance > 0 -> "gets back ${state.currency.format(balance)}"
                                balance < 0 -> "owes ${state.currency.format(-balance)}"
                                else -> "settled up"
                            },
                            style = MaterialTheme.typography.amountMedium,
                            color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
    val transfers = state.suggestedTransfers
    item {
        SvSectionHeader(
            "Settle up",
            hint = if (transfers.isEmpty()) "Everyone is square." else "The fewest payments that settle the group.",
        )
    }
    itemsIndexed(transfers, key = { _, transfer -> "${transfer.fromMemberId}>${transfer.toMemberId}" }) { index, transfer ->
        SvListRow(position = SvRowPosition.of(index, transfers.size)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${state.nameOf(transfer.fromMemberId)} pays ${state.nameOf(transfer.toMemberId)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(state.currency.format(transfer.amountMinor), style = MaterialTheme.typography.amountMedium)
                if (transfer.fromMemberId == state.myMemberId) {
                    Text(
                        "${state.nameOf(transfer.toMemberId)} can send you a request with how to pay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (transfer.toMemberId == state.myMemberId) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    // Off while a call runs, so a second tap cannot ask or record twice.
                    OutlinedButton(onClick = { onAskFor(transfer) }, enabled = !working) { Text("Ask") }
                    FilledTonalButton(onClick = { onMarkReceived(transfer) }, enabled = !working) { Text("Got it") }
                }
            }
        }
    }
}

private fun LazyListScope.members(state: GroupState, actions: GroupActions, onAddPlaceholder: () -> Unit, onRemove: (String) -> Unit) {
    item { SvSectionHeader("People", hint = "${state.members.size} in the group") }
    itemsIndexed(state.members, key = { _, member -> member.id }) { index, member ->
        val detail = listOfNotNull(
            "owner".takeIf { member.isOwner },
            "no app yet".takeIf { !member.hasAccount },
        ).joinToString(", ").ifEmpty { null }
        SvListRow(position = SvRowPosition.of(index, state.members.size)) {
            SvAvatar(member.name)
            Spacer(Modifier.width(SvSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.nameOf(member.id), style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // The owner can take anyone else with the app out of the group.
            if (state.isOwner && member.hasAccount && member.id != state.myMemberId) {
                SvOverflowMenu(
                    label = "More for ${member.name}",
                    options = listOf(SvMenuOption("Take out of the group") { onRemove(member.id) }),
                )
            }
        }
    }
    state.claims.forEach { claim ->
        item(key = "claim-${claim.memberId}") {
            SvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm)) {
                Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    Text(
                        "${claim.claimantName} asks to be ${state.nameOf(claim.memberId)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val balance = state.balances?.get(claim.memberId) ?: 0L
                    if (balance > 0) {
                        Text(
                            "${state.nameOf(claim.memberId)} is owed ${state.currency.format(balance)}, which would then be theirs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                        Button(onClick = { actions.onApprove(claim.memberId) }) { Text("Let them in") }
                        OutlinedButton(onClick = { actions.onReject(claim.memberId) }) { Text("Turn down") }
                    }
                }
            }
        }
    }
    item {
        FlowRow(
            modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            FilledTonalButton(onClick = actions.onInvite) { Text("Invite people") }
            OutlinedButton(onClick = onAddPlaceholder) { Text("Keep a place for someone") }
        }
    }
}

private fun LazyListScope.bills(state: GroupState, onDelete: (String) -> Unit) {
    val bills = state.bills.sortedByDescending { it.updatedAt }
    item { SvSectionHeader("Bills", hint = if (bills.isEmpty()) "Add a bill to the group from its split." else null) }
    itemsIndexed(bills, key = { _, bill -> bill.id }) { index, bill ->
        val counted = bill in state.countedBills
        val mayDelete = bill.ownerMemberId == state.myMemberId || state.isOwner
        SvListRow(position = SvRowPosition.of(index, bills.size)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(titleOf(bill), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (counted) "Added by ${state.nameOf(bill.ownerMemberId)}" else "Left out: its amounts do not add up",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (counted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Text(state.currency.format(bill.totalMinor), style = MaterialTheme.typography.amountMedium)
            if (mayDelete) {
                SvOverflowMenu(
                    label = "More for ${titleOf(bill)}",
                    options = listOf(SvMenuOption("Delete from the group") { onDelete(bill.id) }),
                )
            }
        }
    }
}

private fun LazyListScope.activity(state: GroupState) {
    if (state.log.isEmpty()) return
    item { SvSectionHeader("Recent changes") }
    item {
        Column(modifier = Modifier.padding(horizontal = SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            state.log.take(LOG_SHOWN).forEach { entry ->
                Text(
                    "${describe(entry, state)} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.at))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What a change in the group was, in a sentence. */
internal fun describe(entry: LogEntryDto, state: GroupState): String {
    val who = state.nameOf(entry.memberId)
    val subject = state.member(entry.subjectId)?.let { state.nameOf(it.id) }
    val bill = state.bills.firstOrNull { it.id == entry.subjectId }?.let(::titleOf) ?: "a bill"
    return when (entry.kind) {
        "group_created" -> "$who made the group"
        "member_added" -> "$who kept a place for ${subject ?: "someone"}"
        "member_joined" -> "$who joined"
        "member_left" -> "$who left"
        "member_removed" -> "$who took ${subject ?: "someone"} out of the group"
        "member_renamed" -> "$who changed a name to ${subject ?: "another one"}"
        "claim_requested" -> "Someone asked to be ${subject ?: "a member"}"
        "claim_approved" -> "$who let ${subject ?: "someone"} in"
        "claim_rejected" -> "$who turned down a request to be ${subject ?: "a member"}"
        "bill_added" -> "$who added $bill"
        "bill_changed" -> "$who changed $bill"
        "bill_deleted" -> "$who deleted a bill"
        "settlement_recorded" -> "$who got paid back"
        else -> "$who changed the group"
    }
}

/** The bill's merchant as its document gives it, else a plain name. */
internal fun titleOf(bill: GroupBillDto): String = bill.title ?: "A bill"

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    supporting: String? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NAME_LENGTH) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The dialog open over the group, kept when the phone turns. */
private sealed interface GroupDialog : java.io.Serializable {
    data object Rename : GroupDialog {
        private fun readResolve(): Any = Rename
    }

    data object Placeholder : GroupDialog {
        private fun readResolve(): Any = Placeholder
    }

    data object Leave : GroupDialog {
        private fun readResolve(): Any = Leave
    }

    data class Received(val fromMemberId: String, val amountMinor: Long, val settlementId: String) : GroupDialog

    data class Remove(val memberId: String) : GroupDialog
}

private const val LOG_SHOWN = 20
