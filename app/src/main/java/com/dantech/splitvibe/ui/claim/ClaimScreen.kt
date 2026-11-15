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
import androidx.compose.ui.text.AnnotatedString
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
                title = { Text("Everyone picks their items") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (share != null) {
                        SvOverflowMenu(
                            label = "More for the link",
                            options = listOf(
                                SvMenuOption("Stop sharing the link") { confirming = Confirm.Revoke },
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
                        text = "Sign in with Google to make a link. Only you can then close it or take people off it.",
                        isError = false,
                        action = "Sign in in Settings",
                        onAction = actions.onOpenSettings,
                    )
                }

                state.cannotShare -> item {
                    SvMessageCard(
                        text = "This bill cannot go on a link. A link shows from 1 to 200 lines and up to 30 people.",
                        isError = false,
                    )
                }

                state.otherAccount -> item {
                    SvMessageCard(
                        text = "This bill's link was made while another account was signed in, or no longer exists, so this account " +
                            "cannot change it. A new link replaces it here; the old one works until it ends.",
                        isError = false,
                        action = "Make a new link",
                        onAction = actions.onMakeNewLink,
                    )
                }
            }
            state.message?.let { message ->
                // Before the link exists, the message is about making it, which can be tried again.
                val retry = share == null && !state.otherAccount
                item {
                    SvMessageCard(
                        text = message,
                        isError = true,
                        action = if (retry) "Try again" else "Dismiss",
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
                        "Nobody has picked anything yet. Picks show here as people make them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = SvSpacing.md),
                    )
                }
            } else {
                val hint = "Names are what people typed." + if (share.isOpen) " Say who each one is on the bill." else ""
                item { SvSectionHeader("Picked so far", hint = hint) }
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
                item { SvSectionHeader("Nobody picked", hint = "Everyone shares these, unless you change it in the split.") }
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
    val clipboard = LocalClipboardManager.current
    val url = state.url
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text(statusOf(share, locale), style = MaterialTheme.typography.titleMedium)
            if (share.isOpen && !state.connected) {
                Text(
                    "Reconnecting… new picks show once it is back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Send this link to everyone at the table. They open it in a browser, type their name and tap what they had, " +
                    "without the app or an account.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (url != null) {
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    FilledTonalButton(onClick = {
                        val title = saved.bill.merchant?.takeIf { it.isNotBlank() } ?: "the bill"
                        try {
                            context.startActivity(
                                ShareCardRenderer.shareIntent("Pick what you had at $title: $url", title = "Share the link"),
                            )
                        } catch (e: ActivityNotFoundException) {
                            // No app on this phone takes text; the link can still be copied.
                        }
                    }) { Text("Share the link") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text("Copy") }
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
                    "This bill is in ${group.groupName}, where people settle up, so the page shows each person's share " +
                        "and asks nobody to pay.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                !ClaimViewModel.asksForMoney(saved) -> Text(
                    if (payer != null) {
                        "${payer.name.ifBlank { "Someone else" }} paid this bill, so the page shows each person's share and asks them " +
                            "to settle up with ${payer.name.ifBlank { "whoever paid" }}."
                    } else {
                        "More than one person paid this bill, so the page shows each person's share and asks nobody to pay you."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                payment.chosen == null -> {
                    Text(
                        "Add how you get paid, and the page shows each person how to pay you, with the amount filled in where it can be.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = actions.onAddMethod) { Text("Add a way to get paid") }
                }

                else -> {
                    val method = payment.chosen.method
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("The page asks people to pay you by ${method.title}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                method.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val others = payment.methods.orEmpty().filter { it.id != payment.chosen.id }
                        if (others.isNotEmpty() && !share.isLocked) {
                            SvOverflowMenu(
                                label = "Ask for another way to pay",
                                options = others.map { other ->
                                    SvMenuOption("${other.method.title}: ${other.method.summary}") { actions.onChooseMethod(other.id) }
                                },
                            )
                        }
                    }
                    if (payment.changed) {
                        Text(
                            "Your payment details changed since you sent the amounts. Send them again so the page shows the new ones.",
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
    val items = claim.itemIds.mapNotNull { names[it] }.ifEmpty { listOf("Nothing yet") }.joinToString(", ")
    val participants = saved.plan.participants
    val onBill = when (match) {
        null, NEW_PERSON -> if (claim.itemIds.isEmpty()) "Picked nothing, so not added to the bill" else "Added as someone new"

        else -> participants.firstOrNull { it.id == match }?.let { "On the bill as ${if (it.isMe) "you" else it.name}" }
            ?: "Added as someone new"
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
                val name = if (participant.isMe) "You" else participant.name.ifBlank { "Someone" }
                add(SvMenuOption("This is $name") { onMatch(claim.id, participant.id) })
            }
            add(SvMenuOption("Someone new") { onMatch(claim.id, NEW_PERSON) })
            add(SvMenuOption("Take ${claim.name} off the link", onRemove))
        }
        SvOverflowMenu(label = "Who is ${claim.name}", options = options)
    }
}

@Composable
private fun ClaimBar(share: ShareView, working: Boolean, methodChanged: Boolean, actions: ClaimActions, onReopen: () -> Unit) {
    SvBottomBar {
        when {
            // While a call runs, nothing else can start.
            working -> SvPrimaryButton(text = "Working…", onClick = {}, enabled = false)

            share.status == ShareView.OPEN -> SvPrimaryButton(
                text = "Stop picking and split",
                onClick = actions.onLock,
                enabled = share.claims.isNotEmpty(),
            )

            share.status == ShareView.LOCKING -> SvSplitButton(
                text = "Send what everyone owes",
                onClick = actions.onSend,
                moreLabel = "More for the picks",
                options = listOf(SvMenuOption("Check the split first", actions.onBack), SvMenuOption("Let people pick again", onReopen)),
            )

            methodChanged -> SvSplitButton(
                text = "Send the new payment details",
                onClick = actions.onSend,
                moreLabel = "More for the picks",
                options = listOf(SvMenuOption("Done", actions.onBack), SvMenuOption("Let people pick again", onReopen)),
            )

            else -> SvSplitButton(
                text = "Done",
                onClick = actions.onBack,
                moreLabel = "More for the picks",
                options = listOf(SvMenuOption("Let people pick again", onReopen)),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(asked: Confirm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, text, action) = when (asked) {
        Confirm.Revoke -> Triple(
            "Stop sharing the link?",
            "Its page says it no longer works, and everything people picked on it is deleted. This cannot be undone.",
            "Stop sharing",
        )

        Confirm.Reopen -> Triple(
            "Let people pick again?",
            "The page stops showing what everyone owes. When you stop the picking again, the split goes back to what people picked.",
            "Let them pick",
        )

        is Confirm.Remove -> Triple(
            "Take ${asked.name} off the link?",
            "What they picked is deleted. If they open the link again, they start over.",
            "Take off",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
private fun statusOf(share: ShareView, locale: Locale): String = when (share.status) {
    ShareView.OPEN -> {
        val until = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(share.expiresAt).atZone(ZoneId.systemDefault()))
        "Open for picking until $until"
    }

    ShareView.LOCKING -> "Picking is closed. Check the split, then send what everyone owes."

    else -> if (share.amounts.orEmpty().values.any { it.payment != null }) {
        "The page shows what everyone owes, and how to pay you."
    } else {
        "The page shows what everyone owes."
    }
}
