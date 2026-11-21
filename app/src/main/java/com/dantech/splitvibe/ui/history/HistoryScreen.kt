package com.dantech.splitvibe.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.dayOf
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvFieldRow
import com.dantech.splitvibe.designsystem.components.SvFieldWidth
import com.dantech.splitvibe.designsystem.components.SvIconTile
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvTabs
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.domain.shortDateText
import com.dantech.splitvibe.text.labelRes
import com.dantech.splitvibe.ui.common.style
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
            SvTopBar(title = stringResource(R.string.groups_bills), backLabel = stringResource(R.string.common_back), onBack = onBack)
        },
    ) { padding ->
        // One list for both tabs, in which the tabs stay one and the same item, so a screen reader and
        // the keyboard keep their place on the tab just chosen.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = SvSpacing.sm, bottom = SvSpacing.xl),
        ) {
            // Searching finds bills only, so the Recurring tab has no field to search with.
            if (tab == BillsTab.ALL) {
                item(key = "search") {
                    SvTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            onQueryChanged(it)
                        },
                        // The magnifier and the words in the field say what it is for, as the design draws it.
                        label = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md),
                        placeholder = stringResource(R.string.history_search),
                        leadingIcon = Icons.Default.Search,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
            }
            item(key = "tabs") {
                SvTabs(
                    tabs = listOf(stringResource(R.string.history_all_bills), stringResource(R.string.history_recurring)),
                    selected = tab.ordinal,
                    onSelect = { tab = BillsTab.entries[it] },
                    modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md),
                )
            }
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
                            today = today,
                            onDelete = { deleting = listing.id },
                            onRepeat = if (repeatable) ({ repeating = listing.id }) else null,
                            withCategory = true,
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
        // A deleted bill cannot be had back, so the button that deletes it is red.
        SvDialog(
            title = stringResource(R.string.history_delete_title, title),
            text = stringResource(R.string.history_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                onDelete(id)
                deleting = null
            },
            dismissLabel = stringResource(R.string.common_cancel),
            onDismiss = { deleting = null },
            irreversible = true,
        )
    }
}

/**
 * One bill in a list of bills: what it was spent on as the tile of its category, where, when and with
 * whom, its total, and whether it is still a draft. Its day is written short, as of [today], when its
 * date can be read, and as the bill printed it when not. With [withCategory] the line under its name
 * names the category in words too, as the list of all bills does.
 *
 * With [onDelete] it has a menu to delete it, and to repeat it with [onRepeat]. The menu then takes
 * the end of the row, and the total moves up beside the name, so that the line under it keeps the
 * width of the row; where a word of the name would not fit beside the total, as with large text, the
 * total goes below the name.
 */
@Composable
internal fun BillListingRow(
    listing: BillListing,
    position: SvRowPosition,
    onOpen: () -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    withCategory: Boolean = false,
) {
    val title = titleOf(listing)
    val total = BillCurrency(listing.currency, listing.fractionDigits).format(listing.totalMinor)
    val category = BillCategory.fromWire(listing.category) ?: BillCategory.OTHER
    val categoryName = stringResource(category.labelRes)
    val day = dayText(listing, today)
    val people = listing.people.takeIf { it.isNotBlank() }
    val draft = stringResource(R.string.history_draft).takeIf { listing.status == BillStatus.DRAFT.name }
    // The tile shows the category and the tag the draft; a screen reader hears both in words.
    val spoken = listOfNotNull(day, categoryName, people, draft).joinToString(" · ")
    SvListRow(
        position = position,
        label = stringResource(R.string.history_row_label, title, total, spoken),
        onClickLabel = stringResource(R.string.history_open, title),
        onClick = onOpen,
        modifier = modifier,
    ) {
        val style = category.style
        SvIconTile(style.icon, style.color)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            val name = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    Text(title, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMediumEmphasized)
                    if (draft != null) SvTag(draft)
                }
            }
            if (onDelete == null) {
                name()
            } else {
                // Beside the name while its longest word fits there, and below it when not, as with large text.
                SvFieldRow(widths = NAME_AND_TOTAL, alignment = Alignment.CenterVertically) {
                    name()
                    Text(total, style = MaterialTheme.typography.amountSmall)
                }
            }
            Text(
                listOfNotNull(day, categoryName.takeIf { withCategory }, people).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete == null) {
            Spacer(Modifier.width(SvSpacing.md))
            Text(total, style = MaterialTheme.typography.amountSmall)
        } else {
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

/** The name of a bill, which goes on to more lines between words, and its total, which keeps its width. */
private val NAME_AND_TOTAL = listOf(SvFieldWidth.Share(1f, mustFit = false), SvFieldWidth.Content)

/**
 * The day of [listing] as a list of bills writes it: its date written short as of [today] when the
 * date can be read, the date as the bill printed it when it cannot, and the day it was saved when the
 * bill has no date at all.
 */
private fun dayText(listing: BillListing, today: LocalDate): String {
    val printed = listing.date?.takeIf { it.isNotBlank() }
    val day = if (printed == null) Instant.ofEpochMilli(listing.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate() else dayOf(printed)
    return day?.let { shortDateText(it, today) } ?: printed.orEmpty()
}
