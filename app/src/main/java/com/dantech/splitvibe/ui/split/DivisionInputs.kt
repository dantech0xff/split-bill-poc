package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.ui.common.NUMBER_FIELD_WIDTH
import com.dantech.splitvibe.ui.common.NumberField

/** Percentages in hundredths, read and written like an amount with two decimals: "33.33" is 3333. */
internal val PERCENT = BillCurrency("", 2)

/** A percentage in hundredths as it is shown and typed, without zeros that say nothing: 2500 is "25", 1250 is "12.5". */
internal fun percentText(basisPoints: Long): String = PERCENT.toMajor(basisPoints).stripTrailingZeros().toPlainString()

/** The value one person has in a division, in the units it counts in: shares, hundredths of a percent or minor units. */
internal fun Division.valueOf(participantId: String): Long = when (this) {
    is Division.ByShares -> shares[participantId] ?: 0L
    is Division.ByPercent -> basisPoints[participantId] ?: 0L
    is Division.ByAmount -> amounts[participantId] ?: 0L
    is Division.Equally -> 0L
}

/** What the values of a division in [mode] must add up to, for an amount of [amountMinor]; null when any total will do. */
internal fun targetOf(mode: SplitMode, amountMinor: Long): Long? = when (mode) {
    SplitMode.BY_PERCENT -> Division.ByPercent.FULL
    SplitMode.BY_AMOUNT -> amountMinor
    SplitMode.EQUAL, SplitMode.BY_ITEM, SplitMode.BY_SHARES -> null
}

/**
 * One person's part in a division, to change: a share count to step up and down, or a percentage or
 * an amount to type. [mode] says which; amounts are in [currency]. Nothing is shown for the ways of
 * dividing that have no value per person.
 */
@Composable
internal fun DivisionValueInput(name: String, mode: SplitMode, value: Long, currency: BillCurrency, onValue: (Long) -> Unit) {
    when (mode) {
        SplitMode.BY_SHARES -> ShareStepper(name = name, value = value, onValue = onValue)

        SplitMode.BY_PERCENT -> NumberField(
            label = "Percent for $name",
            text = percentText(value),
            suffix = "%",
            parse = PERCENT::parse,
            onValue = onValue,
            modifier = Modifier.width(NUMBER_FIELD_WIDTH),
        )

        SplitMode.BY_AMOUNT -> NumberField(
            label = "Amount for $name",
            text = currency.inputText(value),
            suffix = currency.code.ifEmpty { null },
            parse = currency::parse,
            onValue = onValue,
            modifier = Modifier.width(NUMBER_FIELD_WIDTH),
        )

        SplitMode.EQUAL, SplitMode.BY_ITEM -> Unit
    }
}

/** A person's colour and name with their part in a division beside it, as a sheet lists everyone. */
@Composable
internal fun DivisionValueRow(name: String, mode: SplitMode, value: Long, currency: BillCurrency, onValue: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.xl, vertical = SvSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        DivisionValueInput(name = name, mode = mode, value = value, currency = currency, onValue = onValue)
    }
}

@Composable
private fun ShareStepper(name: String, value: Long, onValue: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValue((value - 1).coerceAtLeast(0)) }, enabled = value > 0) {
            Icon(MINUS, contentDescription = "One share less for $name")
        }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onValue(value + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "One share more for $name")
        }
    }
}

/**
 * What has been given out so far against what has to be, such as "95% of 100%" or the amount still
 * to give out, in the error colour while the two differ.
 */
@Composable
internal fun DivisionTotalRow(mode: SplitMode, given: Long, amountMinor: Long, currency: BillCurrency, modifier: Modifier = Modifier) {
    val target = targetOf(mode, amountMinor)
    val text = when (mode) {
        SplitMode.BY_PERCENT -> "${percentText(given)}% of 100%"

        SplitMode.BY_AMOUNT -> {
            val left = amountMinor - given
            when {
                left == 0L -> "All of ${currency.format(amountMinor)} is given out"
                left > 0 -> "${currency.format(left)} of ${currency.format(amountMinor)} still to give out"
                else -> "${currency.format(-left)} more than ${currency.format(amountMinor)}"
            }
        }

        SplitMode.BY_SHARES -> if (given == 1L) "1 share in all" else "$given shares in all"

        SplitMode.EQUAL, SplitMode.BY_ITEM -> return
    }
    val balanced = if (target == null) given > 0 else given == target
    Text(
        text,
        modifier = modifier.padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm),
        style = MaterialTheme.typography.bodyMedium,
        color = if (balanced) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

/** The minus of Material's icons, which the small set of icons the app ships does not hold. */
private val MINUS = materialIcon(name = "Filled.Remove") {
    materialPath {
        moveTo(19f, 13f)
        horizontalLineTo(5f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(2f)
        close()
    }
}
