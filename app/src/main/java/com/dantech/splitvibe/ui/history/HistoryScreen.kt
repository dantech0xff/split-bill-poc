package com.dantech.splitvibe.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.text.dateText
import com.dantech.splitvibe.text.labelRes
import com.dantech.splitvibe.ui.recurring.DeleteRecurringBillDialog
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.recurring.RecurringRow
import com.dantech.splitvibe.ui.recurring.RepeatBillSheet
import com.dantech.splitvibe.ui.recurring.recurringBills
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The two tabs of the list of bills. */
private enum class BillsTab { ALL, RECURRING }

/**
 * The list of bills, in two tabs. "All bills" has every bill kept on the phone, newest first, to
 * search, open again or delete, and a finished bill to repeat every week or month with [onRepeat],
 * which then shows the bill on the other tab. "Recurring" has the [recurring] bills, which
 * [recurringActions] pause, resume and delete. [today] is the first day a repeated bill can come
 * back on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    listings: List<BillListing>?,
    query: String,
    onQueryChanged: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    recurring: List<RecurringRow>?,
    recurringActions: RecurringActions,
    onRepeat: ((billId: String, name: String, schedule: RecurrenceSchedule, firstDraft: LocalDate) -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
) {
    // The open tab is part of the screen, so it is still open after a rotation, and when the
    // system brings the app back after it ended it.
    var tab by rememberSaveable { mutableStateOf(BillsTab.ALL) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingRecurring by rememberSaveable { mutableStateOf<String?>(null) }
    var repeating by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept here as typed, so the field never waits for the search to answer.
    var typed by rememberSaveable { mutableStateOf(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_bills)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        // One list for both tabs, in which the choice of tab stays one and the same item, so a screen
        // reader and the keyboard keep their place on it when the tab changes.
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            // Searching finds bills only, so the Recurring tab has no field to search with.
            if (tab == BillsTab.ALL) {
                item(key = "search") {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            onQueryChanged(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                        placeholder = { Text(stringResource(R.string.history_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
            }
            item(key = "tabs") { BillsTabs(selected = tab, onSelect = { tab = it }) }
            when (tab) {
                BillsTab.ALL -> when {
                    listings == null -> item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

                    listings.isEmpty() -> item(key = "empty") {
                        SvEmptyState(
                            icon = Icons.AutoMirrored.Filled.List,
                            text = if (typed.isBlank()) {
                                stringResource(R.string.history_none)
                            } else {
                                stringResource(R.string.history_no_match, typed.trim())
                            },
                            modifier = rowMotion(),
                        )
                    }

                    else -> itemsIndexed(listings, key = { _, listing -> listing.id }) { index, listing ->
                        // Only a finished split is worth repeating.
                        val repeatable = onRepeat != null && listing.status == BillStatus.FINAL.name
                        BillListingRow(
                            listing = listing,
                            position = SvRowPosition.of(index, listings.size),
                            onOpen = { onOpen(listing.id) },
                            onDelete = { deleting = listing.id },
                            onRepeat = if (repeatable) ({ repeating = listing.id }) else null,
                            modifier = rowMotion(),
                        )
                    }
                }

                BillsTab.RECURRING -> recurringBills(rows = recurring, actions = recurringActions, onDelete = { deletingRecurring = it })
            }
        }
    }

    repeating?.let { id ->
        val listing = listings?.firstOrNull { it.id == id }
        if (listing != null && onRepeat != null) {
            RepeatBillSheet(
                name = listing.merchant?.trim().orEmpty(),
                date = listing.date,
                today = today,
                onRepeat = { name, schedule, firstDraft ->
                    repeating = null
                    onRepeat(id, name, schedule, firstDraft)
                    // The bill now recurs, which the other tab shows, on this same screen.
                    tab = BillsTab.RECURRING
                },
                onDismiss = { repeating = null },
            )
        }
    }

    deletingRecurring?.let { id ->
        DeleteRecurringBillDialog(id = id, rows = recurring, onDelete = recurringActions.onDelete, onDismiss = { deletingRecurring = null })
    }

    deleting?.let { id ->
        val title = listings?.firstOrNull { it.id == id }?.let { titleOf(it) } ?: stringResource(R.string.history_this_bill)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.history_delete_title, title)) },
            text = { Text(stringResource(R.string.history_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    deleting = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The choice between the two tabs of the list of bills, read by a screen reader as a choice of one. */
@Composable
private fun BillsTabs(selected: BillsTab, onSelect: (BillsTab) -> Unit) {
    SvChoiceGroup(
        options = listOf(stringResource(R.string.history_all_bills), stringResource(R.string.history_recurring)),
        selected = selected.ordinal,
        onSelect = { onSelect(BillsTab.entries[it]) },
        modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
    )
}

/**
 * One bill in a list of bills: where, when, on what and with whom, its total, and whether it is
 * still a draft. With [onDelete] it has a menu to delete it, and to repeat it with [onRepeat].
 */
@Composable
internal fun BillListingRow(
    listing: BillListing,
    position: SvRowPosition,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
) {
    val title = titleOf(listing)
    val total = BillCurrency(listing.currency, listing.fractionDigits).format(listing.totalMinor)
    val detail = listOfNotNull(
        listing.date ?: dayOf(listing.updatedAt),
        stringResource((BillCategory.fromWire(listing.category) ?: BillCategory.OTHER).labelRes),
        listing.people.takeIf { it.isNotBlank() },
        stringResource(R.string.history_draft).takeIf { listing.status == BillStatus.DRAFT.name },
    ).joinToString(" · ")
    SvListRow(
        position = position,
        label = stringResource(R.string.history_row_label, title, total, detail),
        onClickLabel = stringResource(R.string.history_open, title),
        onClick = onOpen,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(total, style = MaterialTheme.typography.amountSmall)
        if (onDelete != null) {
            SvOverflowMenu(
                label = stringResource(R.string.groups_more_for, title),
                options = listOfNotNull(
                    onRepeat?.let { SvMenuOption(stringResource(R.string.history_repeat), it) },
                    SvMenuOption(stringResource(R.string.common_delete), onDelete),
                ),
            )
        }
    }
}

@Composable
private fun titleOf(listing: BillListing): String = listing.merchant?.takeIf { it.isNotBlank() } ?: stringResource(R.string.common_bill)

private fun dayOf(millis: Long): String = dateText(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
