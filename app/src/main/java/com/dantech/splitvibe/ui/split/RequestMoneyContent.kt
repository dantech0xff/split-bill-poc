package com.dantech.splitvibe.ui.split

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSuccessMark
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.share.QrCodes
import com.dantech.splitvibe.share.ShareCardRenderer
import com.dantech.splitvibe.share.ShareCardState
import com.dantech.splitvibe.share.rememberShareCardState
import com.dantech.splitvibe.share.shareCard
import com.dantech.splitvibe.ui.requests.RequestMoneyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Last step of a split: each person who owes something gets a request of their own through the
 * share sheet, with the amount, what it is for and how to pay it, as a link, a QR code or details.
 * A card with the whole split can go to the group chat as an image.
 */
@Composable
fun RequestMoneyContent(
    result: SplitResultState,
    requests: List<PersonRequest>,
    viewModel: RequestMoneyViewModel,
    onOpenPaymentMethods: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val methods by viewModel.methods.collectAsStateWithLifecycle()
    val method by viewModel.method.collectAsStateWithLifecycle()
    val sharedWith by viewModel.sharedWith.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(viewModel.myName) }
    var choosingMethod by rememberSaveable { mutableStateOf(false) }
    val card = rememberShareCardState()

    fun share(person: PersonRequest) = scope.launch {
        val prepared = viewModel.prepare(result.saved, person, name)
        val image = prepared.paying?.qrPayload?.let { payload ->
            withContext(Dispatchers.Default) { QrCodes.bitmap(payload) }.let { bitmap ->
                withContext(Dispatchers.IO) { ShareCardRenderer.save(context, bitmap, "pay-${person.participant.id}") }
            }
        }
        if (context.open(ShareCardRenderer.shareIntent(prepared.text, image, title = "Payment request"))) {
            viewModel.markShared(person.participant.id)
        }
    }

    Scaffold(bottomBar = { SvBottomBar { SvPrimaryButton("Done", onClick = onDone) } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.xl, vertical = SvSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The request button of the step before turns into this mark.
                    SvSuccessMark(Icons.Default.Check, modifier = Modifier.sharedSurface(SEND_SURFACE, resize = true))
                    Spacer(Modifier.height(SvSpacing.lg))
                    Text("Ask for the money", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Spacer(Modifier.height(SvSpacing.sm))
                    Text(
                        "Each person gets their own message to send in any chat app. Split Vibe never holds or moves money.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                SummaryCard(result = result, state = card, modifier = Modifier.padding(horizontal = SvSpacing.lg))
                OutlinedButton(
                    onClick = { scope.launch { shareSummary(context, card, result) } },
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(SvSpacing.sm))
                    Text("Share the split as a picture")
                }
            }
            item {
                MethodPart(
                    method = method,
                    hasChoice = (methods?.size ?: 0) > 1,
                    onChoose = { choosingMethod = true },
                    onOpenPaymentMethods = onOpenPaymentMethods,
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.md),
                )
            }
            if (viewModel.signedIn) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(NAME_LENGTH) },
                        label = { Text("Your name on the requests") },
                        supportingText = { Text("People who open a request in Split Vibe see it, and can say there when they have paid.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                    )
                }
            }
            itemsIndexed(requests, key = { _, request -> request.participant.id }) { index, request ->
                PersonRow(
                    result = result,
                    request = request,
                    position = SvRowPosition.of(index, requests.size),
                    shared = request.participant.id in sharedWith,
                    onShare = { share(request) },
                )
            }
        }
    }

    if (choosingMethod) {
        MethodSheet(
            methods = methods.orEmpty(),
            chosen = method?.id,
            onChoose = {
                viewModel.choose(it)
                choosingMethod = false
            },
            onDismiss = { choosingMethod = false },
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
            text = { Text(it) },
        )
    }
}

/** The whole split on one card, drawn by the app's own components, which is also the picture that is shared. */
@Composable
private fun SummaryCard(result: SplitResultState, state: ShareCardState, modifier: Modifier = Modifier) {
    val bill = result.bill
    SvCard(modifier = modifier.fillMaxWidth().shareCard(state), elevation = SvElevation.Level2) {
        Column(modifier = Modifier.padding(SvSpacing.xl), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text(
                listOfNotNull(bill.merchant?.takeIf { it.isNotBlank() } ?: "The bill", bill.date).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(bill.format(bill.totalMinor), style = MaterialTheme.typography.amountLarge)
            result.split.shares.forEach { share ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SvAvatar(result.nameOf(share.personId))
                    Spacer(Modifier.width(SvSpacing.md))
                    Text(result.nameOf(share.personId), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(bill.format(share.totalMinor), style = MaterialTheme.typography.amountMedium)
                }
            }
            Text("Split with Split Vibe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MethodPart(
    method: SavedPaymentMethod?,
    hasChoice: Boolean,
    onChoose: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (method == null) {
        SvMessageCard(
            text = "Add how you get paid, such as your bank account or PayPal, and each request carries it with the amount.",
            isError = false,
            modifier = modifier,
            action = "Add a way to get paid",
            onAction = onOpenPaymentMethods,
        )
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Paid back by ${method.method.title}", style = MaterialTheme.typography.titleSmall)
            Text(method.method.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = if (hasChoice) onChoose else onOpenPaymentMethods) { Text(if (hasChoice) "Change" else "Add another") }
    }
}

@Composable
private fun PersonRow(result: SplitResultState, request: PersonRequest, position: SvRowPosition, shared: Boolean, onShare: () -> Unit) {
    val name = result.nameOf(request.participant.id)
    SvListRow(position = position) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(result.bill.format(request.amountMinor), style = MaterialTheme.typography.amountMedium)
            result.converted(request.amountMinor)?.let {
                Text("About $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(SvSpacing.md))
        FilledTonalButton(onClick = onShare) {
            Icon(if (shared) Icons.Default.Check else Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(SvSpacing.xs))
            Text(if (shared) "Again" else "Send")
        }
    }
}

@Composable
private fun MethodSheet(methods: List<SavedPaymentMethod>, chosen: String?, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    SvBottomSheet(title = "Get paid by", onDismiss = onDismiss, actions = { TextButton(onClick = onDismiss) { Text("Cancel") } }) {
        Column {
            methods.forEach { saved ->
                SvPersonChoiceRow(
                    name = saved.method.title,
                    selected = saved.id == chosen,
                    onSelect = { onChoose(saved.id) },
                    detail = saved.method.summary,
                )
            }
        }
    }
}

private suspend fun shareSummary(context: Context, card: ShareCardState, result: SplitResultState) {
    val bill = result.bill
    val bitmap = card.toBitmap()
    val image = withContext(Dispatchers.IO) { ShareCardRenderer.save(context, bitmap, "split-${result.saved.id}") }
    val text = buildList {
        add("${bill.merchant?.takeIf { it.isNotBlank() } ?: "The bill"}: ${bill.format(bill.totalMinor)}")
        result.split.shares.forEach { add("${result.nameOf(it.personId)}: ${bill.format(it.totalMinor)}") }
    }.joinToString("\n")
    context.open(ShareCardRenderer.shareIntent(text, image, title = "The split"))
}

/** Opens [intent]; false when no app on the phone can take it. */
private fun Context.open(intent: android.content.Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}

private const val NAME_LENGTH = 60
