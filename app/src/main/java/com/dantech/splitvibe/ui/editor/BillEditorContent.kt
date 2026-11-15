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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCharge
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ChargeKind
import com.dantech.splitvibe.ui.common.InputField
import com.dantech.splitvibe.ui.common.NumberField
import java.math.BigDecimal
import java.util.Locale

/**
 * The bill as the user can correct it: where and when, the currency, every item and charge, and
 * the total printed on it. Each change is saved as it is typed; the split follows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditorContent(bill: Bill, onEdit: ((Bill) -> Bill) -> Unit, onDone: () -> Unit) {
    val currency = bill.currency
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Edit bill") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = { SvBottomBar { SvPrimaryButton("Done", onClick = onDone) } },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "bill") {
                Column(
                    modifier = rowMotion().padding(horizontal = SvSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    InputField(
                        text = bill.merchant.orEmpty(),
                        onTextChange = { text -> onEdit { BillEditor.withMerchant(it, text) } },
                        label = "Where",
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Restaurant or shop",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                        InputField(
                            text = bill.date.orEmpty(),
                            onTextChange = { text -> onEdit { BillEditor.withDate(it, text) } },
                            label = "Date",
                            modifier = Modifier.weight(1f),
                            placeholder = "2026-11-14",
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
                            label = "Currency",
                            modifier = Modifier.width(CURRENCY_FIELD_WIDTH),
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

            item(key = "items header") { SvSectionHeader("Items", modifier = rowMotion()) }
            itemsIndexed(bill.items, key = { _, item -> "item ${item.id}" }) { index, item ->
                ItemEditor(item, currency, SvRowPosition.of(index, bill.items.size), onEdit, modifier = rowMotion())
            }
            item(key = "add item") {
                OutlinedButton(
                    onClick = { onEdit { BillEditor.withItemAdded(it, name = "") } },
                    modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(SvSpacing.sm))
                    Text("Add an item")
                }
            }

            item(key = "charges header") {
                SvSectionHeader("Taxes, fees and discounts", modifier = rowMotion(), hint = "Type a discount as the amount taken off.")
            }
            itemsIndexed(bill.charges, key = { _, charge -> "charge ${charge.id}" }) { index, charge ->
                ChargeEditor(charge, currency, SvRowPosition.of(index, bill.charges.size), onEdit, modifier = rowMotion())
            }
            item(key = "add charge") {
                FlowRow(
                    modifier = rowMotion().padding(horizontal = SvSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    ADDABLE_CHARGES.forEach { kind ->
                        AssistChip(
                            onClick = { onEdit { BillEditor.withChargeAdded(it, "", kind, 0L) } },
                            label = { Text("Add ${BillEditor.defaultLabel(kind).lowercase(Locale.ROOT)}") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            item(key = "total header") { SvSectionHeader("Total", modifier = rowMotion()) }
            item(key = "total") {
                TotalEditor(bill, onEdit, modifier = rowMotion())
            }
            item(key = "end") { Spacer(Modifier.height(SvSpacing.xl)) }
        }
    }
}

@Composable
private fun ItemEditor(
    item: BillItem,
    currency: BillCurrency,
    position: SvRowPosition,
    onEdit: ((Bill) -> Bill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = item.name.ifBlank { "this item" }
    SvListRow(position = position, modifier = modifier) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InputField(
                    text = item.name,
                    onTextChange = { text -> onEdit { BillEditor.withItemName(it, item.id, text) } },
                    label = "Item",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEdit { BillEditor.withoutItem(it, item.id) } }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove $name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                NumberField(
                    label = "Qty",
                    text = item.quantity.stripTrailingZeros().toPlainString(),
                    parse = ::positiveQuantity,
                    onValue = { quantity -> onEdit { BillEditor.withItemQuantity(it, item.id, quantity) } },
                    modifier = Modifier.width(QUANTITY_FIELD_WIDTH),
                )
                NumberField(
                    label = "Price each",
                    text = item.unitPriceMinor?.let(currency::inputText).orEmpty(),
                    parse = currency::parse,
                    onValue = { price -> onEdit { BillEditor.withItemUnitPrice(it, item.id, price) } },
                    modifier = Modifier.weight(1f),
                    allowEmpty = true,
                    onEmpty = { onEdit { BillEditor.withItemUnitPrice(it, item.id, null) } },
                )
                NumberField(
                    label = "Amount",
                    text = amountText(item.amountMinor, currency),
                    parse = currency::parse,
                    onValue = { amount -> onEdit { BillEditor.withItemAmount(it, item.id, amount) } },
                    modifier = Modifier.weight(1f),
                    placeholder = currency.inputText(0),
                    allowEmpty = true,
                    onEmpty = { onEdit { BillEditor.withItemAmount(it, item.id, 0L) } },
                )
            }
        }
    }
}

@Composable
private fun ChargeEditor(
    charge: BillCharge,
    currency: BillCurrency,
    position: SvRowPosition,
    onEdit: ((Bill) -> Bill) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A discount is typed as what it takes off; the bill keeps it as a negative line.
    val shown = if (charge.kind == ChargeKind.DISCOUNT) -charge.amountMinor else charge.amountMinor
    SvListRow(position = position, modifier = modifier) {
        InputField(
            text = charge.label,
            onTextChange = { text -> onEdit { BillEditor.withCharge(it, charge.id, text, charge.kind, charge.amountMinor) } },
            label = BillEditor.defaultLabel(charge.kind),
            modifier = Modifier.weight(1f),
            // A label left empty is saved as the kind's own name, which is not a change to undo while typing.
            sameValue = { typed, stored -> typed.trim().ifEmpty { BillEditor.defaultLabel(charge.kind) } == stored.trim() },
        )
        Spacer(Modifier.width(SvSpacing.sm))
        NumberField(
            label = "Amount",
            text = amountText(shown, currency),
            parse = currency::parse,
            onValue = { entered ->
                onEdit { BillEditor.withCharge(it, charge.id, charge.label, charge.kind, BillEditor.signedCharge(charge.kind, entered)) }
            },
            modifier = Modifier.width(CHARGE_FIELD_WIDTH),
            placeholder = currency.inputText(0),
            allowEmpty = true,
            onEmpty = { onEdit { BillEditor.withCharge(it, charge.id, charge.label, charge.kind, 0L) } },
        )
        IconButton(onClick = { onEdit { BillEditor.withoutCharge(it, charge.id) } }) {
            Icon(Icons.Default.Delete, contentDescription = "Remove ${charge.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The total printed on the bill, which may be left empty to make the lines the total. When it
 * differs from what the lines add up to, the difference is shown with the two ways to settle it.
 */
@Composable
private fun TotalEditor(bill: Bill, onEdit: ((Bill) -> Bill) -> Unit, modifier: Modifier = Modifier) {
    val currency = bill.currency
    Column(modifier = modifier.padding(horizontal = SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
        NumberField(
            label = "Total printed on the bill",
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
                "Left empty, the total is what the lines add up to: ${currency.format(bill.linesTotalMinor)}."
            } else {
                "The lines add up to ${currency.format(bill.linesTotalMinor)}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bill.differenceMinor != 0L) {
            SvCard(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                Column(modifier = Modifier.padding(SvSpacing.lg)) {
                    val gap = bill.differenceMinor
                    Text(
                        "The total is ${currency.format(Math.abs(gap))} ${if (gap > 0) "more" else "less"} than the lines add up to.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = { onEdit(BillEditor::withLinesAsTotal) }) { Text("Use the lines as the total") }
                    TextButton(onClick = {
                        onEdit(BillEditor::withDifferenceAsCharge)
                    }) { Text("Keep the total and add the difference as a line") }
                }
            }
        }
    }
}

/** An amount as a field shows it, empty for nothing so a new line starts blank. */
private fun amountText(minor: Long, currency: BillCurrency): String = if (minor == 0L) "" else currency.inputText(minor)

/** A quantity typed with either decimal mark, such as "2" or "0,5"; never zero or less. */
private fun positiveQuantity(text: String): BigDecimal? = // Without trailing zeros, so "2.0" and "2" read as the same quantity.
    text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.stripTrailingZeros()

/** The charges a person adds by hand; rounding and the difference come from the bill itself. */
private val ADDABLE_CHARGES = listOf(ChargeKind.TAX, ChargeKind.SERVICE_CHARGE, ChargeKind.TIP, ChargeKind.DISCOUNT, ChargeKind.OTHER)

private val CURRENCY_FIELD_WIDTH = 112.dp
private val QUANTITY_FIELD_WIDTH = 80.dp
private val CHARGE_FIELD_WIDTH = 128.dp
