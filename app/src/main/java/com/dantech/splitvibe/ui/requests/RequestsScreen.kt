package com.dantech.splitvibe.ui.requests

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBesideOrBelow
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSmallAction
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTile
import com.dantech.splitvibe.designsystem.components.SvTileKind
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.domain.BillCurrency
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
)

/**
 * The requests sent to the user, to pay, and those the user sent, to confirm, each as a card with
 * what can be done about it. Above them two tiles say what the user owes and what friends owe the
 * user, as [totals] adds it up: the totals of the one holder that the home and the navigation bar
 * read too, so that the screens never disagree. The tiles add nothing up themselves, and show only
 * once the totals are known and there are requests. [localCurrency], a currency code, is the one
 * whose total leads, as on the home.
 *
 * It is one of the main screens, with its large title, the navigation bar the app passes as
 * [bottomBar] and no back button.
 */
@Composable
fun RequestsScreen(
    requests: List<RequestView>?,
    signedIn: Boolean,
    status: ProxyWork,
    actions: RequestActions,
    totals: RequestTotals,
    localCurrency: String,
    /** Who asked, by request id, as their group knows them; see [RequestsViewModel.creditorNames]. */
    creditorNames: Map<String, String> = emptyMap(),
    bottomBar: @Composable () -> Unit = {},
) {
    var paying by rememberSaveable { mutableStateOf<String?>(null) }
    val tiles = RequestTiles.of(totals, requests, localCurrency)
    Scaffold(
        topBar = {
            SvLargeTopBar(
                title = stringResource(R.string.requests_title),
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
        bottomBar = bottomBar,
    ) { padding ->
        // The list ends above the navigation bar, so no request, nor its buttons, ever passes under it half hidden.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = SvSpacing.sm, bottom = SvSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            if (!signedIn) {
                item(key = "sign-in") {
                    SvMessageCard(
                        text = stringResource(R.string.requests_need_sign_in),
                        isError = false,
                        action = stringResource(R.string.common_sign_in_in_settings),
                        onAction = actions.onOpenSettings,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    )
                }
            }
            status.message?.let {
                item(key = "message") {
                    SvMessageCard(
                        text = it.resolve(),
                        isError = true,
                        action = stringResource(R.string.common_ok),
                        onAction = actions.onDismissMessage,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    )
                }
            }
            tiles?.let { item(key = "tiles") { Tiles(it) } }
            if (status.working && requests == null) item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }
            val all = requests.orEmpty()
            if (signedIn && requests != null && all.isEmpty()) {
                item(key = "empty") {
                    SvEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        text = stringResource(R.string.requests_empty),
                    )
                }
            }
            section(R.string.requests_to_pay, all.filter { !it.isMine }) { request ->
                val who = creditorNames[request.id] ?: request.creditorName ?: stringResource(R.string.common_someone)
                RequestCard(request, who) {
                    if (request.status == RequestStatus.WAITING) {
                        SvPrimaryButton(stringResource(R.string.requests_pay), onClick = { paying = request.id }, compact = true)
                    }
                }
            }
            section(R.string.requests_you_asked_for, all.filter { it.isMine }) { request ->
                RequestCard(request, request.payerName) {
                    if (request.status == RequestStatus.REPORTED_PAID) {
                        SvSmallAction(stringResource(R.string.requests_confirm), onClick = { actions.onConfirmPaid(request.id) })
                    }
                    if (request.status != RequestStatus.PAID) {
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
    }

    paying?.let { id ->
        val request = requests?.firstOrNull { it.id == id }
        if (request == null) {
            paying = null
        } else {
            PaySheet(
                request = request,
                // As the card it was opened from names them.
                creditor = creditorNames[request.id] ?: request.creditorName,
                onReportPaid = {
                    paying = null
                    actions.onReportPaid(id)
                },
                onDismiss = { paying = null },
            )
        }
    }
}

/**
 * What the two tiles of the requests screen show: [youOwe], what the user owes, and [owedToYou],
 * what friends owe the user, each as [RequestTotals] adds it up.
 */
internal data class RequestTiles(val youOwe: Side, val owedToYou: Side) {

    /**
     * One side: its [total] in the user's own currency, or in the currency of the newest request when
     * none is in it, and its totals in [others], the other currencies, which nothing converts.
     */
    data class Side(val total: Money, val others: List<Money>)

    companion object {
        /**
         * The tiles for [totals], beside the [requests] the screen lists, for a user who counts in
         * [localCurrency]. None while nothing is known of the totals, as nothing known is not nothing
         * owed, nor while there is no request to list. A side without an open request comes to
         * nothing in the user's own currency, which is then known.
         */
        fun of(totals: RequestTotals, requests: List<RequestView>?, localCurrency: String): RequestTiles? {
            val loaded = totals as? RequestTotals.Loaded ?: return null
            if (requests.isNullOrEmpty()) return null
            val nothing = Side(Money(BillCurrency.resolve(localCurrency, printedDecimals = null, amounts = emptyList()), 0), emptyList())
            fun side(open: OpenRequests?) = open?.let { Side(it.main, it.others) } ?: nothing
            return RequestTiles(
                youOwe = side(OpenRequests.of(loaded.youOwe, loaded.youOweCount, loaded.youOweTo, localCurrency)),
                owedToYou = side(OpenRequests.of(loaded.owedToYou, loaded.owedToYouCount, loaded.owedBy, localCurrency)),
            )
        }
    }
}

/** What the user owes and what friends owe the user, side by side, as the home writes them. */
@Composable
private fun Tiles(tiles: RequestTiles) {
    // Not stretched to one height by intrinsic measurement, which measures the values unshrunk.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
    ) {
        Tile(stringResource(R.string.home_you_owe), tiles.youOwe, SvTileKind.Ink, Modifier.weight(1f))
        Tile(stringResource(R.string.home_friends_owe_you), tiles.owedToYou, SvTileKind.Brand, Modifier.weight(1f))
    }
}

/** One side as a tile, which a screen reader reads as one element: its label, its total, and the other currencies below. */
@Composable
private fun Tile(label: String, side: RequestTiles.Side, kind: SvTileKind, modifier: Modifier) {
    SvTile(
        label = label,
        value = side.total.text,
        kind = kind,
        modifier = modifier.semantics(mergeDescendants = true) {},
        caption = side.others.takeIf { it.isNotEmpty() }?.joinToString(OTHERS_SEPARATOR) { it.text },
    )
}

private fun LazyListScope.section(@StringRes title: Int, requests: List<RequestView>, card: @Composable (RequestView) -> Unit) {
    if (requests.isEmpty()) return
    item(key = "header-$title") { SvSectionHeader(stringResource(title)) }
    items(requests, key = { request -> request.id }) { request -> card(request) }
}

/**
 * One request as a card: who it is with, by their letters and name, what it is for, its amount, and
 * how it stands, a request paid and confirmed with a tag that says so. [actions] stand at the end
 * of its first line, or below it with larger text. A screen reader reads the card as one element,
 * with its buttons apart.
 */
@Composable
private fun RequestCard(request: RequestView, who: String, actions: @Composable RowScope.() -> Unit) {
    SvCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.screen)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                SvAvatar(who, size = AVATAR_SIZE)
                // The actions keep their width; the texts wrap between words beside them, or take the
                // whole width above them, so that a name or an amount is never broken in the middle.
                SvBesideOrBelow(
                    first = {
                        Column {
                            Text(
                                listOfNotNull(who, request.title).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                            )
                            Text(request.bill.format(request.amountMinor), style = MaterialTheme.typography.amountMedium)
                        }
                    },
                    second = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SvSpacing.xs),
                            content = actions,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (request.status == RequestStatus.PAID) {
                SvTag(statusText(request).resolve(), kind = SvTagKind.Accent, icon = Icons.Default.Check)
            } else {
                Text(
                    statusText(request).resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
private fun PaySheet(request: RequestView, creditor: String?, onReportPaid: () -> Unit, onDismiss: () -> Unit) {
    SvBottomSheet(
        title = stringResource(
            R.string.requests_pay_title,
            creditor ?: stringResource(R.string.requests_them),
            request.bill.format(request.amountMinor),
        ),
        supportingText = stringResource(R.string.requests_pay_hint),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.requests_not_yet), onClick = onDismiss, modifier = Modifier.weight(1f))
            SvPrimaryButton(stringResource(R.string.requests_i_have_paid), onClick = onReportPaid, modifier = Modifier.weight(1f))
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SvSpacing.screen),
        ) {
            PayPanel(request)
        }
    }
}

/**
 * The code, link and details of [request], built on this phone from the one payment method the
 * request carries: the code to scan on a card of its own, the way to open the link, and the details
 * to type, line by line, with a button that copies them.
 */
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
        paying.qrPayload?.let { payload -> CodeCard(payload, method) }
        paying.link?.takeIf { it != paying.qrPayload || method is PaymentMethod.Upi }?.let { link -> OpenLinkButton(link, method) }
        DetailsCard(paying)
    }
}

/**
 * The payment code to scan, at the size it always had and with the white quiet zone around it that
 * scanners need, which the image of the code carries itself; nothing rounds or cuts its edges.
 */
@Composable
private fun CodeCard(payload: String, method: PaymentMethod) {
    // Drawn away from the main thread, then shown.
    val code by produceState<ImageBitmap?>(null, payload) {
        value = withContext(Dispatchers.Default) { runCatching { QrCodes.bitmap(payload, CODE_PX).asImageBitmap() }.getOrNull() }
    }
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SvSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            val drawn = code
            if (drawn == null) {
                Spacer(Modifier.size(CODE_SIZE))
            } else {
                Image(
                    drawn,
                    contentDescription = stringResource(R.string.requests_payment_code, method.title),
                    modifier = Modifier
                        .size(CODE_SIZE)
                        .padding(SvSpacing.sm),
                )
            }
            Text(
                stringResource(R.string.requests_scan_it),
                style = MaterialTheme.typography.bodyMediumEmphasized,
                textAlign = TextAlign.Center,
            )
        }
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
    SvQuietButton(
        text = stringResource(R.string.requests_open_method, method.title),
        onClick = { if (method is PaymentMethod.PastedLink) confirming = true else open() },
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        val host = (method as? PaymentMethod.PastedLink)?.host ?: stringResource(R.string.requests_this_link)
        SvDialog(
            title = stringResource(R.string.requests_open_link_title, host),
            text = stringResource(R.string.requests_pasted_link_warning),
            confirmLabel = stringResource(R.string.common_open),
            onConfirm = {
                confirming = false
                open()
            },
            onDismiss = { confirming = false },
            dismissLabel = stringResource(R.string.common_cancel),
        )
    }
}

/**
 * The details to pay by hand, one line each, with what each line is at its start, such as "Bank",
 * and a button that copies them all as they are written.
 */
@Composable
private fun DetailsCard(paying: PaymentRequest) {
    val clipboard = LocalClipboardManager.current
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = SvSpacing.lg, end = SvSpacing.lg, top = SvSpacing.sm, bottom = SvSpacing.lg)) {
            detailsOf(paying.details).forEach { detail -> DetailRow(detail) }
            if (!paying.amountFilledIn) {
                Text(
                    stringResource(R.string.requests_type_amount),
                    modifier = Modifier.padding(vertical = SvSpacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SvQuietButton(
                text = stringResource(R.string.requests_copy_details),
                onClick = { clipboard.setText(AnnotatedString(paying.details)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SvSpacing.sm),
            )
        }
    }
}

/**
 * One line of the details: what it is in quiet type at its start, and what to type at its end. The
 * value is never hidden by its label: when the two do not fit side by side, as with a long label or
 * text at twice its size, the value stands below its label, at the start. A screen reader reads the
 * line as one element.
 */
@Composable
private fun DetailRow(detail: PaymentDetail) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = DETAIL_LINE_HEIGHT)
        .padding(vertical = SvSpacing.xs)
        .semantics(mergeDescendants = true) {}
    val label = detail.label
    if (label == null) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
            Text(detail.value, style = MaterialTheme.typography.bodyMediumEmphasized)
        }
        return
    }
    SvBesideOrBelow(
        first = { Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        // A value too long for the room beside its label wraps between words, at the end like the others.
        second = { Text(detail.value, style = MaterialTheme.typography.bodyMediumEmphasized, textAlign = TextAlign.End) },
        modifier = modifier,
        firstKeepsItsWidth = true,
        secondBelowAt = Alignment.Start,
        stackedGap = SvSpacing.xs,
    )
}

/** A line of the details of a payment, such as "Account: 000000000": what it is, when it says, and its value. */
internal data class PaymentDetail(val label: String?, val value: String)

/**
 * The lines of [details] as the builder of a payment writes them, one thing a line, each named
 * before a colon, such as "Bank: ACB (970416)". A line that names nothing, such as one more line of
 * details a person typed, is all value.
 */
internal fun detailsOf(details: String): List<PaymentDetail> = details.lines().filter { it.isNotBlank() }.map { line ->
    val colon = line.indexOf(DETAIL_SEPARATOR)
    if (colon > 0) {
        PaymentDetail(line.substring(0, colon).trim(), line.substring(colon + DETAIL_SEPARATOR.length).trim())
    } else {
        PaymentDetail(null, line.trim())
    }
}

private const val CODE_PX = 480
private val CODE_SIZE = 220.dp
private val AVATAR_SIZE = 44.dp
private val DETAIL_LINE_HEIGHT = 44.dp
private const val DETAIL_SEPARATOR = ": "

/** Between the totals in other currencies below a tile's own. */
private const val OTHERS_SEPARATOR = " · "
