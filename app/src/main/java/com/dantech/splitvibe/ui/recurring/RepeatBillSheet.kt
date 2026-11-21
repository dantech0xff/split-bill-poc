package com.dantech.splitvibe.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.dayOf
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.text.resolve
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Makes a bill come back every week or every month: what to call it, how often, and the day of its
 * first draft, which is today at the earliest. Until the user picks a day, the bill comes back on its
 * own day of the week or month, or from today for a bill without a date, and its first draft is the
 * next of those days after the bill itself.
 *
 * @param name what the bill is called now, such as its merchant.
 * @param date the bill's date as written on it, which may be no day at all.
 * @param onRepeat gets the name, the schedule and the day of the first draft, an occurrence of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatBillSheet(
    name: String,
    date: String?,
    today: LocalDate,
    onRepeat: (name: String, schedule: RecurrenceSchedule, firstDraft: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf(name) }
    var frequency by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    // Kept as an epoch day, which saves as a plain number; null until the user picks a day.
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val picked = pickedDay?.let(LocalDate::ofEpochDay)
    // A day the user picks starts the schedule. Until then it keeps to the bill's own day, so a rent
    // dated the 31st comes back on the 31st, and only its first draft is on the 30th of a shorter month.
    val schedule = picked?.let { RecurrenceSchedule(frequency, it) } ?: suggestedSchedule(date, frequency, today)
    val firstDraft = picked ?: firstDraftDay(schedule, today)

    SvBottomSheet(
        title = stringResource(R.string.history_repeat),
        supportingText = stringResource(R.string.recurring_repeat_hint),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.common_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.recurring_repeat),
                onClick = { onRepeat(typed.trim(), schedule, firstDraft) },
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            SvTextField(
                value = typed,
                onValueChange = { typed = it },
                label = stringResource(R.string.common_name),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.recurring_name_placeholder),
                maxLength = RecurringBillRepository.MAX_NAME,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            SvChoiceGroup(
                options = listOf(stringResource(R.string.recurring_weekly), stringResource(R.string.recurring_monthly)),
                selected = if (frequency == Frequency.WEEKLY) 0 else 1,
                onSelect = { frequency = if (it == 0) Frequency.WEEKLY else Frequency.MONTHLY },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                val changeLabel = stringResource(R.string.recurring_change_first_draft)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.recurring_first_draft, dayText(firstDraft)),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                    Text(
                        describe(schedule).resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SvQuietButton(
                    stringResource(R.string.requests_change_method),
                    onClick = { picking = true },
                    // Said in full, as "Change" alone does not tell someone who hears the screen what it changes.
                    modifier = Modifier.semantics { contentDescription = changeLabel },
                )
            }
        }
    }

    if (picking) {
        val firstAllowed = today.toEpochDay() * DAY_MS
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = firstDraft.toEpochDay() * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= firstAllowed

                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker counts days in UTC, from midnight.
                    picker.selectedDateMillis?.let { pickedDay = Math.floorDiv(it, DAY_MS) }
                    picking = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(state = picker)
        }
    }
}

/** The schedule of a bill dated [date] until the user picks a day: from the bill's own day, or from [today] for a bill without one. */
internal fun suggestedSchedule(date: String?, frequency: Frequency, today: LocalDate): RecurrenceSchedule =
    RecurrenceSchedule(frequency, dayOf(date) ?: today)

/**
 * The first draft of [schedule] until the user picks a day. The bill itself is the occurrence the
 * schedule starts from, so the first draft is the one after it, and today at the earliest.
 */
internal fun firstDraftDay(schedule: RecurrenceSchedule, today: LocalDate): LocalDate =
    Recurrence.next(schedule, maxOf(schedule.start, today.minusDays(1)))

private val DAY_MS = TimeUnit.DAYS.toMillis(1)
