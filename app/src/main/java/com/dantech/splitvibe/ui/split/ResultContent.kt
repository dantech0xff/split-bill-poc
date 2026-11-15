package com.dantech.splitvibe.ui.split

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPersonChip
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.motion.SvAmount
import com.dantech.splitvibe.designsystem.motion.bounceOnChange
import com.dantech.splitvibe.designsystem.motion.popIn
import com.dantech.splitvibe.designsystem.motion.rememberSvEntryState
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.designsystem.motion.staggeredEntry
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
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
)

/**
 * Second step of a split: the bill, who is on it, how it is divided and who paid, and what each
 * person pays as a result. Every change is saved as it is made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultContent(result: SplitResultState, people: List<Person>, actions: ResultActions) {
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    var changingPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var splittingItem by rememberSaveable { mutableStateOf<String?>(null) }
    var changingPayments by rememberSaveable { mutableStateOf(false) }
    var changingRate by rememberSaveable { mutableStateOf(false) }
    var showOcrText by rememberSaveable { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bill.merchant ?: "Bill", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = actions.onEditBill) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit bill")
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
            )
        },
    ) { padding ->
        // Every row has a key of its own and moves with rowMotion, so when the split changes, a row
        // that comes or goes fades and the rows below it slide to their new places.
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "total") {
                TotalCard(
                    result = result,
                    onChangeRate = { changingRate = true },
                    modifier = rowMotion()
                        .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm)
                        .staggeredEntry(entry, index = 0),
                )
            }

            // Keyed by its text, so a notice that moves up when another one is settled slides there.
            // The same sentence twice would say nothing new.
            items((plan.openNotices() + bill.warnings).distinct(), key = { note -> "notice $note" }) { note ->
                SvMessageCard(
                    text = note,
                    isError = false,
                    modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs),
                )
            }
            bill.differenceNote()?.let { note ->
                item(key = "difference") {
                    SvMessageCard(
                        text = note,
                        isError = false,
                        action = "Edit bill",
                        onAction = actions.onEditBill,
                        modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs),
                    )
                }
            }

            item(key = "people") {
                Column(modifier = rowMotion()) {
                    SvSectionHeader(
                        "Who is on the bill?",
                        hint = "Tap You to share the bill or not, and anyone else to rename or remove them.",
                    )
                    FlowRow(
                        modifier = Modifier.padding(horizontal = SvSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                    ) {
                        val meSharing = ME_ID in sharing
                        SvPersonChip(
                            name = ME_LABEL,
                            selected = meSharing,
                            onClick = actions.onToggleMe,
                            modifier = Modifier
                                .bounceOnChange(meSharing)
                                .semantics { stateDescription = if (meSharing) "You share the bill" else "You do not share the bill" },
                        )
                        plan.participants.filterNot { it.isMe }.forEach { participant ->
                            // Keyed by person, so a chip stays itself when others are added or removed.
                            key(participant.id) {
                                val name = result.nameOf(participant.id)
                                SvPersonChip(
                                    name = name,
                                    onClick = { changingPerson = participant.id },
                                    onClickLabel = "Change $name",
                                    modifier = Modifier.popIn(entry),
                                )
                            }
                        }
                        AssistChip(
                            onClick = { addingPerson = true },
                            label = { Text("Add someone") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            item(key = "mode") {
                Column(modifier = rowMotion()) {
                    SvSectionHeader("How is it split?")
                    SvChoiceChips(
                        options = MODES.map { it.second },
                        selected = MODES.indexOfFirst { it.first == mode },
                        onSelect = { index -> actions.onSetMode(MODES[index].first) },
                        modifier = Modifier.padding(horizontal = SvSpacing.lg),
                    )
                }
            }

            if (bill.items.isNotEmpty()) {
                item(key = "items header") {
                    SvSectionHeader(
                        "Items",
                        modifier = rowMotion(),
                        hint = if (byItem) "Tap an item to choose who had it." else "Choose By item to split them one by one.",
                    )
                }
                itemsIndexed(bill.items, key = { _, item -> "item ${item.id}" }) { index, item ->
                    ItemRow(
                        bill = bill,
                        item = item,
                        sharers = if (byItem) describe(plan.setup.items[item.id], result) else null,
                        position = SvRowPosition.of(index, bill.items.size),
                        onClick = if (byItem) ({ splittingItem = item.id }) else null,
                        modifier = rowMotion().staggeredEntry(entry, index = index + 1),
                    )
                }
            }

            val chargeRows = bill.charges + listOfNotNull(differenceCharge(bill))
            if (chargeRows.isNotEmpty()) {
                item(key = "charges header") {
                    SvSectionHeader(
                        "Taxes, fees and discounts",
                        modifier = rowMotion(),
                        hint = if (byItem) "Shared in proportion to each person's items." else "Part of the total, divided the same way.",
                    )
                }
                itemsIndexed(chargeRows, key = { _, charge -> "charge ${charge.id}" }) { index, charge ->
                    ChargeRow(bill, charge, SvRowPosition.of(index, chargeRows.size), modifier = rowMotion())
                }
            }

            item(key = "shares header") { SvSectionHeader("Each person pays", modifier = rowMotion()) }
            itemsIndexed(plan.participants, key = { _, participant -> "share ${participant.id}" }) { index, participant ->
                ShareRow(
                    result = result,
                    participant = participant,
                    position = SvRowPosition.of(index, plan.participants.size),
                    onValue = { value -> actions.onSetWholeValue(participant.id, value) },
                    modifier = rowMotion(),
                )
            }
            if (mode == SplitMode.BY_SHARES || mode == SplitMode.BY_PERCENT || mode == SplitMode.BY_AMOUNT) {
                item(key = "given") {
                    val division = plan.setup.whole
                    val given = sharing.sumOf { division?.valueOf(it) ?: 0L }
                    DivisionTotalRow(
                        mode = mode,
                        given = given,
                        amountMinor = bill.totalMinor,
                        currency = bill.currency,
                        modifier = rowMotion(),
                    )
                }
            }
            if (result.problems.isEmpty()) {
                item(key = "shares total") { SharesTotalRow(bill = bill, total = result.split.totalMinor, modifier = rowMotion()) }
            }
            // Keyed by place: two lines of the same name can have the same problem.
            itemsIndexed(result.problems, key = { index, _ -> "problem $index" }) { _, problem ->
                SvMessageCard(
                    text = problem,
                    isError = true,
                    modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs),
                )
            }

            item(key = "paid header") { SvSectionHeader("Paid by", modifier = rowMotion()) }
            item(key = "paid") {
                PaidRow(result = result, onClick = { changingPayments = true }, modifier = rowMotion())
            }

            item(key = "sent text") {
                Column(modifier = rowMotion()) {
                    Spacer(Modifier.height(SvSpacing.sm))
                    if (result.ocrDocument.isNotBlank()) {
                        TextButton(onClick = { showOcrText = !showOcrText }, modifier = Modifier.padding(horizontal = SvSpacing.sm)) {
                            Text(if (showOcrText) "Hide what was sent to Claude" else "Show what was sent to Claude")
                        }
                        if (showOcrText) {
                            Text(
                                text = result.ocrDocument,
                                modifier = Modifier
                                    .padding(horizontal = SvSpacing.lg)
                                    .horizontalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Spacer(Modifier.height(SvSpacing.lg))
                }
            }
        }
    }

    val sheetPeople = plan.participants.map { SheetPerson(it.id, result.nameOf(it.id)) }

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

@Composable
private fun TotalCard(result: SplitResultState, onChangeRate: () -> Unit, modifier: Modifier = Modifier) {
    val bill = result.bill
    SvCard(
        modifier = modifier.fillMaxWidth(),
        elevation = SvElevation.Level2,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(SvSpacing.xl)) {
            Text("Bill total", style = MaterialTheme.typography.labelLarge)
            SvAmount(bill.totalMinor, format = bill::format, style = MaterialTheme.typography.amountLarge, countUp = true)
            bill.date?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (result.summary.isNotBlank()) {
                Spacer(Modifier.height(SvSpacing.sm))
                Text(result.summary, style = MaterialTheme.typography.bodyMedium)
            }
            if (result.needsRate) {
                Spacer(Modifier.height(SvSpacing.sm))
                val converted = result.converted(bill.totalMinor)
                val rate = result.exchangeRate
                if (converted != null && rate != null) {
                    Text("About $converted", style = MaterialTheme.typography.titleMedium)
                    Text(rateNote(rate), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Add an exchange rate to see this bill in ${result.localCurrency}.", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onChangeRate) { Text(if (rate == null) "Add a rate" else "Change the rate") }
            }
        }
    }
}

/** Where a rate comes from, such as "1 USD = 25,350 VND, Frankfurter rate of 2026-11-14". */
private fun rateNote(rate: ExchangeRate): String = when {
    rate.isByHand -> "${rate.describe()}, a rate you entered"
    rate.date != null -> "${rate.describe()}, ${rate.source} rate of ${rate.date}"
    else -> "${rate.describe()}, ${rate.source}"
}

@Composable
private fun ItemRow(
    bill: Bill,
    item: BillItem,
    sharers: String?,
    position: SvRowPosition,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val name = item.name.ifBlank { "Item" }
    val unitPrice = item.unitPriceMinor
    val quantity = if (unitPrice != null && item.quantity.compareTo(BigDecimal.ONE) != 0) {
        "${item.quantity.stripTrailingZeros().toPlainString()} × ${bill.format(unitPrice)}"
    } else {
        null
    }
    val amount = bill.format(item.amountMinor)
    val content: @Composable RowScope.() -> Unit = {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (quantity != null) {
                Text(quantity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sharers != null) Text(sharers, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(amount, style = MaterialTheme.typography.amountSmall)
    }
    if (onClick == null) {
        SvListRow(position = position, modifier = modifier, content = content)
    } else {
        SvListRow(
            position = position,
            label = listOfNotNull(name, quantity, amount, sharers).joinToString(", "),
            onClickLabel = "Choose who had $name",
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
 * One person's row: what they pay and what of the bill it is made of, and, when the bill is divided
 * by shares, percentages or amounts, their part to change.
 */
@Composable
private fun ShareRow(
    result: SplitResultState,
    participant: Participant,
    position: SvRowPosition,
    onValue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bill = result.bill
    val plan = result.plan
    val mode = plan.mode
    val name = result.nameOf(participant.id)
    val sharing = participant.id in plan.sharingIds
    val share = result.split.shareOf(participant.id)
    val paid = plan.payments.filter { it.participantId == participant.id }.sumOf { it.amountMinor }
    val hasInput = sharing && (mode == SplitMode.BY_SHARES || mode == SplitMode.BY_PERCENT || mode == SplitMode.BY_AMOUNT)
    val detail = buildList {
        when {
            !sharing -> add("Not sharing the bill")
            share == null -> Unit
            hasInput -> add("Pays ${bill.format(share.totalMinor)}")
            else -> add("Items ${bill.format(share.itemsMinor)} · charges ${bill.format(share.chargesMinor)}")
        }
        if (share != null && sharing) result.converted(share.totalMinor)?.let { add("about $it") }
        if (paid != 0L) add("paid ${bill.format(paid)}")
    }.joinToString(" · ")

    SvListRow(position = position, modifier = modifier) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(SvSpacing.md))
        when {
            hasInput -> DivisionValueInput(
                name = name,
                mode = mode,
                value = plan.setup.whole?.valueOf(participant.id) ?: 0L,
                currency = bill.currency,
                onValue = onValue,
            )

            share != null -> SvAmount(share.totalMinor, format = bill::format, style = MaterialTheme.typography.amountMedium)
        }
    }
}

/** The proof under the shares: together they are exactly the bill total. */
@Composable
private fun SharesTotalRow(bill: Bill, total: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = SvSpacing.xxl, vertical = SvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(SvSpacing.sm))
        Text("Adds up to the bill total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        SvAmount(total, format = bill::format, style = MaterialTheme.typography.amountMedium)
    }
}

@Composable
private fun PaidRow(result: SplitResultState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bill = result.bill
    val payments = result.plan.payments
    val text = when {
        payments.isEmpty() -> "Choose who paid"
        payments.size == 1 -> "${result.nameOf(payments.single().participantId)} paid ${bill.format(payments.single().amountMinor)}"
        else -> payments.joinToString(", ") { "${result.nameOf(it.participantId)} ${bill.format(it.amountMinor)}" }
    }
    SvListRow(position = SvRowPosition.Single, label = text, onClickLabel = "Change who paid", onClick = onClick, modifier = modifier) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FinishBar(result: SplitResultState, onSendRequests: () -> Unit, onFinish: () -> Unit, onAddToGroup: () -> Unit) {
    val payments = result.plan.payments
    val iPaidItAll = payments.singleOrNull()?.participantId == ME_ID
    val others = result.split.shares.filter { it.personId != ME_ID && it.totalMinor > 0 }
    // A bill shared in a group is kept there; one already in a group is sent to it again after a change.
    val inGroup = result.saved.group
    val toGroup = SvMenuOption(inGroup?.let { "Update in ${it.groupName}" } ?: "Add to a group", onAddToGroup)
    SvBottomBar {
        when {
            result.problems.isNotEmpty() -> SvPrimaryButton("Finish the split", onClick = {}, enabled = false)

            // In a group, people settle up across all its bills, so this bill is not asked for person by person.
            inGroup != null -> SvSplitButton(
                text = "Update in ${inGroup.groupName}",
                onClick = onAddToGroup,
                moreLabel = "More ways to finish",
                options = listOf(SvMenuOption("Finish the split", onFinish)),
            )

            iPaidItAll && others.isNotEmpty() -> {
                val people = if (others.size == 1) "1 person" else "${others.size} people"
                SvSplitButton(
                    text = "Request ${result.bill.format(others.sumOf { it.totalMinor })} from $people",
                    onClick = onSendRequests,
                    moreLabel = "More ways to finish",
                    options = listOf(toGroup, SvMenuOption("Finish without asking", onFinish)),
                    // The button turns into the mark that confirms the requests on the next step.
                    modifier = Modifier.sharedSurface(SEND_SURFACE, resize = true),
                )
            }

            else -> SvSplitButton(
                text = "Finish the split",
                onClick = onFinish,
                moreLabel = "More ways to finish",
                options = listOf(toGroup),
            )
        }
    }
}

/** Who has an item and how, as its row says it: "Everyone", "Minh, Lan" or "Minh 60%, Lan 40%". */
private fun describe(division: Division?, result: SplitResultState): String {
    val bill = result.bill
    val sharing = result.plan.sharingIds
    fun names(ids: Collection<String>) = sharing.filter { it in ids }.joinToString(", ") { result.nameOf(it) }
    fun parts(values: Map<String, Long>, show: (Long) -> String) =
        sharing.filter { (values[it] ?: 0L) != 0L }.joinToString(", ") { "${result.nameOf(it)} ${show(values.getValue(it))}" }
    return when (division) {
        null -> "Everyone"

        is Division.Equally -> {
            val among = if (division.among.isEmpty()) sharing else sharing.filter { it in division.among }
            val chosen = among.filter { it !in division.except }
            if (chosen.isEmpty() || chosen.size == sharing.size) "Everyone" else names(chosen)
        }

        is Division.ByShares -> parts(division.shares) { if (it == 1L) "1 share" else "$it shares" }

        is Division.ByPercent -> parts(division.basisPoints) { "${percentText(it)}%" }

        is Division.ByAmount -> parts(division.amounts) { bill.format(it) }
    }.ifEmpty { "Nobody yet" }
}

/** The gap between the printed total and the lines, as a line of its own so the shares can be followed. */
private fun differenceCharge(bill: Bill): BillCharge? = if (bill.differenceMinor == 0L || bill.items.isEmpty()) {
    null
} else {
    BillCharge("difference", "Difference to the printed total", ChargeKind.OTHER, bill.differenceMinor)
}

private val MODES = listOf(
    SplitMode.EQUAL to "Equally",
    SplitMode.BY_ITEM to "By item",
    SplitMode.BY_SHARES to "By shares",
    SplitMode.BY_PERCENT to "By percent",
    SplitMode.BY_AMOUNT to "By amount",
)
