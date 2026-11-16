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
import androidx.compose.ui.text.input.ImeAction
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
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
import com.dantech.splitvibe.ui.recurring.RepeatBillSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Every bill kept on the phone, newest first, to search, open again or delete, and a finished bill
 * to repeat every week or month with [onRepeat]. [today] is the first day a repeated bill can
 * come back on.
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
    onRepeat: ((billId: String, name: String, schedule: RecurrenceSchedule) -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var repeating by rememberSaveable { mutableStateOf<String?>(null) }
    // Kept here as typed, so the field never waits for the search to answer.
    var typed by rememberSaveable { mutableStateOf(query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
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
                    placeholder = { Text("Search by place, item or person") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            when {
                listings == null -> item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

                listings.isEmpty() -> item(key = "empty") {
                    SvEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        text = if (typed.isBlank()) NO_BILLS else "No bill matches “${typed.trim()}”.",
                        modifier = rowMotion(),
                    )
                }

                else -> itemsIndexed(listings, key = { _, listing -> listing.id }) { index, listing ->
                    BillListingRow(
                        listing = listing,
                        position = SvRowPosition.of(index, listings.size),
                        onOpen = { onOpen(listing.id) },
                        onDelete = { deleting = listing.id },
                        // Only a finished split is worth repeating.
                        onRepeat = if (onRepeat != null && listing.status == BillStatus.FINAL.name) ({ repeating = listing.id }) else null,
                        modifier = rowMotion(),
                    )
                }
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
                onRepeat = { name, schedule ->
                    repeating = null
                    onRepeat(id, name, schedule)
                },
                onDismiss = { repeating = null },
            )
        }
    }

    deleting?.let { id ->
        val title = listings?.firstOrNull { it.id == id }?.let(::titleOf) ?: "this bill"
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $title?") },
            text = { Text("The bill, its split and its photos are deleted from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
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
        (BillCategory.fromWire(listing.category) ?: BillCategory.OTHER).label,
        listing.people.takeIf { it.isNotBlank() },
        "Draft".takeIf { listing.status == BillStatus.DRAFT.name },
    ).joinToString(" · ")
    SvListRow(
        position = position,
        label = "$title, $total, $detail",
        onClickLabel = "Open $title",
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
                label = "More for $title",
                options = listOfNotNull(onRepeat?.let { SvMenuOption("Repeat this bill", it) }, SvMenuOption("Delete", onDelete)),
            )
        }
    }
}

private fun titleOf(listing: BillListing): String = listing.merchant?.takeIf { it.isNotBlank() } ?: "Bill"

private fun dayOf(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private const val NO_BILLS = "No bills yet. Every bill you split is kept here."
