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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.groups.GroupBillDto
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.LogEntryDto
import com.dantech.splitvibe.data.groups.MemberDto
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSmallAction
import com.dantech.splitvibe.designsystem.components.SvStatBar
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.BillCurrency
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
import kotlin.math.absoluteValue

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
 * A group: first where the user stands in it, then who is owed and who owes, the fewest payments
 * that settle it, its people and its bills. Paying back is recorded by whoever got the money, and
 * nothing that was paid back can be undone.
 */
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
            SvTopBar(
                title = state?.group?.name ?: stringResource(R.string.groups_group),
                backLabel = stringResource(R.string.common_back),
                onBack = actions.onBack,
                // A group is named by whoever made it, which can be long; the approved design keeps it to one line.
                oneLineTitle = true,
                action = if (state == null) {
                    null
                } else {
                    {
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
                item(key = "message") {
                    SvMessageCard(
                        text = message.resolve(),
                        isError = true,
                        action = stringResource(R.string.common_ok),
                        onAction = actions.onDismissMessage,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
                    )
                }
            }
            if (state == null) {
                item(key = "loading") { if (status.working) SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }
                return@LazyColumn
            }
            val standing = GroupStanding.of(state)
            standing?.let {
                item(key = "standing") {
                    StandingHero(
                        standing = it,
                        currency = state.currency,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
                    )
                }
            }
            balances(state, saysTheCurrency = standing != null)
            settleUp(
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
            item(key = "end") { Spacer(Modifier.height(SvSpacing.xxl)) }
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

        // Both take the person out of the group at once, so going ahead is red.
        is GroupDialog.Remove -> SvDialog(
            title = stringResource(R.string.groups_take_out_title, state?.nameOf(shown.memberId)?.resolve().orEmpty()),
            text = stringResource(R.string.groups_take_out_text),
            confirmLabel = stringResource(R.string.groups_take_out),
            onConfirm = {
                dialog = null
                actions.onRemoveMember(shown.memberId)
            },
            onDismiss = { dialog = null },
            dismissLabel = stringResource(R.string.common_cancel),
            irreversible = true,
        )

        GroupDialog.Leave -> SvDialog(
            title = stringResource(R.string.groups_leave_title),
            text = stringResource(R.string.groups_leave_text),
            confirmLabel = stringResource(R.string.groups_leave_confirm),
            onConfirm = {
                dialog = null
                actions.onLeave()
            },
            onDismiss = { dialog = null },
            dismissLabel = stringResource(R.string.common_cancel),
            irreversible = true,
        )

        is GroupDialog.Received -> {
            val transfer = Transfer(shown.fromMemberId, state?.myMemberId.orEmpty(), shown.amountMinor)
            // Money asked for with a request is recorded by confirming that request, for its amount.
            val asked = openRequests[shown.fromMemberId]
            val amount = asked?.amountMinor ?: shown.amountMinor
            val from = state?.nameOf(shown.fromMemberId)?.resolve().orEmpty()
            SvDialog(
                title = stringResource(R.string.groups_got_it_title, state?.currency?.format(amount).orEmpty()),
                text = stringResource(if (asked != null) R.string.groups_got_it_confirms else R.string.groups_got_it_records, from),
                confirmLabel = stringResource(R.string.groups_i_got_it),
                onConfirm = {
                    dialog = null
                    actions.onMarkReceived(transfer, shown.settlementId)
                },
                onDismiss = { dialog = null },
                dismissLabel = stringResource(R.string.common_cancel),
            )
        }

        null -> Unit
    }
}

/** The block at the top of the group that says where the user stands in it, and what its balances are in. */
@Composable
private fun StandingHero(standing: GroupStanding, currency: BillCurrency, modifier: Modifier) {
    SvHero(
        // A screen reader reads the block as one element.
        modifier = modifier.semantics(mergeDescendants = true) {},
        kind = standing.kind,
        label = { Text(stringResource(standing.label)) },
        amount = { HeroAmount(currency, standing.amountMinor) },
    ) {
        Text(stringResource(R.string.groups_balances_hint, currency.code))
    }
}

/** [minor] of [currency] in large type, as the phone's region writes it, its symbol or code smaller beside the number. */
@Composable
private fun HeroAmount(currency: BillCurrency, minor: Long) {
    val parts = currency.formatParts(minor)
    SvHeroAmount(prefix = parts.prefix, number = parts.number, suffix = parts.suffix, spokenText = currency.format(minor))
}

private fun LazyListScope.balances(state: GroupState, saysTheCurrency: Boolean) {
    val balances = state.balances
    item(key = "balances") {
        // The block above says what the balances are in; without it, the title says it.
        SvSectionHeader(
            stringResource(R.string.groups_balances),
            hint = if (saysTheCurrency) null else stringResource(R.string.groups_balances_hint, state.currency.code),
        )
    }
    if (balances == null) {
        item(key = "too-large") {
            Text(
                stringResource(R.string.groups_too_large),
                modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.sm),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    // Each bar is a part of the largest balance, owed or to get back, so that the bars compare with one another.
    val largest = balances?.values?.maxOfOrNull { it.absoluteValue } ?: 0L
    itemsIndexed(state.members, key = { _, member -> "balance-${member.id}" }) { index, member ->
        BalanceRow(
            name = state.nameOf(member.id).resolve(),
            // Not known while the numbers cannot be added up, which is not the same as settled up.
            balance = balances?.get(member.id),
            largest = largest,
            currency = state.currency,
            position = SvRowPosition.of(index, state.members.size),
        )
    }
}

/**
 * A member's balance: their name, what they get back or owe in words with the amount, and a bar of
 * it beside the largest balance in the group, which fills from the start for what they get back and
 * from the end for what they owe. The words say it all; the bar only draws it. While the [balance] is
 * not known, as when the group's numbers cannot be added up, the row shows the name alone.
 */
@Composable
private fun BalanceRow(name: String, balance: Long?, largest: Long, currency: BillCurrency, position: SvRowPosition) {
    if (balance == null) {
        SvListRow(position = position) {
            SvAvatar(name)
            Spacer(Modifier.width(SvSpacing.md))
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
        }
        return
    }
    val palette = SvTheme.palette
    val amount = currency.format(balance.absoluteValue)
    val said = when {
        balance > 0 -> stringResource(R.string.groups_gets_back, amount)
        balance < 0 -> stringResource(R.string.groups_owes, amount)
        else -> stringResource(R.string.groups_settled_up)
    }
    val amountStyle = when {
        balance > 0 -> MaterialTheme.typography.amountSmall.toSpanStyle().copy(color = palette.moneyIn)
        balance < 0 -> MaterialTheme.typography.amountSmall.toSpanStyle().copy(color = palette.moneyOut)
        else -> null
    }
    val standing = remember(said, amount, amountStyle) { withStyledPart(said, amount, amountStyle) }
    SvListRow(position = position) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        // One element for a screen reader: the name and what they get back or owe.
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(BAR_GAP),
        ) {
            // Side by side while both fit, as with larger text the balance goes below the name.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, modifier = Modifier.padding(end = SvSpacing.sm), style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(standing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SvStatBar(
                fraction = if (largest > 0) balance.absoluteValue.toFloat() / largest else 0f,
                description = null,
                // The boards' green for what comes back, the same in both themes; its words take moneyIn.
                color = if (balance > 0) palette.moneyInBar else MaterialTheme.colorScheme.onSurface,
                fromEnd = balance < 0,
            )
        }
    }
}

/** [text] with its first [part] in [style]; as it is without a style, or when [part] is not in it. */
private fun withStyledPart(text: String, part: String, style: SpanStyle?): AnnotatedString = buildAnnotatedString {
    append(text)
    val start = text.indexOf(part)
    if (style != null && start >= 0) addStyle(style, start, start + part.length)
}

private fun LazyListScope.settleUp(state: GroupState, working: Boolean, onMarkReceived: (Transfer) -> Unit, onAskFor: (Transfer) -> Unit) {
    val transfers = state.suggestedTransfers
    val hint = when {
        // No payments are suggested then either, but nobody is known to be square.
        state.balances == null -> R.string.groups_settle_up_unknown

        transfers.isEmpty() -> R.string.groups_square

        else -> R.string.groups_fewest_payments
    }
    item(key = "settle-up") {
        SvSectionHeader(stringResource(R.string.groups_settle_up), hint = stringResource(hint))
    }
    itemsIndexed(transfers, key = { _, transfer -> "${transfer.fromMemberId}>${transfer.toMemberId}" }) { index, transfer ->
        TransferRow(
            state = state,
            transfer = transfer,
            position = SvRowPosition.of(index, transfers.size),
            working = working,
            onMarkReceived = onMarkReceived,
            onAskFor = onAskFor,
        )
    }
}

/**
 * One payment that settles the group: who pays whom, and how much. A payment to the user offers to
 * ask for it and to record it once it arrived; one the user makes says that they can be sent a
 * request with how to pay.
 */
@Composable
private fun TransferRow(
    state: GroupState,
    transfer: Transfer,
    position: SvRowPosition,
    working: Boolean,
    onMarkReceived: (Transfer) -> Unit,
    onAskFor: (Transfer) -> Unit,
) {
    val from = state.nameOf(transfer.fromMemberId).resolve()
    val to = state.nameOf(transfer.toMemberId).resolve()
    SvListRow(position = position) {
        // One element for a screen reader, with its buttons apart.
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                FromTo(from, to)
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.groups_pays, from, to),
                        modifier = Modifier.padding(end = SvSpacing.sm),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                    Text(state.currency.format(transfer.amountMinor), style = MaterialTheme.typography.amountSmall)
                }
            }
            if (transfer.fromMemberId == state.myMemberId) {
                Text(
                    stringResource(R.string.groups_can_send_request, to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (transfer.toMemberId == state.myMemberId) {
                Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    // Off while a call runs, so a second tap cannot ask or record twice.
                    SvSmallAction(
                        stringResource(R.string.groups_ask),
                        onClick = { onAskFor(transfer) },
                        modifier = Modifier.weight(1f),
                        enabled = !working,
                    )
                    SvQuietButton(
                        stringResource(R.string.groups_got_it),
                        onClick = { onMarkReceived(transfer) },
                        modifier = Modifier.weight(1f),
                        enabled = !working,
                    )
                }
            }
        }
    }
}

/** The avatars of who pays and who is paid, with an arrow between them. Decorative: the row says who in words. */
@Composable
private fun FromTo(from: String, to: String) {
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.xs),
    ) {
        SvAvatar(from, size = PAIR_AVATAR_SIZE)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(PAIR_ARROW_SIZE))
        SvAvatar(to, size = PAIR_AVATAR_SIZE)
    }
}

private fun LazyListScope.members(state: GroupState, actions: GroupActions, onAddPlaceholder: () -> Unit, onRemove: (String) -> Unit) {
    item(key = "people") {
        SvSectionHeader(
            stringResource(R.string.groups_people),
            hint = plural(R.plurals.groups_people_count, state.members.size).resolve(),
        )
    }
    itemsIndexed(state.members, key = { _, member -> "member-${member.id}" }) { index, member ->
        MemberRow(
            state = state,
            member = member,
            position = SvRowPosition.of(index, state.members.size),
            onRemove = { onRemove(member.id) },
        )
    }
    state.claims.forEach { claim ->
        item(key = "claim-${claim.memberId}") {
            // The one card here that waits for the owner's answer.
            SvCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.md),
                outlined = true,
            ) {
                Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                    Text(
                        stringResource(R.string.groups_asks_to_be, claim.claimantName, state.nameOf(claim.memberId).resolve()),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        SvPrimaryButton(
                            stringResource(R.string.groups_let_in),
                            onClick = { actions.onApprove(claim.memberId) },
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                        SvQuietButton(
                            stringResource(R.string.groups_turn_down),
                            onClick = { actions.onReject(claim.memberId) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    item(key = "add-people") {
        FlowRow(
            modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            SvSmallAction(stringResource(R.string.groups_invite), onClick = actions.onInvite, icon = SvActionIcons.Plus)
            SvQuietButton(stringResource(R.string.groups_keep_place), onClick = onAddPlaceholder)
        }
    }
}

/** A member: their name, tags for the owner and for a kept place without the app, and what the owner may do with them. */
@Composable
private fun MemberRow(state: GroupState, member: MemberDto, position: SvRowPosition, onRemove: () -> Unit) {
    val name = state.nameOf(member.id).resolve()
    SvListRow(position = position) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        // One element for a screen reader: the name and its tags.
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
            if (member.isOwner) SvTag(stringResource(R.string.groups_owner), kind = SvTagKind.Strong)
            if (!member.hasAccount) SvTag(stringResource(R.string.groups_no_app_yet))
        }
        // The owner can take anyone else with the app out of the group.
        if (state.isOwner && member.hasAccount && member.id != state.myMemberId) {
            SvOverflowMenu(
                label = stringResource(R.string.groups_more_for, member.name),
                options = listOf(SvMenuOption(stringResource(R.string.groups_take_out_of_group), onRemove)),
            )
        }
    }
}

private fun LazyListScope.bills(state: GroupState, onDelete: (String) -> Unit) {
    val bills = state.bills.sortedByDescending { it.updatedAt }
    item(key = "bills") {
        SvSectionHeader(
            stringResource(R.string.groups_bills),
            hint = if (bills.isEmpty()) stringResource(R.string.groups_bills_hint) else null,
        )
    }
    itemsIndexed(bills, key = { _, bill -> "bill-${bill.id}" }) { index, bill ->
        BillRow(state = state, bill = bill, position = SvRowPosition.of(index, bills.size), onDelete = { onDelete(bill.id) })
    }
}

/**
 * A bill of the group: who added it, by their letters, as a bill from the proxy carries no category,
 * its name, and its total. A bill left out of the balances says so in place of who added it.
 */
@Composable
private fun BillRow(state: GroupState, bill: GroupBillDto, position: SvRowPosition, onDelete: () -> Unit) {
    val title = titleOf(bill).resolve()
    val adder = state.nameOf(bill.ownerMemberId).resolve()
    val counted = bill in state.countedBills
    val mayDelete = bill.ownerMemberId == state.myMemberId || state.isOwner
    SvListRow(position = position) {
        SvAvatar(adder)
        Spacer(Modifier.width(SvSpacing.md))
        // One element for a screen reader: the bill and its total, with its menu apart.
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(
                    if (counted) stringResource(R.string.groups_added_by, adder) else stringResource(R.string.groups_left_out),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (counted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Text(state.currency.format(bill.totalMinor), style = MaterialTheme.typography.amountSmall)
        }
        if (mayDelete) {
            SvOverflowMenu(
                label = stringResource(R.string.groups_more_for, title),
                options = listOf(SvMenuOption(stringResource(R.string.groups_delete_bill), onDelete)),
            )
        }
    }
}

private fun LazyListScope.activity(state: GroupState) {
    if (state.log.isEmpty()) return
    item(key = "recent-changes") { SvSectionHeader(stringResource(R.string.groups_recent_changes)) }
    item(key = "log") {
        Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
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

/** Asks for a name, such as the group's new one; the button that goes ahead waits until one is typed. */
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
    SvDialog(
        title = title,
        text = supporting,
        confirmLabel = confirm,
        onConfirm = { onConfirm(name) },
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.common_cancel),
        confirmEnabled = name.isNotBlank(),
    ) {
        SvTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
            modifier = Modifier.fillMaxWidth(),
            maxLength = NAME_LENGTH,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
    }
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
private val BAR_GAP = 6.dp
private val PAIR_AVATAR_SIZE = 32.dp
private val PAIR_ARROW_SIZE = 16.dp
