package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.data.contacts.Contact
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.PersonShare
import java.math.BigDecimal

private const val ME_LABEL = "Me"

/**
 * Second step of a split: the bill as the model read it, who shares it, who shares each item, and
 * what each person pays as a result.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultContent(
    result: SplitResultState,
    contacts: List<Contact>,
    onBack: () -> Unit,
    onToggleMe: () -> Unit,
    onToggleContact: (String) -> Unit,
    onSetItemSharers: (Int, Set<String>) -> Unit,
    onSendRequests: () -> Unit,
) {
    var showPeoplePicker by rememberSaveable { mutableStateOf(false) }
    var editingItem by rememberSaveable { mutableStateOf<Int?>(null) }
    var showOcrText by rememberSaveable { mutableStateOf(false) }

    val bill = result.bill
    val plan = result.plan
    val contactsById = remember(contacts) { contacts.associateBy { it.id } }
    val nameOf: (String) -> String = { id -> if (id == ME_ID) ME_LABEL else contactsById[id]?.name ?: id }
    val participants = plan.participantIds
    val sharersByItem = remember(plan) { plan.sharersByItem() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bill.merchant ?: "Bill", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            RequestBar(result = result, onSendRequests = onSendRequests, onPickPeople = { showPeoplePicker = true })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                TotalCard(
                    bill = bill,
                    summary = result.summary,
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                )
            }

            items(plan.openNotices() + bill.warnings) { note ->
                SvMessageCard(text = note, isError = false, modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs))
            }

            item {
                SvSectionHeader("Who is sharing?")
                FlowRow(
                    modifier = Modifier.padding(horizontal = SvSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    FilterChip(selected = plan.includeMe, onClick = onToggleMe, label = { Text(ME_LABEL) })
                    plan.selectedContactIds.forEach { id ->
                        InputChip(
                            selected = true,
                            onClick = { onToggleContact(id) },
                            label = { Text(nameOf(id)) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove ${nameOf(id)}", modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                    AssistChip(
                        onClick = { showPeoplePicker = true },
                        label = { Text("Add people") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            if (bill.items.isNotEmpty()) {
                item { SvSectionHeader("Items", hint = "Tap an item to choose who shares it.") }
                itemsIndexed(bill.items) { index, item ->
                    ItemRow(
                        bill = bill,
                        item = item,
                        position = SvRowPosition.of(index, bill.items.size),
                        sharers =
                            sharersByItem[index]?.let { ids ->
                                participants.filter { it in ids }.joinToString { nameOf(it) }
                            } ?: "Everyone",
                        onClick = { if (participants.size >= 2) editingItem = index else showPeoplePicker = true },
                    )
                }
            }

            if (bill.charges.isNotEmpty()) {
                item { SvSectionHeader("Taxes, fees and discounts", hint = "Shared in proportion to each person's items.") }
                itemsIndexed(bill.charges) { index, charge ->
                    ChargeRow(bill, charge, SvRowPosition.of(index, bill.charges.size))
                }
            }

            item { SvSectionHeader("Each person pays") }
            if (result.split.shares.isEmpty()) {
                item {
                    Text(
                        "Nobody is sharing this bill yet. Add yourself or pick people.",
                        modifier = Modifier.padding(horizontal = SvSpacing.lg),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            itemsIndexed(result.split.shares, key = { _, share -> share.personId }) { index, share ->
                ShareRow(
                    bill = bill,
                    share = share,
                    name = nameOf(share.personId),
                    position = SvRowPosition.of(index, result.split.shares.size),
                )
            }
            if (result.split.shares.isNotEmpty()) {
                item { SharesTotalRow(bill = bill, total = result.split.totalMinor) }
            }

            item {
                TextButton(onClick = { showOcrText = !showOcrText }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showOcrText) "Hide what was sent to Claude" else "Show what was sent to Claude")
                }
                if (showOcrText) {
                    Text(
                        text = result.ocrDocument,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showPeoplePicker) {
        PeoplePickerSheet(
            contacts = contacts,
            selectedIds = plan.selectedContactIds.toSet(),
            onToggle = onToggleContact,
            onDismiss = { showPeoplePicker = false },
        )
    }

    editingItem?.let { index ->
        val item = bill.items.getOrNull(index)
        if (item != null) {
            ItemSharersSheet(
                title = item.name,
                amount = bill.format(item.amountMinor),
                people = participants.map { it to nameOf(it) },
                initiallySharing = sharersByItem[index] ?: participants.toSet(),
                onConfirm = { sharers ->
                    onSetItemSharers(index, sharers)
                    editingItem = null
                },
                onDismiss = { editingItem = null },
            )
        }
    }
}

@Composable
private fun TotalCard(bill: Bill, summary: String, modifier: Modifier = Modifier) {
    SvCard(
        modifier = modifier.fillMaxWidth(),
        elevation = SvElevation.Level2,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(SvSpacing.xl)) {
            Text("Bill total", style = MaterialTheme.typography.labelLarge)
            Text(bill.format(bill.totalMinor), style = MaterialTheme.typography.amountLarge)
            bill.date?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(SvSpacing.sm))
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ItemRow(bill: Bill, item: BillItem, position: SvRowPosition, sharers: String, onClick: () -> Unit) {
    SvListRow(position = position, onClick = onClick, onClickLabel = "Choose who shares ${item.name}") {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            val unitPrice = item.unitPriceMinor
            if (unitPrice != null && item.quantity.compareTo(BigDecimal.ONE) != 0) {
                Text(
                    "${item.quantity.stripTrailingZeros().toPlainString()} × ${bill.format(unitPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(sharers, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(bill.format(item.amountMinor), style = MaterialTheme.typography.amountSmall)
    }
}

@Composable
private fun ChargeRow(bill: Bill, charge: BillCharge, position: SvRowPosition) {
    SvListRow(position = position) {
        Text(charge.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(SvSpacing.md))
        Text(bill.format(charge.amountMinor), style = MaterialTheme.typography.amountSmall)
    }
}

@Composable
private fun ShareRow(bill: Bill, share: PersonShare, name: String, position: SvRowPosition) {
    SvListRow(position = position) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (share.personId == ME_ID) "$name (paid the bill)" else name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Items ${bill.format(share.itemsMinor)} · charges ${bill.format(share.chargesMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(bill.format(share.totalMinor), style = MaterialTheme.typography.amountMedium)
    }
}

/** The proof under the shares: together they are exactly the bill total. */
@Composable
private fun SharesTotalRow(bill: Bill, total: Long) {
    Row(
        modifier = Modifier.padding(horizontal = SvSpacing.xxl, vertical = SvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(SvSpacing.sm))
        Text("Adds up to the bill total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(bill.format(total), style = MaterialTheme.typography.amountMedium)
    }
}

@Composable
private fun RequestBar(result: SplitResultState, onSendRequests: () -> Unit, onPickPeople: () -> Unit) {
    val others = result.split.shares.filter { it.personId != ME_ID && it.totalMinor > 0 }
    val amount = others.sumOf { it.totalMinor }
    SvBottomBar {
        when {
            result.plan.selectedContactIds.isEmpty() -> SvPrimaryButton("Pick people to share with", onClick = onPickPeople)

            others.isEmpty() -> SvPrimaryButton("Nobody else owes anything", onClick = {}, enabled = false)

            else -> {
                val people = if (others.size == 1) "1 person" else "${others.size} people"
                SvPrimaryButton("Request ${result.bill.format(amount)} from $people", onClick = onSendRequests)
            }
        }
    }
}
