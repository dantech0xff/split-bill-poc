package com.dantech.splitvibe.ui.claim

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink.Companion.NEW_PERSON
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.data.share.ShareClaim
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.share.ShareCardRenderer
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.split.nameOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the screen of a bill's link does when the person taps something. */
class ClaimActions(
    val onMatch: (claimId: String, participantId: String) -> Unit,
    val onRemove: (claimId: String) -> Unit,
    val onLock: () -> Unit,
    val onSend: () -> Unit,
    val onReopen: () -> Unit,
    val onRevoke: () -> Unit,
    val onRetry: () -> Unit,
    val onMakeNewLink: () -> Unit,
    val onChooseMethod: (id: String) -> Unit,
    val onAddMethod: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onBack: () -> Unit,
)

/** How the page will ask people to pay: the chosen method among the user's, and whether the page shows an older one. */
class ClaimPayment(val methods: List<SavedPaymentMethod>?, val chosen: SavedPaymentMethod?, val changed: Boolean)

/** Something the user is asked to confirm, because it cannot be undone or replaces their work. */
private sealed interface Confirm {
    data object Revoke : Confirm

    data object Reopen : Confirm

    data class Remove(val claimId: String, val name: String) : Confirm
}

/**
 * The bill's link: everyone at the table opens it in a browser and taps what they had, and this
 * screen shows their picks as they come. The user says who each one is on the bill, stops the
 * picking, checks the split, and sends what each person owes to the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimScreen(state: ClaimState, saved: SavedBill?, payment: ClaimPayment, actions: ClaimActions, locale: Locale = Locale.getDefault()) {
    val share = state.share
    var confirming by rememberSaveable(stateSaver = ConfirmSaver) { mutableStateOf<Confirm?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.claim_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (share != null) {
                        SvOverflowMenu(
                            label = stringResource(R.string.claim_more),
                            options = listOf(
                                SvMenuOption(stringResource(R.string.claim_stop_sharing)) { confirming = Confirm.Revoke },
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (share != null && saved != null) {
                ClaimBar(share, state.working, payment.changed, actions, onReopen = { confirming = Confirm.Reopen })
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(SvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            when {
                state.needsSignIn -> item {
                    SvMessageCard(
                        text = stringResource(R.string.claim_sign_in),
                        isError = false,
                        action = stringResource(R.string.common_sign_in_in_settings),
                        onAction = actions.onOpenSettings,
                    )
                }

                state.cannotShare -> item {
                    SvMessageCard(
                        text = stringResource(R.string.claim_cannot_share),
                        isError = false,
                    )
                }

                state.otherAccount -> item {
                    SvMessageCard(
                        text = stringResource(R.string.claim_other_account),
                        isError = false,
                        action = stringResource(R.string.claim_make_new_link),
                        onAction = actions.onMakeNewLink,
                    )
                }
            }
            state.message?.let { message ->
                // Before the link exists, the message is about making it, which can be tried again.
                val retry = share == null && !state.otherAccount
                item {
                    SvMessageCard(
                        text = message.resolve(),
                        isError = true,
                        action = stringResource(if (retry) R.string.common_try_again else R.string.common_dismiss),
                        onAction = if (retry) actions.onRetry else actions.onDismissMessage,
                    )
                }
            }
            if (share == null || saved == null) {
                if (state.working) item { SvLoadingIndicator() }
                return@LazyColumn
            }
            item { LinkCard(state, share, saved, locale) }
            if (!share.isOpen) item { PaymentCard(share, saved, payment, actions) }
            val claims = share.claims
            if (claims.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.claim_nobody_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = SvSpacing.md),
                    )
                }
            } else {
                item {
                    val hint = stringResource(if (share.isOpen) R.string.claim_names_typed_say_who else R.string.claim_names_typed)
                    SvSectionHeader(stringResource(R.string.claim_picked_so_far), hint = hint)
                }
                itemsIndexed(claims, key = { _, claim -> claim.id }) { index, claim ->
                    ClaimRow(
                        claim = claim,
                        position = SvRowPosition.of(index, claims.size),
                        saved = saved,
                        share = share,
                        match = state.matches[claim.id],
                        onMatch = actions.onMatch,
                        onRemove = { confirming = Confirm.Remove(claim.id, claim.name) },
                    )
                }
            }
            val picked = claims.flatMap { it.itemIds }.toSet()
            val unpicked = share.bill.items.filter { it.id !in picked }
            if (unpicked.isNotEmpty() && claims.isNotEmpty()) {
                item {
                    SvSectionHeader(stringResource(R.string.claim_nobody_picked), hint = stringResource(R.string.claim_nobody_picked_hint))
                }
                itemsIndexed(unpicked, key = { _, item -> "item-${item.id}" }) { index, item ->
                    SvListRow(position = SvRowPosition.of(index, unpicked.size)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(saved.bill.format(item.amountMinor), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item { Spacer(Modifier.height(SvSpacing.xxl)) }
        }
    }
    confirming?.let { asked ->
        ConfirmDialog(
            asked = asked,
            onConfirm = {
                confirming = null
                when (asked) {
                    Confirm.Revoke -> actions.onRevoke()
                    Confirm.Reopen -> actions.onReopen()
                    is Confirm.Remove -> actions.onRemove(asked.claimId)
                }
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun LinkCard(state: ClaimState, share: ShareView, saved: SavedBill, locale: Locale) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboardManager.current
    val url = state.url
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text(statusOf(share, locale).resolve(), style = MaterialTheme.typography.titleMedium)
            if (share.isOpen && !state.connected) {
                Text(
                    stringResource(R.string.claim_reconnecting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.claim_send_link_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (url != null) {
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    FilledTonalButton(onClick = {
                        val title = saved.bill.merchant?.takeIf { it.isNotBlank() }
                            ?: resources.getString(R.string.requests_message_the_bill)
                        try {
                            context.startActivity(
                                ShareCardRenderer.shareIntent(
                                    resources.getString(R.string.claim_share_message, title, url),
                                    title = resources.getString(R.string.claim_share_link),
                                ),
                            )
                        } catch (e: ActivityNotFoundException) {
                            // No app on this phone takes text; the link can still be copied.
                        }
                    }) { Text(stringResource(R.string.claim_share_link)) }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text(stringResource(R.string.claim_copy)) }
                }
            }
        }
    }
}

/** How the page asks people to pay, once picking is closed: with the user's method, or, when someone else is owed, not at all. */
@Composable
private fun PaymentCard(share: ShareView, saved: SavedBill, payment: ClaimPayment, actions: ClaimActions) {
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            val group = saved.group
            val payer = saved.plan.payments.singleOrNull()?.participantId?.let { id -> saved.plan.participants.firstOrNull { it.id == id } }
            when {
                group != null -> Text(
                    stringResource(R.string.claim_in_group, group.groupName),
                    style = MaterialTheme.typography.bodyMedium,
                )

                !ClaimViewModel.asksForMoney(saved) -> Text(
                    if (payer != null) {
                        stringResource(
                            R.string.claim_other_payer,
                            payer.name.ifBlank { stringResource(R.string.claim_someone_else) },
                            payer.name.ifBlank { stringResource(R.string.claim_whoever_paid) },
                        )
                    } else {
                        stringResource(R.string.claim_several_payers)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                payment.chosen == null -> {
                    Text(
                        stringResource(R.string.claim_add_method_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = actions.onAddMethod) { Text(stringResource(R.string.requests_add_method)) }
                }

                else -> {
                    val method = payment.chosen.method
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.claim_pay_you_by, method.title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                method.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val others = payment.methods.orEmpty().filter { it.id != payment.chosen.id }
                        if (others.isNotEmpty() && !share.isLocked) {
                            SvOverflowMenu(
                                label = stringResource(R.string.claim_another_way),
                                options = others.map { other ->
                                    SvMenuOption(stringResource(R.string.requests_summary_line, other.method.title, other.method.summary)) {
                                        actions.onChooseMethod(other.id)
                                    }
                                },
                            )
                        }
                    }
                    if (payment.changed) {
                        Text(
                            stringResource(R.string.claim_method_changed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClaimRow(
    claim: ShareClaim,
    position: SvRowPosition,
    saved: SavedBill,
    share: ShareView,
    match: String?,
    onMatch: (claimId: String, participantId: String) -> Unit,
    onRemove: () -> Unit,
) {
    val names = share.bill.items.associate { it.id to it.name }
    val items = claim.itemIds.mapNotNull { names[it] }.ifEmpty { listOf(stringResource(R.string.claim_nothing_yet)) }.joinToString(", ")
    val participants = saved.plan.participants
    val addedAsNew = stringResource(R.string.claim_added_as_new)
    val onBill = when (match) {
        null, NEW_PERSON -> if (claim.itemIds.isEmpty()) stringResource(R.string.claim_picked_nothing) else addedAsNew

        else -> participants.firstOrNull { it.id == match }?.let {
            stringResource(R.string.claim_on_bill_as, if (it.isMe) stringResource(R.string.claim_you_in_sentence) else it.name)
        } ?: addedAsNew
    }
    SvListRow(position = position) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            Text(claim.name, style = MaterialTheme.typography.bodyLarge)
            Text(items, style = MaterialTheme.typography.bodyMedium)
            Text(onBill, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Who is who, and who stays on the link, is settled while people pick; once picking stops, the split says it.
        if (!share.isOpen) return@SvListRow
        val options = buildList {
            participants.forEach { participant ->
                val name = nameOf(participant).resolve()
                add(SvMenuOption(stringResource(R.string.claim_this_is, name)) { onMatch(claim.id, participant.id) })
            }
            add(SvMenuOption(stringResource(R.string.claim_someone_new)) { onMatch(claim.id, NEW_PERSON) })
            add(SvMenuOption(stringResource(R.string.claim_take_off, claim.name), onRemove))
        }
        SvOverflowMenu(label = stringResource(R.string.claim_who_is, claim.name), options = options)
    }
}

@Composable
private fun ClaimBar(share: ShareView, working: Boolean, methodChanged: Boolean, actions: ClaimActions, onReopen: () -> Unit) {
    val morePicks = stringResource(R.string.claim_more_picks)
    val pickAgain = stringResource(R.string.claim_pick_again)
    SvBottomBar {
        when {
            // While a call runs, nothing else can start.
            working -> SvPrimaryButton(text = stringResource(R.string.common_working), onClick = {}, enabled = false)

            share.status == ShareView.OPEN -> SvPrimaryButton(
                text = stringResource(R.string.claim_stop_picking),
                onClick = actions.onLock,
                enabled = share.claims.isNotEmpty(),
            )

            share.status == ShareView.LOCKING -> SvSplitButton(
                text = stringResource(R.string.claim_send_amounts),
                onClick = actions.onSend,
                moreLabel = morePicks,
                options = listOf(
                    SvMenuOption(stringResource(R.string.claim_check_first), actions.onBack),
                    SvMenuOption(pickAgain, onReopen),
                ),
            )

            methodChanged -> SvSplitButton(
                text = stringResource(R.string.claim_send_new_details),
                onClick = actions.onSend,
                moreLabel = morePicks,
                options = listOf(SvMenuOption(stringResource(R.string.common_done), actions.onBack), SvMenuOption(pickAgain, onReopen)),
            )

            else -> SvSplitButton(
                text = stringResource(R.string.common_done),
                onClick = actions.onBack,
                moreLabel = morePicks,
                options = listOf(SvMenuOption(pickAgain, onReopen)),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(asked: Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, text, action) = when (asked) {
        Confirm.Revoke -> Triple(
            stringResource(R.string.claim_revoke_title),
            stringResource(R.string.claim_revoke_text),
            stringResource(R.string.claim_revoke_confirm),
        )

        Confirm.Reopen -> Triple(
            stringResource(R.string.claim_reopen_title),
            stringResource(R.string.claim_reopen_text),
            stringResource(R.string.claim_reopen_confirm),
        )

        is Confirm.Remove -> Triple(
            stringResource(R.string.claim_remove_title, asked.name),
            stringResource(R.string.claim_remove_text),
            stringResource(R.string.claim_remove_confirm),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Keeps an open question when the phone turns, as plain words. */
private val ConfirmSaver = Saver<Confirm?, List<String>>(
    save = { asked ->
        when (asked) {
            null -> emptyList()
            Confirm.Revoke -> listOf("revoke")
            Confirm.Reopen -> listOf("reopen")
            is Confirm.Remove -> listOf("remove", asked.claimId, asked.name)
        }
    },
    restore = { saved ->
        when (saved.firstOrNull()) {
            "revoke" -> Confirm.Revoke
            "reopen" -> Confirm.Reopen
            "remove" -> Confirm.Remove(saved[1], saved[2])
            else -> null
        }
    },
)

/** Where the link stands, in one line: open until when, or what happens now. */
private fun statusOf(share: ShareView, locale: Locale): UiText = when (share.status) {
    ShareView.OPEN -> {
        val until = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(share.expiresAt).atZone(ZoneId.systemDefault()))
        text(R.string.claim_open_until, until)
    }

    ShareView.LOCKING -> text(R.string.claim_closed)

    else -> {
        val asksToPay = share.amounts.orEmpty().values.any { it.payment != null }
        text(if (asksToPay) R.string.claim_shows_how_to_pay else R.string.claim_shows_owed)
    }
}
