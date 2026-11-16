package com.dantech.splitvibe.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.Recurrence
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * Makes a bill come back every week or every month: what to call it, how often, and the day of its
 * first draft, which is today at the earliest. Until the user picks a day, the first draft comes one
 * week or one month after the bill's own date, or after today for a bill without one.
 *
 * @param name what the bill is called now, such as its merchant.
 * @param date the bill's date as written on it, which may be no day at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatBillSheet(name: String, date: String?, today: LocalDate, onRepeat: (String, RecurrenceSchedule) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf(name) }
    var frequency by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    // Kept as an epoch day, which saves as a plain number; null until the user picks a day.
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val start = pickedDay?.let(LocalDate::ofEpochDay) ?: firstDraftDay(date, frequency, today)
    val schedule = RecurrenceSchedule(frequency, start)

    SvBottomSheet(
        title = "Repeat this bill",
        supportingText = "A draft of it is made on each day it comes back, for you to check and finish.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onRepeat(typed.trim(), schedule) }) { Text("Repeat") }
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(RecurringBillRepository.MAX_NAME) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                placeholder = { Text("Rent") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            SvChoiceGroup(
                options = listOf("Weekly", "Monthly"),
                selected = if (frequency == Frequency.WEEKLY) 0 else 1,
                onSelect = { frequency = if (it == 0) Frequency.WEEKLY else Frequency.MONTHLY },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("First draft ${dayText(start)}", style = MaterialTheme.typography.bodyLarge)
                    Text(describe(schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { picking = true }) { Text("Change") }
            }
        }
    }

    if (picking) {
        val firstAllowed = today.toEpochDay() * DAY_MS
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = start.toEpochDay() * DAY_MS,
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
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = picker)
        }
    }
}

/** The day of the first draft until the user picks one: one period after the bill's date, and today at the earliest. */
internal fun firstDraftDay(date: String?, frequency: Frequency, today: LocalDate): LocalDate {
    val billDay = date?.let {
        try {
            LocalDate.parse(it.trim())
        } catch (e: DateTimeParseException) {
            null
        }
    }
    // The bill itself is the occurrence it starts from, so the first draft is the one after it.
    val from = billDay ?: today
    return Recurrence.next(RecurrenceSchedule(frequency, from), maxOf(from, today.minusDays(1)))
}

private val DAY_MS = TimeUnit.DAYS.toMillis(1)
