package com.dantech.splitvibe.ui.recurring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** What the screen of recurring bills can ask for. */
class RecurringActions(
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onBack: () -> Unit,
)

/**
 * The recurring bills, such as the rent: each with when it comes back and the day of its next
 * draft, to pause, resume or delete. On each of those days a draft of the bill is made for the
 * user to check and finish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(rows: List<RecurringRow>?, actions: RecurringActions) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring bills") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            when {
                rows == null -> item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

                rows.isEmpty() -> item(key = "empty") {
                    SvEmptyState(
                        icon = Icons.Default.DateRange,
                        text = "No recurring bills yet. To repeat a bill such as the rent, open the menu of a finished bill " +
                            "in your bills and choose Repeat this bill.",
                        modifier = rowMotion(),
                    )
                }

                else -> {
                    item(key = "intro") {
                        Text(
                            "On each day a recurring bill comes back, a draft of it is made for you to check and finish.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                        )
                    }
                    itemsIndexed(rows, key = { _, row -> row.bill.id }) { index, row ->
                        RecurringBillRow(row, SvRowPosition.of(index, rows.size), actions, onDelete = { deleting = row.bill.id })
                    }
                }
            }
        }
    }

    deleting?.let { id ->
        val name = rows?.firstOrNull { it.bill.id == id }?.bill?.name ?: "this recurring bill"
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = { Text("No more drafts of it are made. The drafts it made already stay with your bills.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(id)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecurringBillRow(row: RecurringRow, position: SvRowPosition, actions: RecurringActions, onDelete: () -> Unit) {
    val bill = row.bill
    val detail = if (bill.paused) "Paused" else "Next draft ${dayText(row.next)}"
    SvListRow(position = position) {
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, style = MaterialTheme.typography.bodyLarge)
            Text(describe(bill.schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(bill.bill.format(bill.bill.totalMinor), style = MaterialTheme.typography.amountSmall)
        SvOverflowMenu(
            label = "More for ${bill.name}",
            options = listOf(
                if (bill.paused) {
                    SvMenuOption("Resume") { actions.onResume(bill.id) }
                } else {
                    SvMenuOption("Pause") { actions.onPause(bill.id) }
                },
                SvMenuOption("Delete", onDelete),
            ),
        )
    }
}

/** When [schedule] comes back, in English as the app is, such as "Every month on the 31st, or the last day of a shorter month". */
internal fun describe(schedule: RecurrenceSchedule): String {
    val start = schedule.start
    return when (schedule.frequency) {
        Frequency.WEEKLY -> "Every week on ${start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"

        Frequency.MONTHLY -> {
            val day = start.dayOfMonth
            val suffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }
            val shorter = if (day > SHORTEST_MONTH) ", or the last day of a shorter month" else ""
            "Every month on the $day$suffix$shorter"
        }
    }
}

/** A day as the app shows dates, such as "Dec 1, 2026". */
internal fun dayText(day: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)

/** The days every month has. */
private const val SHORTEST_MONTH = 28
