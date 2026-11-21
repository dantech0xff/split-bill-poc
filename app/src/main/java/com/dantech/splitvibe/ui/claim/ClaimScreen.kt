package com.dantech.splitvibe.ui.claim

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink.Companion.NEW_PERSON
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.data.share.ShareClaim
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroKind
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.dateOrderOf
import com.dantech.splitvibe.domain.dateWithYearText
import com.dantech.splitvibe.domain.regionLocale
import com.dantech.splitvibe.share.ShareCardRenderer
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.split.nameOf
import java.time.Instant
import java.time.ZoneId
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
 * picking, checks the split, and sends what each person owes to the page. The block at the top is
 * violet while people pick and takes the colour of the floating bar once picking is closed. The day
 * the link is open until is written in the order of [locale], the phone's region unless another is
 * given.
 */
@Composable
fun ClaimScreen(state: ClaimState, saved: SavedBill?, payment: ClaimPayment, actions: ClaimActions, locale: Locale = regionLocale()) {
    val share = state.share
    var confirming by rememberSaveable(stateSaver = ConfirmSaver) { mutableStateOf<Confirm?>(null) }
    Scaffold(
        topBar = {
            SvTopBar(
                title = stringResource(R.string.claim_title),
                backLabel = stringResource(R.string.common_back),
                onBack = actions.onBack,
                action = if (share == null) {
                    null
                } else {
                    {
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
        // The list ends above the bar at the bottom, so nothing scrolls under it.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = SvSpacing.xs, bottom = SvSpacing.lg),
        ) {
            // Each block apart from the next; the rows of a group of rows touch, so that they make one card.
            val margins = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs)
            when {
                state.needsSignIn -> item(key = "sign-in") {
                    SvMessageCard(
                        text = stringResource(R.string.claim_sign_in),
                        isError = false,
                        action = stringResource(R.string.common_sign_in_in_settings),
                        onAction = actions.onOpenSettings,
                        modifier = margins,
                    )
                }

                state.cannotShare -> item(key = "cannot-share") {
                    SvMessageCard(
                        text = stringResource(R.string.claim_cannot_share),
                        isError = false,
                        modifier = margins,
                    )
                }

                state.otherAccount -> item(key = "other-account") {
                    SvMessageCard(
                        text = stringResource(R.string.claim_other_account),
                        isError = false,
                        action = stringResource(R.string.claim_make_new_link),
                        onAction = actions.onMakeNewLink,
                        modifier = margins,
                    )
                }
            }
            state.message?.let { message ->
                // Before the link exists, the message is about making it, which can be tried again.
                val retry = share == null && !state.otherAccount
                item(key = "message") {
                    SvMessageCard(
                        text = message.resolve(),
                        isError = true,
                        action = stringResource(if (retry) R.string.common_try_again else R.string.common_dismiss),
                        onAction = if (retry) actions.onRetry else actions.onDismissMessage,
                        modifier = margins,
                    )
                }
            }
            if (share == null || saved == null) {
                if (state.working) item(key = "loading") { SvLoadingIndicator(modifier = margins) }
                return@LazyColumn
            }
            item(key = "link") { LinkHero(state, share, saved, locale, modifier = margins) }
            if (!share.isOpen) item(key = "payment") { PaymentCard(share, saved, payment, actions, modifier = margins) }
            val claims = share.claims
            if (claims.isEmpty()) {
                item(key = "nobody-yet") {
                    Text(
                        stringResource(R.string.claim_nobody_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = margins.padding(vertical = SvSpacing.sm),
                    )
                }
            } else {
                item(key = "picked") {
                    val hint = stringResource(if (share.isOpen) R.string.claim_names_typed_say_who else R.string.claim_names_typed)
                    SvSectionHeader(stringResource(R.string.claim_picked_so_far), hint = hint)
                }
                items(claims, key = { claim -> claim.id }) { claim ->
                    ClaimCard(
                        claim = claim,
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
                item(key = "unpicked") {
                    SvSectionHeader(stringResource(R.string.claim_nobody_picked), hint = stringResource(R.string.claim_nobody_picked_hint))
                }
                // One card for what nobody picked, its rows one after another.
                itemsIndexed(unpicked, key = { _, item -> "item-${item.id}" }) { index, item ->
                    SvListRow(position = SvRowPosition.of(index, unpicked.size)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMediumEmphasized, modifier = Modifier.weight(1f))
                        Text(saved.bill.format(item.amountMinor), style = MaterialTheme.typography.amountSmall)
                    }
                }
            }
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

/**
 * The block at the top: where the link stands, what to do with it, its address, and the ways to
 * share and copy it. Violet while people pick, the colour of the floating bar once picking is closed.
 */
@Composable
private fun LinkHero(state: ClaimState, share: ShareView, saved: SavedBill, locale: Locale, modifier: Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboardManager.current
    val url = state.url
    SvHero(modifier = modifier, kind = if (share.isOpen) SvHeroKind.Brand else SvHeroKind.Bar) {
        Text(statusOf(share, locale).resolve(), style = MaterialTheme.typography.titleLarge)
        if (share.isOpen && !state.connected) SvTag(stringResource(R.string.claim_reconnecting))
        Text(stringResource(R.string.claim_send_link_hint))
        if (url != null) {
            // The whole address, which goes on to a second line rather than being cut off.
            Text(url, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                SvPrimaryButton(
                    text = stringResource(R.string.claim_share_link),
                    onClick = {
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
                    },
                    icon = SvActionIcons.Share,
                    compact = true,
                )
                SvQuietButton(stringResource(R.string.claim_copy), onClick = { clipboard.setText(AnnotatedString(url)) })
            }
        }
    }
}

/** How the page asks people to pay, once picking is closed: with the user's method, or, when someone else is owed, not at all. */
@Composable
private fun PaymentCard(share: ShareView, saved: SavedBill, payment: ClaimPayment, actions: ClaimActions, modifier: Modifier) {
    SvCard(modifier = modifier.fillMaxWidth()) {
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
                    SvQuietButton(stringResource(R.string.requests_add_method), onClick = actions.onAddMethod)
                }

                else -> {
                    val method = payment.chosen.method
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                        SvAvatar(method.title, size = AVATAR_SIZE)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) {},
                        ) {
                            Text(
                                stringResource(R.string.claim_pay_you_by, method.title),
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                            )
                            Text(
                                method.summary,
                                style = MaterialTheme.typography.bodySmall,
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

/**
 * What one person picked, as a card of its own: the name they typed, what they had, and who they
 * are on the bill. While people pick, its menu says who they are or takes them off the link.
 */
@Composable
private fun ClaimCard(
    claim: ShareClaim,
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
    SvListRow(position = SvRowPosition.Single, modifier = Modifier.padding(vertical = SvSpacing.xs)) {
        SvAvatar(claim.name, size = AVATAR_SIZE)
        Spacer(Modifier.width(SvSpacing.md))
        // One element for a screen reader: who picked what, and who they are on the bill.
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(claim.name, style = MaterialTheme.typography.bodyLargeEmphasized)
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

/**
 * What to do next at the bottom of the screen. While people pick, the one thing to do is to stop
 * the picking, the main action of the screen where the floating bar puts its own. Once picking is
 * closed the floating bar holds sending what everyone owes, or being done, with a menu for the rest.
 */
@Composable
private fun ClaimBar(share: ShareView, working: Boolean, methodChanged: Boolean, actions: ClaimActions, onReopen: () -> Unit) {
    val morePicks = stringResource(R.string.claim_more_picks)
    val pickAgain = stringResource(R.string.claim_pick_again)
    if (share.status == ShareView.OPEN) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.xl),
        ) {
            // While a call runs, nothing else can start.
            if (working) {
                SvPrimaryButton(text = stringResource(R.string.common_working), onClick = {}, enabled = false)
            } else {
                SvPrimaryButton(
                    text = stringResource(R.string.claim_stop_picking),
                    onClick = actions.onLock,
                    enabled = share.claims.isNotEmpty(),
                )
            }
        }
        return
    }
    SvBottomBar {
        when {
            // While a call runs, nothing else can start.
            working -> SvPrimaryButton(text = stringResource(R.string.common_working), onClick = {}, enabled = false)

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
    SvDialog(
        title = title,
        text = text,
        confirmLabel = action,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.common_cancel),
        // Ending the link and taking someone off it delete what was picked; picking again does not.
        irreversible = asked != Confirm.Reopen,
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

private val AVATAR_SIZE = 44.dp

/** Where the link stands, in one line: open until when, or what happens now. */
private fun statusOf(share: ShareView, locale: Locale): UiText = when (share.status) {
    ShareView.OPEN -> {
        val until = dateWithYearText(Instant.ofEpochMilli(share.expiresAt).atZone(ZoneId.systemDefault()), dateOrderOf(locale))
        text(R.string.claim_open_until, until)
    }

    ShareView.LOCKING -> text(R.string.claim_closed)

    else -> {
        val asksToPay = share.amounts.orEmpty().values.any { it.payment != null }
        text(if (asksToPay) R.string.claim_shows_how_to_pay else R.string.claim_shows_owed)
    }
}
