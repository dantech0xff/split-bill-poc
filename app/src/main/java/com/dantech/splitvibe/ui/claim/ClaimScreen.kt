package com.dantech.splitvibe.ui.claim

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.dantech.splitvibe.data.bills.SavedBill
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
    val onOpenSettings: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * The bill's link: everyone at the table opens it in a browser and taps what they had, and this
 * screen shows their picks as they come. The user says who each one is on the bill, stops the
 * picking, checks the split, and sends what each person owes to the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimScreen(state: ClaimState, saved: SavedBill?, actions: ClaimActions, locale: Locale = Locale.getDefault()) {
    val share = state.share
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
                            options = listOf(SvMenuOption("Stop sharing the link", actions.onRevoke)),
                        )
                    }
                },
            )
        },
        bottomBar = { if (share != null && saved != null) ClaimBar(share, state.working, actions) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(SvSpacing.lg),
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
            }
            state.message?.let { message ->
                // Before the link exists, the message is about making it, which can be tried again.
                val retry = share == null
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
            item { LinkCard(state.url, share, saved, locale) }
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
                item { SvSectionHeader("Picked so far", hint = "Names are what people typed. Say who each one is on the bill.") }
                itemsIndexed(claims, key = { _, claim -> claim.id }) { index, claim ->
                    ClaimRow(claim, SvRowPosition.of(index, claims.size), saved, share, state.matches[claim.id], actions)
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
}

@Composable
private fun LinkCard(url: String?, share: ShareView, saved: SavedBill, locale: Locale) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text(statusOf(share, locale), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ClaimRow(
    claim: ShareClaim,
    position: SvRowPosition,
    saved: SavedBill,
    share: ShareView,
    match: String?,
    actions: ClaimActions,
) {
    val names = share.bill.items.associate { it.id to it.name }
    val items = claim.itemIds.mapNotNull { names[it] }.ifEmpty { listOf("Nothing yet") }.joinToString(", ")
    val participants = saved.plan.participants
    val onBill = when (val id = match) {
        null, ClaimState.NEW_PERSON -> "Added as someone new"

        else -> participants.firstOrNull { it.id == id }?.let { "On the bill as ${if (it.isMe) "you" else it.name}" }
            ?: "Added as someone new"
    }
    SvListRow(position = position) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            Text(claim.name, style = MaterialTheme.typography.bodyLarge)
            Text(items, style = MaterialTheme.typography.bodyMedium)
            Text(onBill, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (share.isLocked) return@SvListRow
        val options = buildList {
            if (share.isOpen) {
                participants.forEach { participant ->
                    val name = if (participant.isMe) "You" else participant.name.ifBlank { "Someone" }
                    add(SvMenuOption("This is $name") { actions.onMatch(claim.id, participant.id) })
                }
                add(SvMenuOption("Someone new") { actions.onMatch(claim.id, ClaimState.NEW_PERSON) })
            }
            add(SvMenuOption("Take ${claim.name} off the link") { actions.onRemove(claim.id) })
        }
        SvOverflowMenu(label = "Who is ${claim.name}", options = options)
    }
}

@Composable
private fun ClaimBar(share: ShareView, working: Boolean, actions: ClaimActions) {
    SvBottomBar {
        when (share.status) {
            ShareView.OPEN -> SvPrimaryButton(
                text = "Stop picking and split",
                onClick = actions.onLock,
                enabled = share.claims.isNotEmpty() && !working,
            )

            ShareView.LOCKING -> SvSplitButton(
                text = "Send what everyone owes",
                onClick = { if (!working) actions.onSend() },
                moreLabel = "More for the picks",
                options = listOf(
                    SvMenuOption("Check the split first", actions.onBack),
                    SvMenuOption("Let people pick again", actions.onReopen),
                ),
            )

            else -> SvSplitButton(
                text = "Done",
                onClick = actions.onBack,
                moreLabel = "More for the picks",
                options = listOf(SvMenuOption("Let people pick again", actions.onReopen)),
            )
        }
    }
}

/** Where the link stands, in one line: open until when, or what happens now. */
private fun statusOf(share: ShareView, locale: Locale): String = when (share.status) {
    ShareView.OPEN -> {
        val until = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(share.expiresAt).atZone(ZoneId.systemDefault()))
        "Open for picking until $until"
    }

    ShareView.LOCKING -> "Picking is closed. Check the split, then send what everyone owes."

    else -> "The page shows what everyone owes, and how to pay you."
}
