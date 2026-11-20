package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvInset
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPersonPickRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.ui.common.MAX_TEXT_CHARS
import com.dantech.splitvibe.ui.common.NumberField
import java.math.BigDecimal

/** Someone on the bill as the sheets show them: their id and the name to call them by. */
internal data class SheetPerson(val id: String, val name: String)

/**
 * Adds someone to the bill: a name typed in, one of the people split with before, or someone
 * picked from the phone's contacts. Only the name is kept.
 */
@Composable
internal fun AddPersonSheet(
    people: List<Person>,
    onAdd: (name: String, personId: String?) -> Unit,
    onPickContact: () -> Unit,
    /** The phone has no app to pick a contact with, which the sheet then says. */
    contactsMissing: Boolean,
    /** Stops suggesting someone, for example a name that was mistyped. */
    onForget: (Person) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val suggestions = remember(name, people) { NameMatcher.suggest(name, people) }
    val add = { if (name.isNotBlank()) onAdd(name.trim(), NameMatcher.find(name, people)?.id) }

    SvBottomSheet(
        title = stringResource(R.string.split_add_someone),
        supportingText = stringResource(R.string.split_add_someone_hint),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.split_from_contacts), onClick = onPickContact, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.common_add),
                onClick = add,
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank(),
            )
        },
    ) {
        SvTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SvSpacing.screen),
            maxLength = MAX_TEXT_CHARS,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        if (contactsMissing) ErrorText(stringResource(R.string.split_no_contacts))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        ) {
            items(suggestions, key = { it.id }) { person ->
                Suggestion(person = person, onAdd = { onAdd(person.name, person.id) }, onForget = { onForget(person) })
            }
        }
    }
}

/** Someone split with before whose name matches what was typed: added with a tap, or no longer suggested. */
@Composable
private fun Suggestion(person: Person, onAdd: () -> Unit, onForget: () -> Unit) {
    // One element for a screen reader, which reads the name, with the two buttons in it.
    SvInset(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            SvAvatar(person.name)
            Text(person.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
            SvQuietButton(stringResource(R.string.common_add), onClick = onAdd)
            IconButton(onClick = onForget) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.split_stop_suggesting, person.name))
            }
        }
    }
}

/** Renames someone on the bill, or takes them off it. */
@Composable
internal fun PersonSheet(person: SheetPerson, onRename: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(person.id) { mutableStateOf(person.name) }
    SvBottomSheet(
        title = person.name,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.split_remove_from_bill), color = MaterialTheme.colorScheme.error)
            }
            SvPrimaryButton(
                text = stringResource(R.string.common_save),
                onClick = { onRename(name) },
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank(),
            )
        },
    ) {
        SvTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.common_name),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SvSpacing.screen),
            maxLength = MAX_TEXT_CHARS,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onRename(name) }),
        )
    }
}

/**
 * Chooses who had one item and how they divide it: equally between the people ticked, or by
 * shares, percentages or exact amounts, where someone given nothing has no part of it.
 */
@Composable
internal fun ItemSplitSheet(
    item: BillItem,
    currency: BillCurrency,
    people: List<SheetPerson>,
    division: Division?,
    onConfirm: (Division?) -> Unit,
    onDismiss: () -> Unit,
) {
    val everyone = remember(people) { people.map { it.id } }
    var mode by remember { mutableStateOf(modeOf(division)) }
    var sharing by remember { mutableStateOf(sharersOf(division, everyone)) }
    var values by remember {
        mutableStateOf<Map<String, Long>>(
            if (division == null || division is Division.Equally) emptyMap() else everyone.associateWith { division.valueOf(it) },
        )
    }

    // Whoever has a part now, which is who a new way of dividing starts from.
    fun inNow(): List<String> = everyone.filter { if (mode == SplitMode.EQUAL) it in sharing else (values[it] ?: 0L) > 0 }

    fun startOver(newMode: SplitMode, among: List<String>) {
        val start = among.ifEmpty { everyone }
        mode = newMode
        sharing = start.toSet()
        values = evenValues(newMode, start, item.amountMinor)
    }

    val given = values.values.sum()
    val target = targetOf(mode, item.amountMinor)
    val complete = when (mode) {
        SplitMode.EQUAL -> sharing.isNotEmpty()
        else -> values.values.all { it >= 0 } && (if (target == null) given > 0 else given == target)
    }
    val result: Division? = when (mode) {
        SplitMode.BY_SHARES -> Division.ByShares(values.filterValues { it != 0L })
        SplitMode.BY_PERCENT -> Division.ByPercent(values.filterValues { it != 0L })
        SplitMode.BY_AMOUNT -> Division.ByAmount(values.filterValues { it != 0L })
        else -> if (sharing.containsAll(everyone)) null else Division.Equally(among = sharing)
    }

    SvBottomSheet(
        title = item.name.ifBlank { stringResource(R.string.common_item) },
        supportingText = stringResource(R.string.split_item_sheet_hint, currency.format(item.amountMinor)),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.split_everyone), onClick = { startOver(mode, everyone) }, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.common_done),
                onClick = { onConfirm(result) },
                modifier = Modifier.weight(1f),
                enabled = complete,
            )
        },
    ) {
        SvChoiceChips(
            options = ITEM_MODES.map { stringResource(it.second) },
            selected = ITEM_MODES.indexOfFirst { it.first == mode },
            onSelect = { index -> ITEM_MODES[index].first.takeIf { it != mode }?.let { startOver(it, inNow()) } },
            modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.sm),
        )
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(people, key = { it.id }) { person ->
                if (mode == SplitMode.EQUAL) {
                    SvPersonPickRow(
                        name = person.name,
                        checked = person.id in sharing,
                        onCheckedChange = { checked -> sharing = if (checked) sharing + person.id else sharing - person.id },
                    )
                } else {
                    DivisionValueRow(
                        name = person.name,
                        mode = mode,
                        value = values[person.id] ?: 0L,
                        currency = currency,
                        onValue = { values = values + (person.id to it) },
                    )
                }
            }
            item(key = "given") {
                when {
                    mode != SplitMode.EQUAL -> DivisionTotalRow(
                        mode = mode,
                        given = given,
                        amountMinor = item.amountMinor,
                        currency = currency,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs),
                    )

                    sharing.isEmpty() -> ErrorText(stringResource(R.string.split_tick_someone))
                }
            }
        }
    }
}

/**
 * Who paid the bill: one person all of it, or several people each an amount, which must add up to
 * the bill total.
 */
@Composable
internal fun PaymentsSheet(
    people: List<SheetPerson>,
    payments: List<Payment>,
    totalMinor: Long,
    currency: BillCurrency,
    onConfirm: (List<Payment>) -> Unit,
    onDismiss: () -> Unit,
) {
    var several by remember { mutableStateOf(payments.size > 1) }
    var payer by remember { mutableStateOf(payments.singleOrNull()?.participantId ?: people.firstOrNull()?.id) }
    var amounts by remember { mutableStateOf(payments.associate { it.participantId to it.amountMinor }) }
    val given = amounts.values.sum()
    val chosen = if (several) {
        people.mapNotNull { person -> amounts[person.id]?.takeIf { it != 0L }?.let { Payment(person.id, it) } }
    } else {
        listOfNotNull(payer?.let { Payment(it, totalMinor) })
    }
    val complete = if (several) chosen.isNotEmpty() && chosen.all { it.amountMinor > 0 } && given == totalMinor else payer != null

    SvBottomSheet(
        title = stringResource(R.string.split_who_paid),
        supportingText = stringResource(R.string.split_bill_total_is, currency.format(totalMinor)),
        onDismiss = onDismiss,
        actions = {
            SvPrimaryButton(
                text = stringResource(R.string.common_done),
                onClick = { onConfirm(chosen) },
                modifier = Modifier.weight(1f),
                enabled = complete,
            )
        },
    ) {
        SvChoiceGroup(
            options = listOf(stringResource(R.string.split_one_payer), stringResource(R.string.split_several_payers)),
            selected = if (several) 1 else 0,
            onSelect = { index ->
                several = index == 1
                // Several payers start from the one who paid it all.
                if (several && amounts.size < 2) amounts = listOfNotNull(payer).associateWith { totalMinor }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.sm),
        )
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(people, key = { it.id }) { person ->
                if (several) {
                    DivisionValueRow(
                        name = person.name,
                        mode = SplitMode.BY_AMOUNT,
                        value = amounts[person.id] ?: 0L,
                        currency = currency,
                        onValue = { amounts = amounts + (person.id to it) },
                    )
                } else {
                    SvPersonChoiceRow(name = person.name, selected = person.id == payer, onSelect = { payer = person.id })
                }
            }
            if (several) {
                item(key = "given") {
                    DivisionTotalRow(
                        mode = SplitMode.BY_AMOUNT,
                        given = given,
                        amountMinor = totalMinor,
                        currency = currency,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs),
                    )
                }
            }
        }
    }
}

/**
 * The rate that converts the bill into the user's currency: the day's rate the app found, or one
 * the user types in, such as the one their card was charged at.
 */
@Composable
internal fun ExchangeRateSheet(
    from: String,
    to: String,
    rate: ExchangeRate?,
    onSave: (BigDecimal) -> Unit,
    onUseDayRate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf(rate?.rate) }
    SvBottomSheet(
        title = stringResource(R.string.split_exchange_rate),
        supportingText = stringResource(R.string.split_exchange_rate_hint, from, to),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.split_use_day_rate), onClick = onUseDayRate, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.common_save),
                onClick = { typed?.let(onSave) },
                modifier = Modifier.weight(1f),
                enabled = typed != null,
            )
        },
    ) {
        NumberField(
            label = stringResource(R.string.split_rate_label, from, to),
            // Without grouping, so it reads back as the number it is.
            text = rate?.rate?.let { ExchangeRate.rateInputText(it) }.orEmpty(),
            parse = ::positiveRate,
            onValue = { typed = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SvSpacing.screen),
            suffix = to,
            allowEmpty = true,
            onEmpty = { typed = null },
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

/** A rate typed with either decimal mark and no grouping, such as "25350" or "0,92": more than zero, without an exponent. */
internal fun positiveRate(text: String): BigDecimal? {
    if (!RATE_TEXT.matches(text.trim())) return null
    // Without trailing zeros, so "25350.0" and "25350" read as the same rate.
    return text.trim().replace(',', '.').toBigDecimal().stripTrailingZeros().takeIf { it.signum() > 0 }
}

/** Up to ten digits before the decimal mark and twelve after it: enough for any rate between two currencies. */
private val RATE_TEXT = Regex("""\d{1,10}([.,]\d{1,12})?""")

private val ITEM_MODES = listOf(
    SplitMode.EQUAL to R.string.split_mode_equal,
    SplitMode.BY_SHARES to R.string.split_mode_by_shares,
    SplitMode.BY_PERCENT to R.string.split_mode_by_percent,
    SplitMode.BY_AMOUNT to R.string.split_mode_by_amount,
)

private fun modeOf(division: Division?): SplitMode = when (division) {
    is Division.ByShares -> SplitMode.BY_SHARES
    is Division.ByPercent -> SplitMode.BY_PERCENT
    is Division.ByAmount -> SplitMode.BY_AMOUNT
    is Division.Equally, null -> SplitMode.EQUAL
}

/** Who shares an item divided equally, as the calculator counts them: nobody left means everyone. */
private fun sharersOf(division: Division?, everyone: List<String>): Set<String> {
    val equally = division as? Division.Equally ?: return everyone.toSet()
    val chosen = if (equally.among.isEmpty()) everyone else everyone.filter { it in equally.among }
    return chosen.filter { it !in equally.except }.ifEmpty { everyone }.toSet()
}

/** The values an even division of [amountMinor] between [people] starts from, in [mode]'s units. */
private fun evenValues(mode: SplitMode, people: List<String>, amountMinor: Long): Map<String, Long> = when (mode) {
    SplitMode.BY_SHARES -> people.associateWith { 1L }
    SplitMode.BY_PERCENT -> people.zip(SplitCalculator.divideEqually(Division.ByPercent.FULL, people.size)).toMap()
    SplitMode.BY_AMOUNT -> people.zip(SplitCalculator.divideEqually(amountMinor, people.size)).toMap()
    SplitMode.EQUAL, SplitMode.BY_ITEM -> emptyMap()
}
