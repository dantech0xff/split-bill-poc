package com.dantech.splitvibe.ui.requests

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dantech.splitvibe.R
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
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.text.text
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
                title = { Text(stringResource(R.string.requests_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (signedIn) {
                        SvOverflowMenu(
                            label = stringResource(R.string.requests_more),
                            options = listOf(SvMenuOption(stringResource(R.string.groups_reload), actions.onRefresh)),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            if (!signedIn) {
                item {
                    SvMessageCard(
                        text = stringResource(R.string.requests_need_sign_in),
                        isError = false,
                        action = stringResource(R.string.common_sign_in_in_settings),
                        onAction = actions.onOpenSettings,
                        modifier = Modifier.padding(SvSpacing.lg),
                    )
                }
            }
            status.message?.let {
                item {
                    SvMessageCard(
                        text = it.resolve(),
                        isError = true,
                        action = stringResource(R.string.common_ok),
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
                        text = stringResource(R.string.requests_empty),
                    )
                }
            }
            section(R.string.requests_to_pay, all.filter { !it.isMine }) { request, position ->
                RequestRow(request, position, creditorNames[request.id]) {
                    if (request.status == RequestStatus.WAITING) {
                        FilledTonalButton(onClick = { paying = request.id }) { Text(stringResource(R.string.requests_pay)) }
                    }
                }
            }
            section(R.string.requests_you_asked_for, all.filter { it.isMine }) { request, position ->
                RequestRow(request, position, null) {
                    if (request.status != RequestStatus.PAID) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                            if (request.status == RequestStatus.REPORTED_PAID) {
                                FilledTonalButton(onClick = { actions.onConfirmPaid(request.id) }) {
                                    Text(stringResource(R.string.requests_confirm))
                                }
                            }
                            SvOverflowMenu(
                                label = stringResource(R.string.requests_more_for, request.payerName),
                                options = buildList {
                                    if (request.status == RequestStatus.WAITING) {
                                        add(SvMenuOption(stringResource(R.string.requests_arrived)) { actions.onConfirmPaid(request.id) })
                                    }
                                    add(SvMenuOption(stringResource(R.string.requests_send_reminder)) { actions.onRemind(request) })
                                    RequestView.REMINDER_DAYS.filter { it != request.remindEveryDays }.forEach { days ->
                                        add(SvMenuOption(reminderLabel(days).resolve()) { actions.onSetReminders(request.id, days) })
                                    }
                                    add(SvMenuOption(stringResource(R.string.requests_delete)) { actions.onDelete(request.id) })
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

private fun LazyListScope.section(
    @StringRes title: Int,
    requests: List<RequestView>,
    row: @Composable (RequestView, SvRowPosition) -> Unit,
) {
    if (requests.isEmpty()) return
    item(key = "header-$title") { SvSectionHeader(stringResource(title)) }
    itemsIndexed(requests, key = { _, request -> request.id }) { index, request -> row(request, SvRowPosition.of(index, requests.size)) }
}

@Composable
private fun RequestRow(request: RequestView, position: SvRowPosition, groupName: String?, trailing: @Composable () -> Unit) {
    val who = if (request.isMine) request.payerName else groupName ?: request.creditorName ?: stringResource(R.string.common_someone)
    SvListRow(position = position) {
        Column(modifier = Modifier.weight(1f)) {
            Text(listOfNotNull(who, request.title).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            Text(request.bill.format(request.amountMinor), style = MaterialTheme.typography.amountMedium)
            Text(
                statusText(request).resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** How a request stands, from the side of whoever looks at it. */
internal fun statusText(request: RequestView): UiText = when (request.status) {
    RequestStatus.PAID -> text(R.string.requests_paid)

    RequestStatus.REPORTED_PAID -> if (request.isMine) {
        text(R.string.requests_they_say_paid, request.payerName)
    } else {
        text(R.string.requests_you_said_paid)
    }

    RequestStatus.WAITING -> when {
        !request.isMine -> text(R.string.requests_not_paid)
        !request.debtorBound -> withReminders(text(R.string.requests_not_opened), request)
        else -> withReminders(text(R.string.requests_not_paid), request)
    }
}

/** [status] with how often the payer is reminded, while they are. */
private fun withReminders(status: UiText, request: RequestView): UiText = if (request.remindEveryDays > 0) {
    text(R.string.requests_status_and_reminders, status, reminderLabel(request.remindEveryDays))
} else {
    status
}

private fun reminderLabel(days: Int): UiText = when (days) {
    0 -> text(R.string.requests_no_reminders)
    else -> plural(R.plurals.requests_remind_every, days)
}

/** How to pay a request: its code to scan, its link to open and its details to type, then a way to say it is paid. */
@Composable
private fun PaySheet(request: RequestView, onReportPaid: () -> Unit, onDismiss: () -> Unit) {
    SvBottomSheet(
        title = stringResource(
            R.string.requests_pay_title,
            request.creditorName ?: stringResource(R.string.requests_them),
            request.bill.format(request.amountMinor),
        ),
        supportingText = stringResource(R.string.requests_pay_hint),
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.requests_not_yet)) }
            Button(onClick = onReportPaid) { Text(stringResource(R.string.requests_i_have_paid)) }
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
                stringResource(R.string.requests_no_way_to_pay),
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
                        contentDescription = stringResource(R.string.requests_payment_code, method.title),
                        modifier = Modifier.size(CODE_SIZE).padding(SvSpacing.sm),
                    )
                }
            }
            Text(
                stringResource(R.string.requests_scan_it),
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
        Text(stringResource(R.string.requests_open_method, method.title))
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = {
                val host = (method as? PaymentMethod.PastedLink)?.host ?: stringResource(R.string.requests_this_link)
                Text(stringResource(R.string.requests_open_link_title, host))
            },
            text = { Text(stringResource(R.string.requests_pasted_link_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        open()
                    },
                ) { Text(stringResource(R.string.common_open)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun DetailsCard(paying: PaymentRequest) {
    val clipboard = LocalClipboardManager.current
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text(paying.details, style = MaterialTheme.typography.bodyMedium)
            if (!paying.amountFilledIn) Text(stringResource(R.string.requests_type_amount), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { clipboard.setText(AnnotatedString(paying.details)) }) {
                Text(stringResource(R.string.requests_copy_details))
            }
        }
    }
}

private const val CODE_PX = 480
private val CODE_SIZE = 220.dp
