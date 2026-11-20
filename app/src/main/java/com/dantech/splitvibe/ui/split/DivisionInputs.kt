package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvFieldRow
import com.dantech.splitvibe.designsystem.components.SvFieldWidth
import com.dantech.splitvibe.designsystem.components.SvTallyLine
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.inputNumberText
import com.dantech.splitvibe.domain.numberText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.NUMBER_FIELD_WIDTH
import com.dantech.splitvibe.ui.common.NumberField

/** Percentages in hundredths, read and written like an amount with two decimals: "33.33" is 3333. */
internal val PERCENT = BillCurrency("", 2)

/**
 * A percentage in hundredths as it is shown, with the marks of the phone's region and without zeros
 * that say nothing: 2500 is "25", and 1250 is "12.5" in the United States and "12,5" in Vietnam.
 */
internal fun percentText(basisPoints: Long): String = numberText(PERCENT.toMajor(basisPoints))

/** A percentage in hundredths as its field shows it: as [percentText] writes it, but without grouping, so [parsePercent] reads it back. */
internal fun percentInputText(basisPoints: Long): String = inputNumberText(PERCENT.toMajor(basisPoints).stripTrailingZeros())

/**
 * A percentage as someone types it, in hundredths: "25" is 2500 and "33,33" is 3333. The last
 * separator is always the decimal mark, and more than two decimals are not a percentage the split
 * can use, so "33.333" is none rather than 33,333%.
 */
internal fun parsePercent(text: String): Long? {
    val match = PERCENT_TEXT.matchEntire(text.trim()) ?: return null
    val whole = match.groupValues[1].toLong()
    val hundredths = match.groupValues[2].padEnd(2, '0').toLong()
    return whole * 100 + hundredths
}

private val PERCENT_TEXT = Regex("""(\d{1,5})(?:[.,](\d{0,2}))?""")

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
 * dividing that have no value per person. It takes the width it is given, [NUMBER_FIELD_WIDTH] beside
 * a name.
 */
@Composable
internal fun DivisionValueInput(
    name: String,
    mode: SplitMode,
    value: Long,
    currency: BillCurrency,
    onValue: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        SplitMode.BY_SHARES -> ShareStepper(name = name, value = value, onValue = onValue, modifier = modifier)

        SplitMode.BY_PERCENT -> NumberField(
            label = stringResource(R.string.split_percent_for, name),
            text = percentInputText(value),
            suffix = stringResource(R.string.split_percent_sign),
            parse = ::parsePercent,
            onValue = onValue,
            modifier = modifier.fillMaxWidth(),
        )

        SplitMode.BY_AMOUNT -> NumberField(
            label = stringResource(R.string.split_amount_for, name),
            text = currency.inputText(value),
            suffix = currency.code.ifEmpty { null },
            parse = currency::parse,
            onValue = onValue,
            modifier = modifier.fillMaxWidth(),
        )

        SplitMode.EQUAL, SplitMode.BY_ITEM -> Unit
    }
}

/** Whether a way of dividing gives each person a value of their own to set: a share count, a percentage or an amount. */
internal val SplitMode.hasValuePerPerson: Boolean
    get() = this == SplitMode.BY_SHARES || this == SplitMode.BY_PERCENT || this == SplitMode.BY_AMOUNT

/**
 * A person's colour and name with their part in a division beside it, as a sheet lists everyone. With
 * large text, a part too wide to stand beside the name goes below it, across the row, so that no
 * number is cut off. A way of dividing without a value per person ([hasValuePerPerson]) has no part
 * to show, and the row then holds the name alone.
 */
@Composable
internal fun DivisionValueRow(name: String, mode: SplitMode, value: Long, currency: BillCurrency, onValue: (Long) -> Unit) {
    val row = Modifier
        .fillMaxWidth()
        .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.xs)
    if (!mode.hasValuePerPerson) {
        PersonName(name, modifier = row)
        return
    }
    SvFieldRow(widths = PERSON_AND_PART, modifier = row, alignment = Alignment.CenterVertically) {
        PersonName(name)
        DivisionValueInput(name = name, mode = mode, value = value, currency = currency, onValue = onValue)
    }
}

/** A person's colour and name, as tall as a row with a part beside it. */
@Composable
private fun PersonName(name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.heightIn(min = PERSON_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
    }
}

/** A name, which takes what room is left, and a person's part beside it, as wide as a number field. */
internal val PERSON_AND_PART = listOf(SvFieldWidth.Share(1f, mustFit = false), SvFieldWidth.Fixed(NUMBER_FIELD_WIDTH))

@Composable
private fun ShareStepper(name: String, value: Long, onValue: (Long) -> Unit, modifier: Modifier = Modifier) {
    // At the end of the width it is given, where a field for a part would end.
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onValue((value - 1).coerceAtLeast(0)) }, enabled = value > 0) {
            Icon(MINUS, contentDescription = stringResource(R.string.split_share_less, name))
        }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onValue(value + 1) }) {
            Icon(SvActionIcons.Plus, contentDescription = stringResource(R.string.split_share_more, name))
        }
    }
}

/**
 * What has been given out so far against what has to be, such as "95% of 100%" or the amount still
 * to give out, with a tick once the two are the same and in the error colour while they differ. The
 * caller places it, as the line below a list of people.
 */
@Composable
internal fun DivisionTotalRow(mode: SplitMode, given: Long, amountMinor: Long, currency: BillCurrency, modifier: Modifier = Modifier) {
    val target = targetOf(mode, amountMinor)
    val text = when (mode) {
        SplitMode.BY_PERCENT -> stringResource(R.string.split_percent_of_whole, percentText(given))

        SplitMode.BY_AMOUNT -> {
            val left = amountMinor - given
            when {
                left == 0L -> stringResource(R.string.split_all_given_out, currency.format(amountMinor))
                left > 0 -> stringResource(R.string.split_still_to_give_out, currency.format(left), currency.format(amountMinor))
                else -> stringResource(R.string.split_more_than, currency.format(-left), currency.format(amountMinor))
            }
        }

        SplitMode.BY_SHARES -> plural(R.plurals.split_shares_in_all, given.toInt(), given).resolve()

        SplitMode.EQUAL, SplitMode.BY_ITEM -> return
    }
    val balanced = if (target == null) given > 0 else given == target
    SvTallyLine(text = text, balanced = balanced, modifier = modifier)
}

/** As tall as a person's row in a sheet, so a row with a part to type is no shorter than one without. */
private val PERSON_ROW_HEIGHT = 56.dp

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
