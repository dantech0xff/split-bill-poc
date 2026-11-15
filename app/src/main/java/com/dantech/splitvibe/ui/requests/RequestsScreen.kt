package com.dantech.splitvibe.ui.requests

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.domain.payments.PaymentRequest
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.share.QrCodes
import com.dantech.splitvibe.ui.common.ProxyWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the requests screen can ask for. */
class RequestActions(
    val onReportPaid: (String) -> Unit,
    val onConfirmPaid: (String) -> Unit,
    val onRemind: (RequestView) -> Unit,
    val onSetReminders: (String, Int) -> Unit,
    val onDelete: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onBack: () -> Unit,
)

/** The requests sent to the user, to pay, and those the user sent, to confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    requests: List<RequestView>?,
    signedIn: Boolean,
    status: ProxyWork,
    actions: RequestActions,
    /** Who asked, by request id, as their group knows them; see [RequestsViewModel.creditorNames]. */
    creditorNames: Map<String, String> = emptyMap(),
) {
    var paying by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Requests") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { if (signedIn) SvOverflowMenu(label = "More", options = listOf(SvMenuOption("Reload", actions.onRefresh))) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            if (!signedIn) {
                item {
                    SvMessageCard(
                        text = "Sign in with Google to see who has paid you back. Without it, requests are only messages in your chats.",
                        isError = false,
                        action = "Sign in in Settings",
                        onAction = actions.onOpenSettings,
                        modifier = Modifier.padding(SvSpacing.lg),
                    )
                }
            }
            status.message?.let {
                item {
                    SvMessageCard(
                        text = it,
                        isError = true,
                        action = "OK",
                        onAction = actions.onDismissMessage,
                        modifier = Modifier.padding(SvSpacing.lg),
                    )
                }
            }
            if (status.working && requests == null) item { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }
            val all = requests.orEmpty()
            if (signedIn && requests != null && all.isEmpty()) {
                item {
                    SvEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        text = "Requests you send after a split, and those sent to you, show here.",
                    )
                }
            }
            section("To pay", all.filter { !it.isMine }) { request, position ->
                RequestRow(request, position, creditorNames[request.id]) {
                    if (request.status == RequestStatus.WAITING) {
                        FilledTonalButton(onClick = { paying = request.id }) { Text("Pay") }
                    }
                }
            }
            section("You asked for", all.filter { it.isMine }) { request, position ->
                RequestRow(request, position, null) {
                    if (request.status != RequestStatus.PAID) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                            if (request.status == RequestStatus.REPORTED_PAID) {
                                FilledTonalButton(onClick = { actions.onConfirmPaid(request.id) }) { Text("Confirm") }
                            }
                            SvOverflowMenu(
                                label = "More for the request to ${request.payerName}",
                                options = buildList {
                                    if (request.status == RequestStatus.WAITING) {
                                        add(SvMenuOption("It arrived, confirm it") { actions.onConfirmPaid(request.id) })
                                    }
                                    add(SvMenuOption("Send a reminder") { actions.onRemind(request) })
                                    RequestView.REMINDER_DAYS.filter { it != request.remindEveryDays }.forEach { days ->
                                        add(SvMenuOption(reminderLabel(days)) { actions.onSetReminders(request.id, days) })
                                    }
                                    add(SvMenuOption("Delete the request") { actions.onDelete(request.id) })
                                },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(SvSpacing.xxl)) }
        }
    }

    paying?.let { id ->
        val request = requests?.firstOrNull { it.id == id }
        if (request == null) {
            paying = null
        } else {
            PaySheet(
                request = request,
                onReportPaid = {
                    paying = null
                    actions.onReportPaid(id)
                },
                onDismiss = { paying = null },
            )
        }
    }
}

private fun LazyListScope.section(title: String, requests: List<RequestView>, row: @Composable (RequestView, SvRowPosition) -> Unit) {
    if (requests.isEmpty()) return
    item(key = "header-$title") { SvSectionHeader(title) }
    itemsIndexed(requests, key = { _, request -> request.id }) { index, request -> row(request, SvRowPosition.of(index, requests.size)) }
}

@Composable
private fun RequestRow(request: RequestView, position: SvRowPosition, groupName: String?, trailing: @Composable () -> Unit) {
    val who = if (request.isMine) request.payerName else groupName ?: request.creditorName ?: "Someone"
    SvListRow(position = position) {
        Column(modifier = Modifier.weight(1f)) {
            Text(listOfNotNull(who, request.title).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            Text(request.bill.format(request.amountMinor), style = MaterialTheme.typography.amountMedium)
            Text(
                statusText(request),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** How a request stands, from the side of whoever looks at it. */
internal fun statusText(request: RequestView): String = when (request.status) {
    RequestStatus.PAID -> "Paid and confirmed"

    RequestStatus.REPORTED_PAID -> if (request.isMine) {
        "${request.payerName} says it is paid. Confirm once it arrived."
    } else {
        "You said you paid. Waiting for them to confirm."
    }

    RequestStatus.WAITING -> when {
        !request.isMine -> "Not paid yet"
        !request.debtorBound -> "Not opened in the app yet" + reminderNote(request)
        else -> "Not paid yet" + reminderNote(request)
    }
}

private fun reminderNote(request: RequestView): String = if (request.remindEveryDays >
    0
) {
    ". ${reminderLabel(request.remindEveryDays)}"
} else {
    ""
}

private fun reminderLabel(days: Int): String = when (days) {
    0 -> "No reminders"
    1 -> "Remind every day"
    else -> "Remind every $days days"
}

/** How to pay a request: its code to scan, its link to open and its details to type, then a way to say it is paid. */
@Composable
private fun PaySheet(request: RequestView, onReportPaid: () -> Unit, onDismiss: () -> Unit) {
    SvBottomSheet(
        title = "Pay ${request.creditorName ?: "them"} ${request.bill.format(request.amountMinor)}",
        supportingText = "Pay in your own banking or payment app, then come back to say you have paid.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Not yet") }
            Button(onClick = onReportPaid) { Text("I have paid") }
        },
    ) {
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = SvSpacing.xl)) {
            PayPanel(request)
        }
    }
}

/** The code, link and details of [request], built on this phone from the one payment method the request carries. */
@Composable
fun PayPanel(request: RequestView, modifier: Modifier = Modifier) {
    val method = request.paymentMethod
    val paying =
        remember(request.id, method) { method?.let { PaymentRequestBuilder.build(it, request.amountMinor, request.bill, request.title) } }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        if (method == null || paying == null) {
            Text(
                "This request carries no way to pay. Ask them how they would like to be paid.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        paying.qrPayload?.let { payload ->
            // Drawn away from the main thread, then shown.
            val code by produceState<ImageBitmap?>(null, payload) {
                value = withContext(Dispatchers.Default) { runCatching { QrCodes.bitmap(payload, CODE_PX).asImageBitmap() }.getOrNull() }
            }
            SvCard(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                val drawn = code
                if (drawn == null) {
                    Spacer(Modifier.size(CODE_SIZE))
                } else {
                    Image(
                        drawn,
                        contentDescription = "Payment code for ${method.title}",
                        modifier = Modifier.size(CODE_SIZE).padding(SvSpacing.sm),
                    )
                }
            }
            Text(
                "Scan it in your banking app.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        paying.link?.takeIf { it != paying.qrPayload || method is PaymentMethod.Upi }?.let { link -> OpenLinkButton(link, method) }
        DetailsCard(paying)
    }
}

/** Opens a payment link; a pasted one only after the user saw where it goes. */
@Composable
private fun OpenLinkButton(link: String, method: PaymentMethod) {
    val context = LocalContext.current
    var confirming by rememberSaveable { mutableStateOf(false) }
    val open = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
        } catch (e: ActivityNotFoundException) {
            // No app takes the link; the details below still work.
        }
    }
    OutlinedButton(onClick = {
        if (method is PaymentMethod.PastedLink) confirming = true else open()
    }, modifier = Modifier.fillMaxWidth()) {
        Text("Open ${method.title}")
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Open ${(method as? PaymentMethod.PastedLink)?.host ?: "this link"}?") },
            text = { Text("This link was pasted by the person who asked you to pay. Check the address before you pay there.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        open()
                    },
                ) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailsCard(paying: PaymentRequest) {
    val clipboard = LocalClipboardManager.current
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text(paying.details, style = MaterialTheme.typography.bodyMedium)
            if (!paying.amountFilledIn) Text("Type in the amount yourself.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { clipboard.setText(AnnotatedString(paying.details)) }) { Text("Copy the details") }
        }
    }
}

private const val CODE_PX = 480
private val CODE_SIZE = 220.dp
