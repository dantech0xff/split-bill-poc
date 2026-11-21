package com.dantech.splitvibe.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvBesideOrBelow
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvQuietIconTile
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
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
 * a card of its own with when it comes back and the day of its next draft, to pause, resume or
 * delete. On each of those days a draft of the bill is made for the user to check and finish.
 * [onDelete] asks to delete one, which [DeleteRecurringBillDialog] then confirms.
 */
internal fun LazyListScope.recurringBills(rows: List<RecurringRow>?, actions: RecurringActions, onDelete: (String) -> Unit) {
    when {
        rows == null -> item(key = "recurring-loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

        rows.isEmpty() -> item(key = "recurring-empty") {
            SvEmptyState(
                icon = SvActionIcons.Repeat,
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
                    modifier = rowMotion().padding(
                        start = SvSpacing.screen,
                        end = SvSpacing.screen,
                        top = SvSpacing.xs,
                        bottom = SvSpacing.md,
                    ),
                )
            }
            items(rows, key = { row -> "recurring-${row.bill.id}" }) { row ->
                RecurringBillRow(
                    row = row,
                    actions = actions,
                    onDelete = { onDelete(row.bill.id) },
                    modifier = rowMotion().padding(bottom = SvSpacing.md),
                )
            }
        }
    }
}

/** Asks whether to delete the recurring bill [id], one of [rows], and deletes it with [onDelete] when the user says so. */
@Composable
internal fun DeleteRecurringBillDialog(id: String, rows: List<RecurringRow>?, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    val name = rows?.firstOrNull { it.bill.id == id }?.bill?.name ?: stringResource(R.string.recurring_this_bill)
    // No more drafts come of a deleted recurring bill, and it cannot be had back, so the button that deletes it is red.
    SvDialog(
        title = stringResource(R.string.history_delete_title, name),
        text = stringResource(R.string.recurring_delete_text),
        confirmLabel = stringResource(R.string.common_delete),
        onConfirm = {
            onDelete(id)
            onDismiss()
        },
        dismissLabel = stringResource(R.string.common_cancel),
        onDismiss = onDismiss,
        irreversible = true,
    )
}

/**
 * One recurring bill as a card of its own: what it is called and its total, when it comes back, and
 * the day of its next draft, or a tag while it is paused, with a menu to pause, resume or delete it.
 */
@Composable
private fun RecurringBillRow(row: RecurringRow, actions: RecurringActions, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val bill = row.bill
    SvListRow(position = SvRowPosition.Single, modifier = modifier) {
        SvQuietIconTile(SvActionIcons.Repeat)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            // Beside the name while its longest word fits there, and below it when not, as on the list of all bills.
            SvBesideOrBelow(
                first = { Text(bill.name, style = MaterialTheme.typography.bodyLargeEmphasized) },
                second = { Text(bill.bill.format(bill.bill.totalMinor), style = MaterialTheme.typography.amountMedium) },
                secondBelowAt = Alignment.Start,
                gap = SvSpacing.sm,
                stackedGap = SvSpacing.xs,
            )
            Text(
                describe(bill.schedule).resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (bill.paused) {
                SvTag(stringResource(R.string.recurring_paused))
            } else {
                Text(detailOf(row).resolve(), style = MaterialTheme.typography.bodySmallEmphasized)
            }
        }
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
