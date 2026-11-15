package com.dantech.splitvibe.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.ExchangeRate

/**
 * Adds the bill on screen to a group, or sends it to its group again after a change: which group,
 * which member each person on the bill is, and the rate into the group's currency.
 */
@Composable
fun AddToGroupSheet(
    saved: SavedBill,
    viewModel: AddToGroupViewModel,
    nameOf: (String) -> String,
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
            group == null -> "Add to a group"
            again -> "Update in ${group.group.name}"
            else -> "Add to ${group.group.name}"
        },
        supportingText = "Everyone in the group sees what was bought and who had what. The photos stay on this phone.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            if (group != null) {
                Button(onClick = { viewModel.submit(saved) }, enabled = amounts != null && !state.working) {
                    Text(if (again) "Update" else "Add the bill")
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SvSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            state.message?.let { SvMessageCard(text = it, isError = true, action = "OK", onAction = viewModel::dismissMessage) }
            if (state.working && group == null) SvLoadingIndicator()

            if (group == null) {
                val list = choices.orEmpty()
                if (choices != null && list.isEmpty()) {
                    Text(
                        "You are not in a group yet. Make one in Groups, from the home screen.",
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

            Text("Who is who in the group", style = MaterialTheme.typography.titleSmall)
            saved.plan.participants.forEach { participant ->
                Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                    Text(nameOf(participant.id), style = MaterialTheme.typography.bodyLarge)
                    val members = group.members
                    SvChoiceChips(
                        options = members.map { group.nameOf(it.id) },
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
                    "Comes to ${group.currency.format(it.totalMinor)} in the group.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } ?: Text("Match everyone on the bill to a member.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RateField(saved: SavedBill, rate: ExchangeRate?, groupCode: String, onRate: (java.math.BigDecimal) -> Unit) {
    // Shows the rate in use until the user types one of their own.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    val from = saved.bill.currency.code
    OutlinedTextField(
        value = typed ?: rate?.rate?.stripTrailingZeros()?.toPlainString().orEmpty(),
        onValueChange = { text ->
            typed = text.take(RATE_LENGTH)
            text.trim().replace(',', '.').toBigDecimalOrNull()?.let(onRate)
        },
        label = { Text("$groupCode for 1 $from") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = {
            Text(
                when {
                    rate == null -> "No rate for today yet. Type the one you were charged at."
                    rate.isByHand -> "The rate you typed."
                    else -> "${rate.source}'s rate for ${rate.date}. Change it to the one you were charged at."
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val RATE_LENGTH = 24
