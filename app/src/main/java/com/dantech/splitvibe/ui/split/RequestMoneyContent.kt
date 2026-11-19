package com.dantech.splitvibe.ui.split

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.res.Resources
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.splitvibe.R
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
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.requests.RequestMoneyViewModel
import java.util.UUID
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
    // Read here, where a change of configuration reaches it, for the texts that go to the share sheet.
    val resources = LocalResources.current
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
        // A name of its own for every share; without the picture, for example on a full disk, the text still goes.
        val image = prepared.paying?.qrPayload?.let { payload ->
            runCatching {
                val bitmap = withContext(Dispatchers.Default) { QrCodes.bitmap(payload) }
                withContext(Dispatchers.IO) { ShareCardRenderer.save(context, bitmap, "pay-${UUID.randomUUID()}") }
            }.getOrNull()
        }
        val text = prepared.text.resolve(resources)
        if (context.open(ShareCardRenderer.shareIntent(text, image, title = resources.getString(R.string.requests_share_title)))) {
            viewModel.markShared(person.participant.id)
        }
    }

    Scaffold(bottomBar = { SvBottomBar { SvPrimaryButton(stringResource(R.string.common_done), onClick = onDone) } }) { padding ->
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
                    Text(stringResource(R.string.requests_ask_for_money), style = MaterialTheme.typography.headlineSmallEmphasized)
                    Spacer(Modifier.height(SvSpacing.sm))
                    Text(
                        stringResource(R.string.requests_ask_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                SummaryCard(result = result, state = card, modifier = Modifier.padding(horizontal = SvSpacing.lg))
                OutlinedButton(
                    onClick = { scope.launch { shareSummary(context, resources, card, result) } },
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(SvSpacing.sm))
                    Text(stringResource(R.string.requests_share_picture))
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
                        label = { Text(stringResource(R.string.requests_your_name)) },
                        supportingText = { Text(stringResource(R.string.requests_your_name_hint)) },
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
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.common_ok)) } },
            text = { Text(it.resolve()) },
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
                listOfNotNull(
                    bill.merchant?.takeIf { it.isNotBlank() } ?: stringResource(R.string.common_the_bill),
                    bill.date,
                ).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(bill.format(bill.totalMinor), style = MaterialTheme.typography.amountLarge)
            result.split.shares.forEach { share ->
                val name = result.nameOf(share.personId).resolve()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SvAvatar(name)
                    Spacer(Modifier.width(SvSpacing.md))
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(bill.format(share.totalMinor), style = MaterialTheme.typography.amountMedium)
                }
            }
            Text(
                stringResource(R.string.requests_split_with),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            text = stringResource(R.string.requests_add_method_hint),
            isError = false,
            modifier = modifier,
            action = stringResource(R.string.requests_add_method),
            onAction = onOpenPaymentMethods,
        )
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.requests_paid_back_by, method.method.title), style = MaterialTheme.typography.titleSmall)
            Text(method.method.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = if (hasChoice) onChoose else onOpenPaymentMethods) {
            Text(stringResource(if (hasChoice) R.string.requests_change_method else R.string.requests_add_another))
        }
    }
}

@Composable
private fun PersonRow(result: SplitResultState, request: PersonRequest, position: SvRowPosition, shared: Boolean, onShare: () -> Unit) {
    val name = result.nameOf(request.participant.id).resolve()
    SvListRow(position = position) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(result.bill.format(request.amountMinor), style = MaterialTheme.typography.amountMedium)
            result.converted(request.amountMinor)?.let {
                Text(
                    stringResource(R.string.split_about, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(SvSpacing.md))
        FilledTonalButton(onClick = onShare) {
            Icon(if (shared) Icons.Default.Check else Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(SvSpacing.xs))
            Text(stringResource(if (shared) R.string.requests_send_again else R.string.requests_send))
        }
    }
}

@Composable
private fun MethodSheet(methods: List<SavedPaymentMethod>, chosen: String?, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    SvBottomSheet(
        title = stringResource(R.string.requests_get_paid_by),
        onDismiss = onDismiss,
        actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
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

private suspend fun shareSummary(context: Context, resources: Resources, card: ShareCardState, result: SplitResultState) {
    val bill = result.bill
    val image = runCatching {
        val bitmap = card.toBitmap()
        withContext(Dispatchers.IO) { ShareCardRenderer.save(context, bitmap, "split-${UUID.randomUUID()}") }
    }.getOrNull()
    val text = buildList {
        val title = bill.merchant?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.common_the_bill)
        add(resources.getString(R.string.requests_summary_line, title, bill.format(bill.totalMinor)))
        result.split.shares.forEach { share ->
            val name = result.nameOf(share.personId).resolve(resources)
            add(resources.getString(R.string.requests_summary_line, name, bill.format(share.totalMinor)))
        }
    }.joinToString("\n")
    context.open(ShareCardRenderer.shareIntent(text, image, title = resources.getString(R.string.requests_summary_share_title)))
}

/** Opens [intent]; false when no app on the phone can take it. */
private fun Context.open(intent: android.content.Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
}

private const val NAME_LENGTH = 60
