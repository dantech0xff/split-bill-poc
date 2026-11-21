package com.dantech.splitvibe.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.inputNumberText
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.resolve

/**
 * Adds the bill on screen to a group, or sends it to its group again after a change: which group,
 * which member each person on the bill is, and the rate into the group's currency.
 */
@Composable
fun AddToGroupSheet(
    saved: SavedBill,
    viewModel: AddToGroupViewModel,
    nameOf: (String) -> UiText,
    onLinked: (GroupLink) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(saved.id) { viewModel.start(saved) }
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { state.done?.let(onLinked) }

    val group = state.group
    val amounts = viewModel.amounts(saved)
    val again = saved.group != null && saved.group.groupId == group?.group?.id
    SvBottomSheet(
        title = when {
            group == null -> stringResource(R.string.split_add_to_group)
            again -> stringResource(R.string.split_update_in_group, group.group.name)
            else -> stringResource(R.string.groups_add_to, group.group.name)
        },
        supportingText = stringResource(R.string.groups_add_hint),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.common_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            if (group != null) {
                SvPrimaryButton(
                    text = stringResource(if (again) R.string.groups_update else R.string.groups_add_the_bill),
                    onClick = { viewModel.submit(saved) },
                    modifier = Modifier.weight(1f),
                    enabled = amounts != null && !state.working,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SvSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            state.message?.let {
                SvMessageCard(
                    text = it.resolve(),
                    isError = true,
                    action = stringResource(R.string.common_ok),
                    onAction = viewModel::dismissMessage,
                )
            }
            when (state.conflict) {
                GroupConflict.DELETED -> SvMessageCard(
                    text = stringResource(R.string.groups_bill_deleted),
                    isError = true,
                    action = stringResource(R.string.groups_add_again),
                    onAction = { viewModel.addAgain(saved) },
                )

                GroupConflict.CHANGED -> SvMessageCard(
                    text = stringResource(R.string.groups_bill_changed),
                    isError = true,
                    action = stringResource(R.string.groups_send_mine),
                    onAction = { viewModel.replaceTheirs(saved) },
                )

                null -> Unit
            }
            if (state.askedPersonally) {
                SvMessageCard(
                    text = stringResource(R.string.groups_asked_personally),
                    isError = false,
                )
            }
            if (state.working && group == null) SvLoadingIndicator()

            if (group == null) {
                val list = choices.orEmpty()
                if (choices != null && list.isEmpty()) {
                    Text(
                        stringResource(R.string.groups_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                list.forEach { summary ->
                    SvPersonChoiceRow(
                        name = summary.name,
                        selected = false,
                        onSelect = { viewModel.choose(saved, summary.id) },
                        detail = summary.currency.code,
                    )
                }
                return@Column
            }

            Text(stringResource(R.string.groups_who_is_who), style = MaterialTheme.typography.titleSmall)
            saved.plan.participants.forEach { participant ->
                Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                    Text(nameOf(participant.id).resolve(), style = MaterialTheme.typography.bodyLarge)
                    val members = group.members
                    SvChoiceChips(
                        options = members.map { group.nameOf(it.id).resolve() },
                        selected = members.indexOfFirst { it.id == state.memberOf[participant.id] },
                        onSelect = { viewModel.match(participant.id, members[it].id) },
                    )
                }
            }

            if (saved.bill.currency.code != group.currency.code) {
                RateField(saved = saved, rate = state.rate, groupCode = group.currency.code, onRate = { viewModel.setRate(it, saved) })
            }

            amounts?.let {
                Text(
                    stringResource(R.string.groups_comes_to, group.currency.format(it.totalMinor)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: Text(stringResource(R.string.groups_match_everyone), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RateField(saved: SavedBill, rate: ExchangeRate?, groupCode: String, onRate: (String) -> Unit) {
    // Shows the rate in use until the user types one of their own.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    val from = saved.bill.currency.code
    SvTextField(
        value = typed ?: rate?.rate?.stripTrailingZeros()?.let { inputNumberText(it) }.orEmpty(),
        onValueChange = { text ->
            typed = text
            onRate(text)
        },
        label = stringResource(R.string.groups_rate_label, groupCode, from),
        modifier = Modifier.fillMaxWidth(),
        supportingText = when {
            rate == null -> stringResource(R.string.groups_no_rate)
            rate.isByHand -> stringResource(R.string.groups_rate_typed)
            else -> stringResource(R.string.groups_rate_of_day, rate.source, rate.date.toString())
        },
        maxLength = RATE_LENGTH,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private const val RATE_LENGTH = 24
