package com.dantech.splitvibe.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvFieldButton
import com.dantech.splitvibe.designsystem.components.SvFieldRow
import com.dantech.splitvibe.designsystem.components.SvFieldWidth
import com.dantech.splitvibe.designsystem.components.SvIconTile
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvOpenListRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.domain.inputNumberText
import com.dantech.splitvibe.domain.numberText
import com.dantech.splitvibe.text.labelRes
import com.dantech.splitvibe.ui.common.InputField
import com.dantech.splitvibe.ui.common.NumberField
import com.dantech.splitvibe.ui.common.style
import java.math.BigDecimal
import java.util.Locale

/**
 * The bill as the user can correct it: where and when, the currency, what it was spent on, every
 * item and charge, and the total printed on it. The items and charges are rows of what they hold, of
 * which one at a time is open with its fields; a row added opens itself. Each change is saved as it is
 * typed; the split follows it.
 */
@Composable
fun BillEditorContent(bill: Bill, onEdit: ((Bill) -> Bill) -> Unit, onDone: () -> Unit) {
    val currency = bill.currency
    // The row open to be changed, kept by the id of its item or charge, so that the same row stays open
    // when rows above it are taken away or the bill changes elsewhere.
    var openRow by rememberSaveable { mutableStateOf<String?>(null) }
    // The rows there were when one was added: the one that is not among them is the new row.
    var rowsBeforeAdding by remember { mutableStateOf<List<String>?>(null) }
    val rows = rowsOf(bill)
    LaunchedEffect(rows) {
        val before = rowsBeforeAdding ?: return@LaunchedEffect
        rows.firstOrNull { it !in before }?.let { added ->
            openRow = added
            rowsBeforeAdding = null
        }
    }
    val add: ((Bill) -> Bill) -> Unit = { change ->
        rowsBeforeAdding = rows
        onEdit(change)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            SvTopBar(title = stringResource(R.string.split_edit_bill), backLabel = stringResource(R.string.common_back), onBack = onDone)
        },
        bottomBar = { SvBottomBar { SvPrimaryButton(stringResource(R.string.common_done), onClick = onDone) } },
    ) { padding ->
        // The list ends above the bottom bar, so nothing scrolls under it and the keyboard never pushes a field behind it.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "bill") {
                SvCard(
                    modifier = rowMotion()
                        .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                        InputField(
                            text = bill.merchant.orEmpty(),
                            onTextChange = { text -> onEdit { BillEditor.withMerchant(it, text) } },
                            label = stringResource(R.string.editor_where),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.editor_where_placeholder),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        )
                        SvFieldRow(widths = DATE_AND_CURRENCY) {
                            InputField(
                                text = bill.date.orEmpty(),
                                onTextChange = { text -> onEdit { BillEditor.withDate(it, text) } },
                                label = stringResource(R.string.editor_date),
                                placeholder = stringResource(R.string.editor_date_placeholder),
                            )
                            InputField(
                                text = currency.code,
                                onTextChange = { text ->
                                    val code = text.trim().uppercase(Locale.ROOT)
                                    if (BillCurrency.isKnownCode(code)) {
                                        onEdit {
                                            BillEditor.withCurrency(it, BillCurrency.resolve(code, null, emptyList()))
                                        }
                                    }
                                },
                                label = stringResource(R.string.editor_currency),
                                sameValue = { typed, stored -> typed.trim().equals(stored.trim(), ignoreCase = true) },
                                isError = { typed -> !BillCurrency.isKnownCode(typed) },
                                maxLength = 3,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                        }
                    }
                }
            }

            item(key = "category") {
                Column(modifier = rowMotion()) {
                    SvSectionHeader(stringResource(R.string.editor_category), hint = stringResource(R.string.editor_category_hint))
                    SvChoiceChips(
                        options = BillCategory.entries.map { stringResource(it.labelRes) },
                        selected = bill.category.ordinal,
                        onSelect = { index -> onEdit { it.copy(category = BillCategory.entries[index]) } },
                        modifier = Modifier.padding(horizontal = SvSpacing.screen),
                        leading = { index ->
                            val style = BillCategory.entries[index].style
                            SvIconTile(style.icon, style.color, size = CATEGORY_TILE_SIZE)
                        },
                    )
                }
            }

            item(key = "items header") { SvSectionHeader(stringResource(R.string.split_items), modifier = rowMotion()) }
            // The items and "Add an item" form one group.
            val itemRows = bill.items.size + 1
            itemsIndexed(bill.items, key = { _, item -> itemRow(item.id) }) { index, item ->
                val row = itemRow(item.id)
                val position = SvRowPosition.of(index, itemRows)
                if (openRow == row) {
                    OpenItem(item, currency, position, onEdit, modifier = rowMotion())
                } else {
                    ClosedItem(item, currency, position, onOpen = { openRow = row }, modifier = rowMotion())
                }
            }
            item(key = "add item") {
                SvListRow(position = SvRowPosition.of(itemRows - 1, itemRows), modifier = rowMotion()) {
                    SvQuietButton(
                        stringResource(R.string.editor_add_item),
                        onClick = { add { BillEditor.withItemAdded(it, name = "") } },
                        modifier = Modifier.weight(1f),
                        icon = SvActionIcons.Plus,
                    )
                }
            }

            item(key = "charges header") {
                SvSectionHeader(
                    stringResource(R.string.split_charges),
                    modifier = rowMotion(),
                    hint = stringResource(R.string.editor_charges_hint),
                )
            }
            // The charges and the ways to add one form one group.
            val chargeRows = bill.charges.size + 1
            itemsIndexed(bill.charges, key = { _, charge -> chargeRow(charge.id) }) { index, charge ->
                val row = chargeRow(charge.id)
                val position = SvRowPosition.of(index, chargeRows)
                if (openRow == row) {
                    OpenCharge(charge, currency, position, onEdit, modifier = rowMotion())
                } else {
                    ClosedCharge(charge, currency, position, onOpen = { openRow = row }, modifier = rowMotion())
                }
            }
            item(key = "add charge") {
                SvListRow(position = SvRowPosition.of(chargeRows - 1, chargeRows), modifier = rowMotion()) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                    ) {
                        ADDABLE_CHARGES.forEach { kind ->
                            SvQuietButton(
                                stringResource(addLabelOf(kind)),
                                onClick = { add { BillEditor.withChargeAdded(it, "", kind, 0L) } },
                                icon = SvActionIcons.Plus,
                            )
                        }
                    }
                }
            }

            item(key = "total header") { SvSectionHeader(stringResource(R.string.editor_total), modifier = rowMotion()) }
            item(key = "total") {
                TotalEditor(bill, onEdit, modifier = rowMotion())
            }
            item(key = "end") { Spacer(Modifier.height(SvSpacing.xl)) }
        }
    }
}

/** An item as a closed row: its name, its quantity and price, and its amount; a tap opens it. */
@Composable
private fun ClosedItem(item: BillItem, currency: BillCurrency, position: SvRowPosition, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val name = item.name.ifBlank { stringResource(R.string.common_item) }
    val each = item.unitPriceMinor?.let { stringResource(R.string.split_quantity_times, numberText(item.quantity), currency.format(it)) }
    val amount = currency.format(item.amountMinor)
    ClosedRow(
        title = name,
        detail = each,
        amount = amount,
        position = position,
        onOpen = onOpen,
        modifier = modifier,
        untitled = item.name.isBlank(),
    )
}

/** An item open to be changed: its name and the button that removes it, then its quantity, price each and amount. */
@Composable
private fun OpenItem(
    item: BillItem,
    currency: BillCurrency,
    position: SvRowPosition,
    onEdit: ((Bill) -> Bill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = item.name.ifBlank { stringResource(R.string.editor_this_item) }
    SvOpenListRow(position = position, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            // The label stands above the box of the field, so the button lines up with the box rather
            // than with the whole field: both at the bottom of the row.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                InputField(
                    text = item.name,
                    onTextChange = { text -> onEdit { BillEditor.withItemName(it, item.id, text) } },
                    label = stringResource(R.string.common_item),
                    modifier = Modifier.weight(1f),
                )
                SvFieldButton(
                    SvActionIcons.Bin,
                    label = stringResource(R.string.editor_remove, name),
                    onClick = { onEdit { BillEditor.withoutItem(it, item.id) } },
                    destructive = true,
                )
            }
            // A label on two lines, as with large text, makes its field taller from the top, so the boxes
            // of the three fields line up at their bottoms; numbers too wide for them put them one above the other.
            SvFieldRow(widths = QUANTITY_PRICE_AND_AMOUNT) {
                NumberField(
                    label = stringResource(R.string.editor_quantity),
                    text = inputNumberText(item.quantity.stripTrailingZeros()),
                    parse = ::positiveQuantity,
                    onValue = { quantity -> onEdit { BillEditor.withItemQuantity(it, item.id, quantity) } },
                )
                NumberField(
                    label = stringResource(R.string.editor_price_each),
                    text = item.unitPriceMinor?.let(currency::inputText).orEmpty(),
                    parse = currency::parse,
                    onValue = { price -> onEdit { BillEditor.withItemUnitPrice(it, item.id, price) } },
                    allowEmpty = true,
                    onEmpty = { onEdit { BillEditor.withItemUnitPrice(it, item.id, null) } },
                )
                NumberField(
                    label = stringResource(R.string.editor_amount),
                    text = amountText(item.amountMinor, currency),
                    parse = currency::parse,
                    onValue = { amount -> onEdit { BillEditor.withItemAmount(it, item.id, amount) } },
                    placeholder = currency.inputText(0),
                    emptyValue = 0L,
                )
            }
        }
    }
}

/** A charge as a closed row: its label, what kind of charge it is, and its amount; a tap opens it. */
@Composable
private fun ClosedCharge(
    charge: BillCharge,
    currency: BillCurrency,
    position: SvRowPosition,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = stringResource(charge.kind.labelRes)
    ClosedRow(
        title = charge.label.ifBlank { kind },
        detail = kind,
        amount = currency.format(charge.amountMinor),
        position = position,
        onOpen = onOpen,
        modifier = modifier,
    )
}

/**
 * A charge open to be changed: its label and its amount side by side, lined up at the bottom of their
 * boxes as an item's fields are, and the button that removes it.
 */
@Composable
private fun OpenCharge(
    charge: BillCharge,
    currency: BillCurrency,
    position: SvRowPosition,
    onEdit: ((Bill) -> Bill) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A discount is typed as what it takes off; the bill keeps it as a negative line.
    val shown = if (charge.kind == ChargeKind.DISCOUNT) -charge.amountMinor else charge.amountMinor
    SvOpenListRow(position = position, modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            SvFieldRow(widths = LABEL_AND_AMOUNT, modifier = Modifier.weight(1f)) {
                InputField(
                    text = charge.label,
                    onTextChange = { text -> onEdit { BillEditor.withCharge(it, charge.id, text, charge.kind, charge.amountMinor) } },
                    label = stringResource(charge.kind.labelRes),
                    // A label left empty is saved as the kind's own name, which is not a change to undo while typing.
                    sameValue = { typed, stored -> typed.trim().ifEmpty { BillEditor.defaultLabel(charge.kind) } == stored.trim() },
                )
                NumberField(
                    label = stringResource(R.string.editor_amount),
                    text = amountText(shown, currency),
                    parse = currency::parse,
                    onValue = { entered ->
                        onEdit {
                            BillEditor.withCharge(it, charge.id, charge.label, charge.kind, BillEditor.signedCharge(charge.kind, entered))
                        }
                    },
                    placeholder = currency.inputText(0),
                    emptyValue = 0L,
                )
            }
            SvFieldButton(
                SvActionIcons.Bin,
                label = stringResource(R.string.editor_remove, charge.label),
                onClick = { onEdit { BillEditor.withoutCharge(it, charge.id) } },
                destructive = true,
            )
        }
    }
}

/**
 * A closed row of the editor: [title] and the [detail] below it, the [amount], and the pencil that says
 * a tap opens it. An item without a name yet shows its placeholder, [untitled], in the quieter colour.
 */
@Composable
private fun ClosedRow(
    title: String,
    detail: String?,
    amount: String,
    position: SvRowPosition,
    onOpen: () -> Unit,
    modifier: Modifier,
    untitled: Boolean = false,
) {
    SvListRow(
        position = position,
        label = listOfNotNull(title, detail, amount).joinToString(", "),
        onClickLabel = stringResource(R.string.editor_change, title),
        onClick = onOpen,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = if (untitled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(SvSpacing.md))
        Text(amount, style = MaterialTheme.typography.amountSmall)
        Spacer(Modifier.width(SvSpacing.md))
        Icon(
            SvActionIcons.Pencil,
            contentDescription = null,
            modifier = Modifier.size(EDIT_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The total printed on the bill, which may be left empty to make the lines the total. When it
 * differs from what the lines add up to, the difference is shown with the two ways to settle it.
 */
@Composable
private fun TotalEditor(bill: Bill, onEdit: ((Bill) -> Bill) -> Unit, modifier: Modifier = Modifier) {
    val currency = bill.currency
    SvCard(
        modifier = modifier
            .padding(horizontal = SvSpacing.screen)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            NumberField(
                label = stringResource(R.string.editor_printed_total),
                text = bill.printedTotalMinor?.let(currency::inputText).orEmpty(),
                parse = currency::parse,
                onValue = { total -> onEdit { BillEditor.withPrintedTotal(it, total) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = currency.inputText(bill.linesTotalMinor),
                suffix = currency.code.ifEmpty { null },
                allowEmpty = true,
                onEmpty = { onEdit { BillEditor.withPrintedTotal(it, null) } },
            )
            Text(
                if (bill.printedTotalMinor == null) {
                    stringResource(R.string.editor_total_empty, currency.format(bill.linesTotalMinor))
                } else {
                    stringResource(R.string.editor_lines_add_up, currency.format(bill.linesTotalMinor))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (bill.differenceMinor != 0L) {
                SvCard(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                    Column(modifier = Modifier.padding(SvSpacing.lg), horizontalAlignment = Alignment.Start) {
                        val gap = bill.differenceMinor
                        Text(
                            stringResource(
                                if (gap > 0) R.string.editor_total_more else R.string.editor_total_less,
                                currency.format(Math.abs(gap)),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        SvTextAction(stringResource(R.string.editor_use_lines), onClick = { onEdit(BillEditor::withLinesAsTotal) })
                        SvTextAction(stringResource(R.string.editor_keep_total), onClick = { onEdit(BillEditor::withDifferenceAsCharge) })
                    }
                }
            }
        }
    }
}

/** The rows of [bill] that can be opened, in order: its items, then its charges. */
private fun rowsOf(bill: Bill): List<String> = bill.items.map { itemRow(it.id) } + bill.charges.map { chargeRow(it.id) }

private fun itemRow(id: String) = "item $id"

private fun chargeRow(id: String) = "charge $id"

/** An amount as a field shows it, empty for nothing so a new line starts blank. */
private fun amountText(minor: Long, currency: BillCurrency): String = if (minor == 0L) "" else currency.inputText(minor)

/** A quantity typed with either decimal mark, such as "2" or "0,5": more than zero, up to [MAX_QUANTITY], with at most three decimals. */
private fun positiveQuantity(text: String): BigDecimal? {
    if (!QUANTITY_TEXT.matches(text.trim())) return null
    // Without trailing zeros, so "2.0" and "2" read as the same quantity.
    val quantity = text.trim().replace(',', '.').toBigDecimal().stripTrailingZeros()
    return quantity.takeIf { it.signum() > 0 && it <= MAX_QUANTITY }
}

private val QUANTITY_TEXT = Regex("""\d{1,5}([.,]\d{1,3})?""")
private val MAX_QUANTITY = BigDecimal(10_000)

/** The charges a person adds by hand; rounding and the difference come from the bill itself. */
private val ADDABLE_CHARGES = listOf(ChargeKind.TAX, ChargeKind.SERVICE_CHARGE, ChargeKind.TIP, ChargeKind.DISCOUNT, ChargeKind.OTHER)

/** What the button that adds a charge of [kind] says, such as "Add tax". */
private fun addLabelOf(kind: ChargeKind): Int = when (kind) {
    ChargeKind.TAX -> R.string.editor_add_tax
    ChargeKind.SERVICE_CHARGE -> R.string.editor_add_service_charge
    ChargeKind.TIP -> R.string.editor_add_tip
    ChargeKind.DISCOUNT -> R.string.editor_add_discount
    ChargeKind.ROUNDING -> R.string.editor_add_rounding
    ChargeKind.OTHER -> R.string.editor_add_other
}

/** The date and the currency share a row three to two, as on the approved design. */
private val DATE_AND_CURRENCY = listOf(SvFieldWidth.Share(3f), SvFieldWidth.Share(2f))

private val QUANTITY_PRICE_AND_AMOUNT = listOf(SvFieldWidth.Fixed(80.dp), SvFieldWidth.Share(1f), SvFieldWidth.Share(1f))

/** A label is text that may scroll in its field, so only the amount beside it decides whether the two fit side by side. */
private val LABEL_AND_AMOUNT = listOf(SvFieldWidth.Share(1f, mustFit = false), SvFieldWidth.Fixed(128.dp))

private val CATEGORY_TILE_SIZE = 26.dp
private val EDIT_ICON_SIZE = 18.dp
