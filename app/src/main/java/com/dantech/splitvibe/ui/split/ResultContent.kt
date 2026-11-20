package com.dantech.splitvibe.ui.split

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.dayOf
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAiMark
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvAvatarStack
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTallyLine
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.motion.SvAmount
import com.dantech.splitvibe.designsystem.motion.rememberSvEntryState
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.designsystem.motion.staggeredEntry
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.numberText
import com.dantech.splitvibe.text.dateText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.NUMBER_FIELD_WIDTH
import com.dantech.splitvibe.ui.groups.AddToGroupSheet
import java.math.BigDecimal
import org.koin.androidx.compose.koinViewModel

/** What the bill and people step asks the flow to do. */
class ResultActions(
    val onBack: () -> Unit,
    val onEditBill: () -> Unit,
    val onAddPerson: (name: String, personId: String?) -> Unit,
    val onContactPicked: (Uri) -> Unit,
    val onForgetPerson: (Person) -> Unit,
    val onRenamePerson: (id: String, name: String) -> Unit,
    val onRemovePerson: (id: String) -> Unit,
    val onToggleMe: () -> Unit,
    val onSetMode: (SplitMode) -> Unit,
    val onSetWholeValue: (id: String, value: Long) -> Unit,
    val onSetItemDivision: (itemId: String, division: Division?) -> Unit,
    val onSetPayments: (List<Payment>) -> Unit,
    val onSetExchangeRate: (BigDecimal) -> Unit,
    val onUseDayRate: () -> Unit,
    val onSendRequests: () -> Unit,
    val onFinish: () -> Unit,
    /** The bill was added to a group, or sent to it again. */
    val onLinkedToGroup: (GroupLink) -> Unit = {},
    /** Opens the bill's link, where everyone at the table picks what they had. */
    val onPickTogether: () -> Unit = {},
)

/**
 * Second step of a split: the bill's total and what the AI understood of it, what each person pays
 * with everyone on the bill, the field that changes the split by a sentence the user types or says,
 * how it is divided, its items and charges, and who paid. Every change is saved as it is made, and
 * what the [assistant] proposes changes nothing until the user has checked it.
 */
@Composable
fun ResultContent(
    result: SplitResultState,
    people: List<Person>,
    actions: ResultActions,
    assistant: AssistantUiState,
    assistantActions: AssistantActions,
) {
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    var changingPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var splittingItem by rememberSaveable { mutableStateOf<String?>(null) }
    var changingPayments by rememberSaveable { mutableStateOf(false) }
    var changingRate by rememberSaveable { mutableStateOf(false) }
    var contactsMissing by rememberSaveable { mutableStateOf(false) }
    var addingToGroup by rememberSaveable { mutableStateOf(false) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contact ->
        if (contact != null) actions.onContactPicked(contact)
    }

    val bill = result.bill
    val plan = result.plan
    val mode = plan.mode
    val byItem = mode == SplitMode.BY_ITEM
    val sharing = plan.sharingIds
    val entry = rememberSvEntryState()
    val differenceLabel = stringResource(R.string.split_difference)
    val list = rememberLazyListState()
    BringProposalIntoView(assistant.proposal, list)

    Scaffold(
        // The assistant's field is typed into, so the page ends above the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            SvTopBar(
                title = bill.merchant ?: stringResource(R.string.common_bill),
                backLabel = stringResource(R.string.common_back),
                onBack = actions.onBack,
                // A shop's name read from a receipt can be long, and the approved design keeps it to one line.
                oneLineTitle = true,
                action = {
                    IconButton(onClick = actions.onEditBill) {
                        Icon(SvActionIcons.Pencil, contentDescription = stringResource(R.string.split_edit_bill))
                    }
                },
            )
        },
        bottomBar = {
            FinishBar(
                result = result,
                onSendRequests = actions.onSendRequests,
                onFinish = actions.onFinish,
                onAddToGroup = { addingToGroup = true },
                onPickTogether = actions.onPickTogether,
            )
        },
    ) { padding ->
        // Every row has a key of its own and moves with rowMotion, so when the split changes, a row
        // that comes or goes fades and the rows below it slide to their new places.
        // The list ends above the bottom bar, so nothing scrolls under it and the keyboard never pushes a field behind it.
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "total") {
                TotalHero(
                    result = result,
                    onChangeRate = { changingRate = true },
                    modifier = rowMotion()
                        .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm)
                        .staggeredEntry(entry, index = 0),
                )
            }
            // What the AI proposes stands right below the total, above what each person pays, as the
            // approved design has it; what an applied change did takes its place there.
            assistant.proposal?.let { proposal ->
                item(key = "proposal") {
                    ProposalCard(
                        proposal = proposal,
                        actions = assistantActions,
                        modifier = rowMotion().padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
                    )
                }
            }
            assistant.applied?.let { applied ->
                item(key = "applied") {
                    SvMessageCard(
                        text = applied.resolve(),
                        isError = false,
                        action = stringResource(R.string.common_undo),
                        onAction = assistantActions.onUndo,
                        modifier = rowMotion().padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs),
                    )
                }
            }
            if (result.summary.isNotBlank()) {
                item(key = "understood") {
                    UnderstoodCard(
                        summary = result.summary,
                        modifier = rowMotion()
                            .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs)
                            .staggeredEntry(entry, index = 1),
                    )
                }
            }

            // Keyed by its text, so a notice that moves up when another one is settled slides there.
            // The same sentence twice would say nothing new.
            items((plan.openNotices() + bill.warnings).distinct(), key = { note -> "notice $note" }) { note ->
                SvMessageCard(
                    text = note,
                    isError = false,
                    modifier = rowMotion().padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs),
                )
            }
            bill.differenceNote()?.let { note ->
                item(key = "difference") {
                    SvMessageCard(
                        text = note,
                        isError = false,
                        action = stringResource(R.string.split_edit_bill),
                        onAction = actions.onEditBill,
                        modifier = rowMotion().padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs),
                    )
                }
            }

            item(key = "shares header") {
                SvSectionHeader(
                    stringResource(R.string.split_each_person_pays),
                    modifier = rowMotion(),
                    hint = stringResource(R.string.split_who_hint),
                )
            }
            // Everyone on the bill is a row of what they pay, and "Add someone" ends the group.
            val rows = plan.participants.size + 1
            itemsIndexed(plan.participants, key = { _, participant -> "share ${participant.id}" }) { index, participant ->
                PersonRow(
                    result = result,
                    participant = participant,
                    position = SvRowPosition.of(index, rows),
                    onToggleMe = actions.onToggleMe,
                    onChange = { changingPerson = participant.id },
                    onValue = { value -> actions.onSetWholeValue(participant.id, value) },
                    modifier = rowMotion().staggeredEntry(entry, index = index + 2),
                )
            }
            item(key = "add someone") {
                SvListRow(position = SvRowPosition.of(rows - 1, rows), modifier = rowMotion()) {
                    SvQuietButton(
                        stringResource(R.string.split_add_someone),
                        onClick = { addingPerson = true },
                        modifier = Modifier.weight(1f),
                        icon = SvActionIcons.Plus,
                    )
                }
            }
            if (mode.hasValuePerPerson) {
                item(key = "given") {
                    val division = plan.setup.whole
                    val given = sharing.sumOf { division?.valueOf(it) ?: 0L }
                    DivisionTotalRow(
                        mode = mode,
                        given = given,
                        amountMinor = bill.totalMinor,
                        currency = bill.currency,
                        modifier = rowMotion().padding(start = LINE_INSET, end = LINE_INSET, top = SvSpacing.sm),
                    )
                }
            }
            if (result.problems.isEmpty()) {
                item(key = "shares total") {
                    // The proof under the shares: together they are exactly the bill total.
                    SvTallyLine(
                        text = stringResource(R.string.split_adds_up),
                        balanced = true,
                        modifier = rowMotion().padding(start = LINE_INSET, end = LINE_INSET, top = SvSpacing.sm),
                        amount = bill.format(result.split.totalMinor),
                    )
                }
            }
            // Keyed by place: two lines of the same name can have the same problem.
            itemsIndexed(result.problems, key = { index, _ -> "problem $index" }) { _, problem ->
                SvMessageCard(
                    text = problem,
                    isError = true,
                    modifier = rowMotion().padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs),
                )
            }

            item(key = "assistant") {
                AssistantSection(
                    state = assistant,
                    actions = assistantActions,
                    modifier = rowMotion().padding(
                        start = SvSpacing.screen,
                        end = SvSpacing.screen,
                        top = SvSpacing.xl,
                        bottom = SvSpacing.xs,
                    ),
                )
            }

            item(key = "mode") {
                Column(modifier = rowMotion()) {
                    SvSectionHeader(stringResource(R.string.split_how_is_it_split))
                    // One row that scrolls sideways, running to the edges of the screen.
                    SvChoiceChips(
                        options = MODES.map { stringResource(it.second) },
                        selected = MODES.indexOfFirst { it.first == mode },
                        onSelect = { index -> actions.onSetMode(MODES[index].first) },
                        modifier = Modifier.fillMaxWidth(),
                        singleRow = true,
                        contentPadding = PaddingValues(horizontal = SvSpacing.screen),
                    )
                    if (bill.items.isNotEmpty()) {
                        Text(
                            stringResource(if (byItem) R.string.split_items_hint_by_item else R.string.split_items_hint),
                            modifier = Modifier.padding(
                                start = SvSpacing.screen,
                                end = SvSpacing.screen,
                                top = SvSpacing.md,
                                bottom = SvSpacing.sm,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(bill.items, key = { _, item -> "item ${item.id}" }) { index, item ->
                ItemRow(
                    bill = bill,
                    item = item,
                    sharers = if (byItem) {
                        Sharers(
                            describe(plan.setup.items[item.id], result),
                            sharerNames(plan.setup.items[item.id], result),
                        )
                    } else {
                        null
                    },
                    position = SvRowPosition.of(index, bill.items.size),
                    onClick = if (byItem) ({ splittingItem = item.id }) else null,
                    modifier = rowMotion(),
                )
            }

            val chargeRows = bill.charges + listOfNotNull(differenceCharge(bill, differenceLabel))
            if (chargeRows.isNotEmpty()) {
                item(key = "charges header") {
                    SvSectionHeader(
                        stringResource(R.string.split_charges),
                        modifier = rowMotion(),
                        hint = stringResource(if (byItem) R.string.split_charges_hint_by_item else R.string.split_charges_hint),
                    )
                }
                itemsIndexed(chargeRows, key = { _, charge -> "charge ${charge.id}" }) { index, charge ->
                    ChargeRow(bill, charge, SvRowPosition.of(index, chargeRows.size), modifier = rowMotion())
                }
            }

            item(key = "paid header") { SvSectionHeader(stringResource(R.string.split_paid_by), modifier = rowMotion()) }
            item(key = "paid") {
                PaidRow(result = result, onClick = { changingPayments = true }, modifier = rowMotion())
            }
            item(key = "end") { Spacer(Modifier.height(SvSpacing.lg)) }
        }
    }

    AssistantVoicePrompts(voice = assistant.voice, actions = assistantActions)

    val sheetPeople = plan.participants.map { SheetPerson(it.id, result.nameOf(it.id).resolve()) }

    if (addingPerson) {
        AddPersonSheet(
            people = people,
            onAdd = { name, personId ->
                actions.onAddPerson(name, personId)
                addingPerson = false
            },
            onPickContact = {
                try {
                    pickContact.launch(null)
                    contactsMissing = false
                    addingPerson = false
                } catch (e: ActivityNotFoundException) {
                    contactsMissing = true
                }
            },
            contactsMissing = contactsMissing,
            onForget = actions.onForgetPerson,
            onDismiss = { addingPerson = false },
        )
    }

    changingPerson?.let { id ->
        val person = sheetPeople.firstOrNull { it.id == id }
        if (person == null) {
            changingPerson = null
        } else {
            PersonSheet(
                person = person,
                onRename = { name ->
                    actions.onRenamePerson(id, name)
                    changingPerson = null
                },
                onRemove = {
                    actions.onRemovePerson(id)
                    changingPerson = null
                },
                onDismiss = { changingPerson = null },
            )
        }
    }

    splittingItem?.let { itemId ->
        val item = bill.items.firstOrNull { it.id == itemId }
        if (item == null) {
            splittingItem = null
        } else {
            ItemSplitSheet(
                item = item,
                currency = bill.currency,
                people = sheetPeople.filter { it.id in sharing },
                division = plan.setup.items[itemId],
                onConfirm = { division ->
                    actions.onSetItemDivision(itemId, division)
                    splittingItem = null
                },
                onDismiss = { splittingItem = null },
            )
        }
    }

    if (changingPayments) {
        PaymentsSheet(
            people = sheetPeople,
            payments = plan.payments,
            totalMinor = bill.totalMinor,
            currency = bill.currency,
            onConfirm = { payments ->
                actions.onSetPayments(payments)
                changingPayments = false
            },
            onDismiss = { changingPayments = false },
        )
    }

    if (addingToGroup) {
        AddToGroupSheet(
            saved = result.saved,
            viewModel = koinViewModel(key = "add-to-group-${result.saved.id}"),
            nameOf = result::nameOf,
            onLinked = { link ->
                addingToGroup = false
                actions.onLinkedToGroup(link)
            },
            onDismiss = { addingToGroup = false },
        )
    }

    if (changingRate && result.needsRate) {
        ExchangeRateSheet(
            from = bill.currency.code,
            to = result.localCurrency,
            rate = result.exchangeRate,
            onSave = { rate ->
                actions.onSetExchangeRate(rate)
                changingRate = false
            },
            onUseDayRate = {
                actions.onUseDayRate()
                changingRate = false
            },
            onDismiss = { changingRate = false },
        )
    }
}

/**
 * The block the screen is about: the bill's total, the day of the bill, and for a bill in another
 * currency than the user's, the total in theirs with the rate it is converted at.
 */
@Composable
private fun TotalHero(result: SplitResultState, onChangeRate: () -> Unit, modifier: Modifier = Modifier) {
    val bill = result.bill
    val parts = bill.currency.formatParts(bill.totalMinor)
    SvHero(
        modifier = modifier,
        label = { Text(stringResource(R.string.split_bill_total)) },
        amount = {
            SvHeroAmount(prefix = parts.prefix, number = parts.number, suffix = parts.suffix, spokenText = bill.format(bill.totalMinor))
        },
    ) {
        // The day of the bill and how many people are on it, side by side as the approved design has them.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            billDayText(bill.date)?.let { day -> SvTag(day) }
            val people = result.plan.participants.size
            SvTag(plural(R.plurals.split_people_count, people).resolve())
        }
        if (result.needsRate) {
            val converted = result.converted(bill.totalMinor)
            val rate = result.exchangeRate
            if (converted != null && rate != null) {
                Text(stringResource(R.string.split_about, converted), style = MaterialTheme.typography.titleMedium)
                Text(rateNote(rate), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(stringResource(R.string.split_add_rate_hint, result.localCurrency), style = MaterialTheme.typography.bodySmall)
            }
            SvTextAction(stringResource(if (rate == null) R.string.split_add_rate else R.string.split_change_rate), onClick = onChangeRate)
        }
    }
}

/**
 * Brings [proposal] into view in [list] when one comes in, so that the person who asked for it in the
 * field further down sees it: at once with animations off, otherwise with the list's own scroll. The
 * field lets go of the focus, and the keyboard closes, since a field with the focus keeps itself in
 * view and would take the list back down. What the person ticks or unticks in the proposal moves
 * nothing, and nor does turning the phone.
 */
@Composable
private fun BringProposalIntoView(proposal: ProposalUi?, list: LazyListState) {
    val reducedMotion = LocalReducedMotion.current
    val focus = LocalFocusManager.current
    // What the AI answered, without what the person kept or dropped of it since.
    val answer = proposal?.let { listOf(it.instruction, it.explanation) + it.edits.map(ProposedEdit::text) }?.hashCode()
    // The answer the list last brought into view, kept when the phone turns.
    var shown by rememberSaveable { mutableStateOf(answer) }
    LaunchedEffect(answer) {
        if (answer != null && answer != shown) {
            focus.clearFocus()
            if (reducedMotion) list.scrollToItem(PROPOSAL_INDEX) else list.animateScrollToItem(PROPOSAL_INDEX)
        }
        shown = answer
    }
}

/** Where the proposal stands in the result list: the row right below the total, which is the first. */
private const val PROPOSAL_INDEX = 1

/** What the AI said it understood of the bill when it read it. */
@Composable
private fun UnderstoodCard(summary: String, modifier: Modifier = Modifier) {
    SvCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                SvAiMark()
                Text(stringResource(R.string.split_what_was_understood), style = MaterialTheme.typography.bodySmallEmphasized)
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The day a bill names as the app writes a date with its year, such as "Nov 13, 2026", when it can be
 * read as a day, and as the bill printed it when it cannot; null for a bill without one.
 */
internal fun billDayText(date: String?): String? {
    val printed = date?.takeIf { it.isNotBlank() } ?: return null
    return dayOf(printed)?.let(::dateText) ?: printed
}

/** Where a rate comes from, such as "1 USD = 25,350 VND, Frankfurter rate of 2026-11-14". */
@Composable
private fun rateNote(rate: ExchangeRate): String {
    val date = rate.date
    return when {
        rate.isByHand -> stringResource(R.string.split_rate_by_hand, rate.describe())
        date != null -> stringResource(R.string.split_rate_of_day, rate.describe(), rate.source, date)
        else -> stringResource(R.string.split_rate_source, rate.describe(), rate.source)
    }
}

/** Who has an item, as its row shows them: in words, such as "Minh 60%, Lan 40%", and their names for their colours. */
private class Sharers(val text: String, val names: List<String>)

@Composable
private fun ItemRow(
    bill: Bill,
    item: BillItem,
    sharers: Sharers?,
    position: SvRowPosition,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val name = item.name.ifBlank { stringResource(R.string.common_item) }
    val unitPrice = item.unitPriceMinor
    val quantity = if (unitPrice != null && item.quantity.compareTo(BigDecimal.ONE) != 0) {
        stringResource(R.string.split_quantity_times, numberText(item.quantity), bill.format(unitPrice))
    } else {
        null
    }
    val detail = listOfNotNull(quantity, sharers?.text).joinToString(" · ").ifEmpty { null }
    val amount = bill.format(item.amountMinor)
    val content: @Composable RowScope.() -> Unit = {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMediumEmphasized)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val faces = sharers?.names.orEmpty()
        if (faces.isNotEmpty()) {
            Spacer(Modifier.width(SvSpacing.sm))
            SvAvatarStack(faces, size = SHARER_SIZE)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(amount, style = MaterialTheme.typography.amountSmall)
    }
    if (onClick == null) {
        SvListRow(position = position, modifier = modifier, content = content)
    } else {
        SvListRow(
            position = position,
            label = listOfNotNull(name, detail, amount).joinToString(", "),
            onClickLabel = stringResource(R.string.split_choose_who_had, name),
            onClick = onClick,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun ChargeRow(bill: Bill, charge: BillCharge, position: SvRowPosition, modifier: Modifier = Modifier) {
    SvListRow(position = position, modifier = modifier) {
        Text(charge.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(SvSpacing.md))
        Text(bill.format(charge.amountMinor), style = MaterialTheme.typography.amountSmall)
    }
}

/**
 * One person on the bill: what they pay and what of the bill it is made of, whether they paid it, and,
 * when the bill is divided by shares, percentages or amounts, their part to change beside it. A tap on
 * the user's own row switches whether they share the bill; a tap on anyone else's opens what can be
 * done with them, such as renaming.
 */
@Composable
private fun PersonRow(
    result: SplitResultState,
    participant: Participant,
    position: SvRowPosition,
    onToggleMe: () -> Unit,
    onChange: () -> Unit,
    onValue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bill = result.bill
    val plan = result.plan
    val mode = plan.mode
    val name = result.nameOf(participant.id).resolve()
    val sharing = participant.id in plan.sharingIds
    val share = result.split.shareOf(participant.id)
    val paid = plan.payments.filter { it.participantId == participant.id }.sumOf { it.amountMinor }
    // Who paid the whole bill carries a tag; a part paid is said with the rest.
    val paidAll = paid != 0L && paid == bill.totalMinor
    val hasInput = sharing && mode.hasValuePerPerson
    val detail = buildList {
        when {
            !sharing -> add(stringResource(R.string.split_not_sharing))
            share == null -> Unit
            hasInput -> add(stringResource(R.string.split_pays, bill.format(share.totalMinor)))
            else -> add(stringResource(R.string.split_items_and_charges, bill.format(share.itemsMinor), bill.format(share.chargesMinor)))
        }
        if (share != null && sharing) result.converted(share.totalMinor)?.let { add(stringResource(R.string.split_about_in_sentence, it)) }
        if (paid != 0L && !paidAll) add(stringResource(R.string.split_paid_in_sentence, bill.format(paid)))
    }.joinToString(" · ")
    val tag = stringResource(R.string.split_paid_the_bill).takeIf { paidAll }
    val amount = share?.totalMinor?.takeIf { !hasInput }
    val label = listOfNotNull(name, tag, detail.ifEmpty { null }, amount?.let(bill::format)).joinToString(", ")
    val part: (@Composable () -> Unit)? = if (hasInput) {
        {
            DivisionValueInput(
                name = name,
                mode = mode,
                value = plan.setup.whole?.valueOf(participant.id) ?: 0L,
                currency = bill.currency,
                onValue = onValue,
            )
        }
    } else {
        null
    }
    val content: @Composable RowScope.() -> Unit = {
        SvAvatar(name, size = PERSON_AVATAR_SIZE)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DETAIL_GAP)) {
            // The tag goes below the name when the two do not fit side by side, so a long tag never squeezes the name.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DETAIL_GAP),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyLargeEmphasized)
                if (tag != null) SvTag(tag, kind = SvTagKind.Accent)
            }
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (amount != null) {
            Spacer(Modifier.width(SvSpacing.md))
            SvAmount(amount, format = bill::format, style = MaterialTheme.typography.amountMedium)
        }
    }
    if (participant.isMe) {
        SvListRow(
            position = position,
            label = label,
            checked = sharing,
            onCheckedChange = { onToggleMe() },
            stateDescription = stringResource(if (sharing) R.string.split_you_share else R.string.split_you_do_not_share),
            modifier = modifier,
            trailing = part,
            trailingWidth = NUMBER_FIELD_WIDTH,
            content = content,
        )
    } else {
        SvListRow(
            position = position,
            label = label,
            onClickLabel = stringResource(R.string.split_change_person, name),
            onClick = onChange,
            modifier = modifier,
            trailing = part,
            trailingWidth = NUMBER_FIELD_WIDTH,
            content = content,
        )
    }
}

@Composable
private fun PaidRow(result: SplitResultState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bill = result.bill
    val payments = result.plan.payments
    val resources = LocalResources.current
    val names = payments.associate { it.participantId to result.nameOf(it.participantId).resolve() }
    val text = when {
        payments.isEmpty() -> stringResource(R.string.split_choose_who_paid)

        payments.size == 1 -> {
            val payment = payments.single()
            stringResource(R.string.split_one_paid, names.getValue(payment.participantId), bill.format(payment.amountMinor))
        }

        else -> payments.joinToString(", ") { payment ->
            resources.getString(R.string.split_person_part, names.getValue(payment.participantId), bill.format(payment.amountMinor))
        }
    }
    val changeLabel = stringResource(R.string.split_change_who_paid)
    SvListRow(position = SvRowPosition.Single, label = text, onClickLabel = changeLabel, onClick = onClick, modifier = modifier) {
        if (names.isNotEmpty()) {
            SvAvatarStack(payments.map { names.getValue(it.participantId) }, size = PAYER_SIZE)
            Spacer(Modifier.width(SvSpacing.md))
        }
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
        Icon(SvActionIcons.Pencil, contentDescription = null, modifier = Modifier.size(EDIT_ICON_SIZE))
    }
}

@Composable
private fun FinishBar(
    result: SplitResultState,
    onSendRequests: () -> Unit,
    onFinish: () -> Unit,
    onAddToGroup: () -> Unit,
    onPickTogether: () -> Unit,
) {
    val payments = result.plan.payments
    val iPaidItAll = payments.singleOrNull()?.participantId == ME_ID
    val others = result.split.shares.filter { it.personId != ME_ID && it.totalMinor > 0 }
    // A bill shared in a group is kept there; one already in a group is sent to it again after a change.
    val inGroup = result.saved.group
    val toGroup = SvMenuOption(
        inGroup?.let { stringResource(R.string.split_update_in_group, it.groupName) } ?: stringResource(R.string.split_add_to_group),
        onAddToGroup,
    )
    // Everyone at the table picks what they had on a page of their own; the bill's link opens with it.
    val pickLabel = stringResource(if (result.saved.share == null) R.string.split_pick_together else R.string.split_see_picks)
    val pickTogether = SvMenuOption(pickLabel, onPickTogether)
    val finishSplit = stringResource(R.string.split_finish)
    val moreWays = stringResource(R.string.split_more_ways)
    SvBottomBar {
        when {
            result.problems.isNotEmpty() -> SvPrimaryButton(finishSplit, onClick = {}, enabled = false)

            // In a group, people settle up across all its bills, so this bill is not asked for person by person.
            inGroup != null -> SvSplitButton(
                text = stringResource(R.string.split_update_in_group, inGroup.groupName),
                onClick = onAddToGroup,
                moreLabel = moreWays,
                options = listOf(SvMenuOption(finishSplit, onFinish), pickTogether),
            )

            iPaidItAll && others.isNotEmpty() -> {
                val asked = result.bill.format(others.sumOf { it.totalMinor })
                SvSplitButton(
                    text = plural(R.plurals.split_request_from, others.size, asked, others.size).resolve(),
                    onClick = onSendRequests,
                    moreLabel = moreWays,
                    options = listOf(toGroup, pickTogether, SvMenuOption(stringResource(R.string.split_finish_without_asking), onFinish)),
                    // The button turns into the mark that confirms the requests on the next step.
                    modifier = Modifier.sharedSurface(SEND_SURFACE, resize = true),
                )
            }

            else -> SvSplitButton(
                text = finishSplit,
                onClick = onFinish,
                moreLabel = moreWays,
                options = listOf(toGroup, pickTogether),
            )
        }
    }
}

/** Who has an item and how, as its row says it: "Everyone", "Minh, Lan" or "Minh 60%, Lan 40%". */
@Composable
private fun describe(division: Division?, result: SplitResultState): String {
    val bill = result.bill
    val sharing = result.plan.sharingIds
    val resources = LocalResources.current
    val nameOf = sharing.associateWith { result.nameOf(it).resolve() }
    val everyone = stringResource(R.string.split_everyone)
    fun names(ids: Collection<String>) = sharing.filter { it in ids }.joinToString(", ") { nameOf.getValue(it) }
    fun parts(values: Map<String, Long>, show: (Long) -> String) = sharing.filter { (values[it] ?: 0L) != 0L }
        .joinToString(", ") { resources.getString(R.string.split_person_part, nameOf.getValue(it), show(values.getValue(it))) }
    return when (division) {
        null -> everyone

        is Division.Equally -> {
            val among = if (division.among.isEmpty()) sharing else sharing.filter { it in division.among }
            val chosen = among.filter { it !in division.except }
            if (chosen.isEmpty() || chosen.size == sharing.size) everyone else names(chosen)
        }

        is Division.ByShares -> parts(division.shares) { plural(R.plurals.split_shares, it.toInt(), it).resolve(resources) }

        is Division.ByPercent -> parts(division.basisPoints) { resources.getString(R.string.split_percent, percentText(it)) }

        is Division.ByAmount -> parts(division.amounts) { bill.format(it) }
    }.ifEmpty { stringResource(R.string.split_nobody_yet) }
}

/** The names of who has an item under [division], in the order of the bill's people, for the colours of their avatars. */
@Composable
private fun sharerNames(division: Division?, result: SplitResultState): List<String> {
    val sharing = result.plan.sharingIds
    val ids = when (division) {
        null -> sharing

        is Division.Equally -> {
            val among = if (division.among.isEmpty()) sharing else sharing.filter { it in division.among }
            among.filter { it !in division.except }.ifEmpty { sharing }
        }

        is Division.ByShares, is Division.ByPercent, is Division.ByAmount -> sharing.filter { division.valueOf(it) != 0L }
    }
    return ids.map { result.nameOf(it).resolve() }
}

/** The gap between the printed total and the lines, as a line of its own so the shares can be followed. */
private fun differenceCharge(bill: Bill, label: String): BillCharge? = if (bill.differenceMinor == 0L || bill.items.isEmpty()) {
    null
} else {
    BillCharge("difference", label, ChargeKind.OTHER, bill.differenceMinor)
}

private val MODES = listOf(
    SplitMode.EQUAL to R.string.split_mode_equal,
    SplitMode.BY_ITEM to R.string.split_mode_by_item,
    SplitMode.BY_SHARES to R.string.split_mode_by_shares,
    SplitMode.BY_PERCENT to R.string.split_mode_by_percent,
    SplitMode.BY_AMOUNT to R.string.split_mode_by_amount,
)

/** Where the lines below a group start: as far in as what the rows of the group hold. */
private val LINE_INSET = SvSpacing.screen + SvSpacing.sm

private val PERSON_AVATAR_SIZE = 44.dp
private val DETAIL_GAP = 2.dp
private val SHARER_SIZE = 22.dp
private val PAYER_SIZE = 36.dp
private val EDIT_ICON_SIZE = 20.dp
