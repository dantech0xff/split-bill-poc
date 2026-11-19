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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dantech.splitvibe.R
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
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.dateText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.common.ProxyWork
import java.time.Instant
import java.time.ZoneId
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
                title = { Text(state?.group?.name ?: stringResource(R.string.groups_group)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state != null) {
                        SvOverflowMenu(
                            label = stringResource(R.string.groups_more),
                            options = listOfNotNull(
                                SvMenuOption(stringResource(R.string.groups_reload)) { actions.onRefresh() },
                                SvMenuOption(stringResource(R.string.groups_rename)) {
                                    dialog = GroupDialog.Rename
                                }.takeIf { state.isOwner },
                                SvMenuOption(stringResource(R.string.groups_stop_invites)) {
                                    actions.onRevokeInvites()
                                }.takeIf { state.isOwner },
                                SvMenuOption(stringResource(R.string.groups_leave)) { dialog = GroupDialog.Leave },
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
                        text = message.resolve(),
                        isError = true,
                        action = stringResource(R.string.common_ok),
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
            title = stringResource(R.string.groups_rename),
            initial = state?.group?.name.orEmpty(),
            confirm = stringResource(R.string.groups_rename_confirm),
            onConfirm = {
                dialog = null
                actions.onRename(it)
            },
            onDismiss = { dialog = null },
        )

        GroupDialog.Placeholder -> NameDialog(
            title = stringResource(R.string.groups_keep_place),
            supporting = stringResource(R.string.groups_keep_place_hint),
            initial = "",
            confirm = stringResource(R.string.common_add),
            onConfirm = {
                dialog = null
                actions.onAddPlaceholder(it)
            },
            onDismiss = { dialog = null },
        )

        is GroupDialog.Remove -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.groups_take_out_title, state?.nameOf(shown.memberId)?.resolve().orEmpty())) },
            text = { Text(stringResource(R.string.groups_take_out_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        actions.onRemoveMember(shown.memberId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.groups_take_out)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
        )

        GroupDialog.Leave -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.groups_leave_title)) },
            text = { Text(stringResource(R.string.groups_leave_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        actions.onLeave()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.groups_leave_confirm)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
        )

        is GroupDialog.Received -> {
            val transfer = Transfer(shown.fromMemberId, state?.myMemberId.orEmpty(), shown.amountMinor)
            // Money asked for with a request is recorded by confirming that request, for its amount.
            val asked = openRequests[shown.fromMemberId]
            val amount = asked?.amountMinor ?: shown.amountMinor
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.groups_got_it_title, state?.currency?.format(amount).orEmpty())) },
                text = {
                    val from = state?.nameOf(shown.fromMemberId)?.resolve().orEmpty()
                    Text(stringResource(if (asked != null) R.string.groups_got_it_confirms else R.string.groups_got_it_records, from))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialog = null
                            actions.onMarkReceived(transfer, shown.settlementId)
                        },
                    ) { Text(stringResource(R.string.groups_i_got_it)) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
            )
        }

        null -> Unit
    }
}

private fun LazyListScope.balances(state: GroupState, working: Boolean, onMarkReceived: (Transfer) -> Unit, onAskFor: (Transfer) -> Unit) {
    item {
        SvSectionHeader(stringResource(R.string.groups_balances), hint = stringResource(R.string.groups_balances_hint, state.currency.code))
        val balances = state.balances
        SvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = SvSpacing.lg)) {
            Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                if (balances == null) {
                    Text(stringResource(R.string.groups_too_large), style = MaterialTheme.typography.bodyMedium)
                }
                state.members.forEach { member ->
                    val balance = balances?.get(member.id) ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvAvatar(member.name)
                        Spacer(Modifier.width(SvSpacing.md))
                        Text(state.nameOf(member.id).resolve(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                balance > 0 -> stringResource(R.string.groups_gets_back, state.currency.format(balance))
                                balance < 0 -> stringResource(R.string.groups_owes, state.currency.format(-balance))
                                else -> stringResource(R.string.groups_settled_up)
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
            stringResource(R.string.groups_settle_up),
            hint = stringResource(if (transfers.isEmpty()) R.string.groups_square else R.string.groups_fewest_payments),
        )
    }
    itemsIndexed(transfers, key = { _, transfer -> "${transfer.fromMemberId}>${transfer.toMemberId}" }) { index, transfer ->
        SvListRow(position = SvRowPosition.of(index, transfers.size)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.groups_pays,
                        state.nameOf(transfer.fromMemberId).resolve(),
                        state.nameOf(transfer.toMemberId).resolve(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(state.currency.format(transfer.amountMinor), style = MaterialTheme.typography.amountMedium)
                if (transfer.fromMemberId == state.myMemberId) {
                    Text(
                        stringResource(R.string.groups_can_send_request, state.nameOf(transfer.toMemberId).resolve()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (transfer.toMemberId == state.myMemberId) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    // Off while a call runs, so a second tap cannot ask or record twice.
                    OutlinedButton(onClick = { onAskFor(transfer) }, enabled = !working) { Text(stringResource(R.string.groups_ask)) }
                    FilledTonalButton(onClick = { onMarkReceived(transfer) }, enabled = !working) {
                        Text(stringResource(R.string.groups_got_it))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.members(state: GroupState, actions: GroupActions, onAddPlaceholder: () -> Unit, onRemove: (String) -> Unit) {
    item {
        SvSectionHeader(
            stringResource(R.string.groups_people),
            hint = plural(R.plurals.groups_people_count, state.members.size).resolve(),
        )
    }
    itemsIndexed(state.members, key = { _, member -> member.id }) { index, member ->
        val detail = listOfNotNull(
            stringResource(R.string.groups_owner).takeIf { member.isOwner },
            stringResource(R.string.groups_no_app_yet).takeIf { !member.hasAccount },
        ).joinToString(", ").ifEmpty { null }
        SvListRow(position = SvRowPosition.of(index, state.members.size)) {
            SvAvatar(member.name)
            Spacer(Modifier.width(SvSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.nameOf(member.id).resolve(), style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // The owner can take anyone else with the app out of the group.
            if (state.isOwner && member.hasAccount && member.id != state.myMemberId) {
                SvOverflowMenu(
                    label = stringResource(R.string.groups_more_for, member.name),
                    options = listOf(SvMenuOption(stringResource(R.string.groups_take_out_of_group)) { onRemove(member.id) }),
                )
            }
        }
    }
    state.claims.forEach { claim ->
        item(key = "claim-${claim.memberId}") {
            SvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm)) {
                Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    Text(
                        stringResource(R.string.groups_asks_to_be, claim.claimantName, state.nameOf(claim.memberId).resolve()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val balance = state.balances?.get(claim.memberId) ?: 0L
                    if (balance > 0) {
                        Text(
                            stringResource(
                                R.string.groups_claim_owed,
                                state.nameOf(claim.memberId).resolve(),
                                state.currency.format(balance),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                        Button(onClick = { actions.onApprove(claim.memberId) }) { Text(stringResource(R.string.groups_let_in)) }
                        OutlinedButton(onClick = { actions.onReject(claim.memberId) }) { Text(stringResource(R.string.groups_turn_down)) }
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
            FilledTonalButton(onClick = actions.onInvite) { Text(stringResource(R.string.groups_invite)) }
            OutlinedButton(onClick = onAddPlaceholder) { Text(stringResource(R.string.groups_keep_place)) }
        }
    }
}

private fun LazyListScope.bills(state: GroupState, onDelete: (String) -> Unit) {
    val bills = state.bills.sortedByDescending { it.updatedAt }
    item {
        SvSectionHeader(
            stringResource(R.string.groups_bills),
            hint = if (bills.isEmpty()) stringResource(R.string.groups_bills_hint) else null,
        )
    }
    itemsIndexed(bills, key = { _, bill -> bill.id }) { index, bill ->
        val counted = bill in state.countedBills
        val mayDelete = bill.ownerMemberId == state.myMemberId || state.isOwner
        SvListRow(position = SvRowPosition.of(index, bills.size)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(titleOf(bill).resolve(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (counted) {
                        stringResource(R.string.groups_added_by, state.nameOf(bill.ownerMemberId).resolve())
                    } else {
                        stringResource(R.string.groups_left_out)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (counted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Text(state.currency.format(bill.totalMinor), style = MaterialTheme.typography.amountMedium)
            if (mayDelete) {
                SvOverflowMenu(
                    label = stringResource(R.string.groups_more_for, titleOf(bill).resolve()),
                    options = listOf(SvMenuOption(stringResource(R.string.groups_delete_bill)) { onDelete(bill.id) }),
                )
            }
        }
    }
}

private fun LazyListScope.activity(state: GroupState) {
    if (state.log.isEmpty()) return
    item { SvSectionHeader(stringResource(R.string.groups_recent_changes)) }
    item {
        Column(modifier = Modifier.padding(horizontal = SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            state.log.take(LOG_SHOWN).forEach { entry ->
                Text(
                    stringResource(
                        R.string.groups_log_line,
                        describe(entry, state).resolve(),
                        dateText(Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault())),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What a change in the group was, in a sentence. */
internal fun describe(entry: LogEntryDto, state: GroupState): UiText {
    val who = state.nameOf(entry.memberId)
    val subject = state.member(entry.subjectId)?.let { state.nameOf(it.id) }
    val bill = state.bills.firstOrNull { it.id == entry.subjectId }?.let(::titleOf) ?: text(R.string.groups_log_a_bill)
    val someone = subject ?: text(R.string.groups_log_someone)
    val aMember = subject ?: text(R.string.groups_log_a_member)
    return when (entry.kind) {
        "group_created" -> text(R.string.groups_log_created, who)
        "member_added" -> text(R.string.groups_log_member_added, who, someone)
        "member_joined" -> text(R.string.groups_log_member_joined, who)
        "member_left" -> text(R.string.groups_log_member_left, who)
        "member_removed" -> text(R.string.groups_log_member_removed, who, someone)
        "member_renamed" -> text(R.string.groups_log_member_renamed, who, subject ?: text(R.string.groups_log_another_one))
        "claim_requested" -> text(R.string.groups_log_claim_requested, aMember)
        "claim_approved" -> text(R.string.groups_log_claim_approved, who, someone)
        "claim_rejected" -> text(R.string.groups_log_claim_rejected, who, aMember)
        "bill_added" -> text(R.string.groups_log_bill_added, who, bill)
        "bill_changed" -> text(R.string.groups_log_bill_changed, who, bill)
        "bill_deleted" -> text(R.string.groups_log_bill_deleted, who)
        "settlement_recorded" -> text(R.string.groups_log_paid_back, who)
        else -> text(R.string.groups_log_changed, who)
    }
}

/** The bill's merchant as its document gives it, else a plain name. */
internal fun titleOf(bill: GroupBillDto): UiText = bill.title?.let(UiText::Raw) ?: text(R.string.groups_a_bill)

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
                    label = { Text(stringResource(R.string.common_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
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
