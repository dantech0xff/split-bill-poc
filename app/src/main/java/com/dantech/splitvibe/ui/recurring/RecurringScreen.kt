package com.dantech.splitvibe.ui.recurring

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.splitvibe.R
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
import com.dantech.splitvibe.domain.TEXT_LOCALE
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.dateText
import com.dantech.splitvibe.text.message
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.text.text
import java.time.LocalDate
import java.time.format.TextStyle

/** What the recurring bills on the Recurring tab of the list of bills can ask for. */
class RecurringActions(val onPause: (String) -> Unit, val onResume: (String) -> Unit, val onDelete: (String) -> Unit)

/**
 * The recurring bills, such as the rent, as items of the list of bills on its Recurring tab: each
 * with when it comes back and the day of its next draft, to pause, resume or delete. On each of those
 * days a draft of the bill is made for the user to check and finish. [onDelete] asks to delete one,
 * which [DeleteRecurringBillDialog] then confirms.
 */
internal fun LazyListScope.recurringBills(rows: List<RecurringRow>?, actions: RecurringActions, onDelete: (String) -> Unit) {
    when {
        rows == null -> item(key = "recurring-loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

        rows.isEmpty() -> item(key = "recurring-empty") {
            SvEmptyState(
                icon = Icons.Default.DateRange,
                text = stringResource(R.string.recurring_none),
                modifier = rowMotion(),
            )
        }

        else -> {
            item(key = "recurring-intro") {
                Text(
                    stringResource(R.string.recurring_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                )
            }
            itemsIndexed(rows, key = { _, row -> "recurring-${row.bill.id}" }) { index, row ->
                RecurringBillRow(row, SvRowPosition.of(index, rows.size), actions, onDelete = { onDelete(row.bill.id) })
            }
        }
    }
}

/** Asks whether to delete the recurring bill [id], one of [rows], and deletes it with [onDelete] when the user says so. */
@Composable
internal fun DeleteRecurringBillDialog(id: String, rows: List<RecurringRow>?, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    val name = rows?.firstOrNull { it.bill.id == id }?.bill?.name ?: stringResource(R.string.recurring_this_bill)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_title, name)) },
        text = { Text(stringResource(R.string.recurring_delete_text)) },
        confirmButton = {
            TextButton(onClick = {
                onDelete(id)
                onDismiss()
            }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun RecurringBillRow(row: RecurringRow, position: SvRowPosition, actions: RecurringActions, onDelete: () -> Unit) {
    val bill = row.bill
    SvListRow(position = position) {
        Column(modifier = Modifier.weight(1f)) {
            Text(bill.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                describe(bill.schedule).resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(detailOf(row).resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(bill.bill.format(bill.bill.totalMinor), style = MaterialTheme.typography.amountSmall)
        SvOverflowMenu(
            label = stringResource(R.string.groups_more_for, bill.name),
            options = listOf(
                if (bill.paused) {
                    SvMenuOption(stringResource(R.string.recurring_resume)) { actions.onResume(bill.id) }
                } else {
                    SvMenuOption(stringResource(R.string.recurring_pause)) { actions.onPause(bill.id) }
                },
                SvMenuOption(stringResource(R.string.common_delete), onDelete),
            ),
        )
    }
}

/**
 * When [schedule] comes back, such as "Every month on the 31st, or the last day of a shorter month".
 * The day of the week is named in the language of the texts, and the resource gives the day of the
 * month the ending of its ordinal.
 */
internal fun describe(schedule: RecurrenceSchedule): UiText {
    val start = schedule.start
    return when (schedule.frequency) {
        Frequency.WEEKLY -> text(R.string.recurring_every_week, start.dayOfWeek.getDisplayName(TextStyle.FULL, TEXT_LOCALE))

        Frequency.MONTHLY -> {
            val day = start.dayOfMonth
            val every = message(R.string.recurring_every_month, day)
            if (day > SHORTEST_MONTH) text(R.string.recurring_or_last_day, every) else every
        }
    }
}

/** Where [row] is: paused, with a draft due and not made yet, or with the day of its next draft. */
internal fun detailOf(row: RecurringRow): UiText = when {
    row.bill.paused -> text(R.string.recurring_paused)
    row.due -> text(R.string.recurring_draft_due, dayText(row.next))
    else -> text(R.string.recurring_next_draft, dayText(row.next))
}

/** A day as the app shows dates, such as "Dec 1, 2026". */
internal fun dayText(day: LocalDate): String = dateText(day)

/** The days every month has. */
private const val SHORTEST_MONTH = 28
