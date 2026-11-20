package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A field to type text into, such as the name of a place or an amount, with its [label] above it.
 * The field takes the width it is given, so give it `Modifier.fillMaxWidth()` or a weight in a row.
 * Put it on the page, a card, a sheet or a dialog, as the approved designs do: its label and its
 * supporting text lie outside its box and keep the text colours of those, which would not read on
 * the dark bar or on a violet or lime block.
 *
 * What is typed is cut at [maxLength] characters, [SV_TEXT_FIELD_MAX_LENGTH] unless the field says
 * otherwise. [keyboardOptions] reach the keyboard as they are given; by default the first letter of
 * each sentence is a capital and the keyboard's action goes to the next field, or adds a new line in
 * a field of more than one line. [keyboardActions] say what the keyboard's action does.
 *
 * Turning auto-correct off in [keyboardOptions] only stops the keyboard from changing words: it still
 * suggests words and may learn what is typed. A value that a keyboard must not learn, such as an
 * account number, asks for a password keyboard (`KeyboardType.Password`), which keyboards neither
 * suggest for nor learn from. The field still shows what is typed, as it applies no visual
 * transformation.
 *
 * [placeholder] shows while the field is empty, such as an example of what to type. [prefix] and
 * [suffix] stand before and after what is typed, such as "paypal.me/" or a currency code, and
 * [leadingIcon] is a decorative icon at its start, such as a magnifier in a search field. [trailing]
 * holds buttons at its end, such as sending what was typed; give each button a label. With [isError]
 * the field shows that what was typed cannot be used, and [supportingText] below it says why, or
 * what to type. With [singleLine] off the field grows with the text, from [minLines] lines up to
 * [maxLines], and scrolls beyond them.
 *
 * With [titleLabel] the label is as large as the title of a section, for the one field a part of a
 * screen is about, such as how a new split should be split.
 *
 * A screen reader reads the label, the placeholder and the supporting text with the field, so a test
 * can find the field by its label.
 */
@Composable
fun SvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = if (singleLine) 1 else MULTI_LINE_MIN_LINES,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    maxLength: Int = SV_TEXT_FIELD_MAX_LENGTH,
    keyboardOptions: KeyboardOptions = if (singleLine) SingleLineKeyboard else MultiLineKeyboard,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    titleLabel: Boolean = false,
) {
    require(maxLength > 0) { "maxLength must be at least 1, was $maxLength" }
    require(singleLine || maxLines >= minLines) { "maxLines ($maxLines) must be at least minLines ($minLines)" }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val fade = if (enabled) 1f else DISABLED_ALPHA
    val textColor = colors.onSurface.copy(alpha = fade)
    val quietColor = colors.onSurfaceVariant.copy(alpha = fade)
    val outline = when {
        !enabled -> colors.outline.copy(alpha = DISABLED_ALPHA)
        isError -> colors.error
        focused -> colors.onSurface
        else -> colors.outline
    }
    val outlineWidth = if (enabled && (isError || focused)) STRONG_OUTLINE_WIDTH else OUTLINE_WIDTH
    val textStyle = typography.bodyLarge.copy(color = textColor, lineHeight = FIELD_LINE_HEIGHT)
    val shape = MaterialTheme.shapes.medium

    BasicTextField(
        value = value,
        onValueChange = { entered -> onValueChange(entered.cutAt(maxLength)) },
        modifier = modifier.semantics { if (isError) error(ERROR_DESCRIPTION) },
        enabled = enabled,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) colors.error else colors.primary),
        // The label and the supporting text lie inside the field, so that they are read with it and a
        // tap on the label puts the cursor in the field.
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(if (titleLabel) TITLE_LABEL_GAP else LABEL_GAP)) {
                Text(
                    label,
                    modifier = Modifier.then(TakesTheFieldsWidth),
                    style = if (titleLabel) typography.titleLarge else typography.smallLabel,
                    color = if (isError && enabled) colors.error else textColor,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FIELD_HEIGHT)
                        .background(SvElevation.Level1.surface, shape)
                        .border(outlineWidth, outline, shape)
                        .padding(start = SvSpacing.lg, end = if (trailing == null) SvSpacing.lg else SvSpacing.xs),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    val textPadding = Modifier.padding(vertical = TEXT_PADDING)
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            modifier = textPadding
                                .padding(end = SvSpacing.md)
                                .size(ICON_SIZE),
                            tint = quietColor,
                        )
                    }
                    if (prefix != null) {
                        // A prefix reads as the start of what is typed, so it stands close to it.
                        Text(prefix, modifier = textPadding.padding(end = PREFIX_GAP), style = textStyle, color = quietColor)
                    }
                    Box(modifier = textPadding.weight(1f)) {
                        // A placeholder longer than the field, as with large text, goes on to more lines
                        // rather than being cut off; the field returns to one line once something is typed.
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = quietColor)
                        innerTextField()
                    }
                    if (suffix != null) {
                        Text(
                            suffix,
                            modifier = textPadding.padding(start = SvSpacing.sm),
                            style = typography.bodySmallEmphasized,
                            color = quietColor,
                        )
                    }
                    if (trailing != null) {
                        CompositionLocalProvider(LocalContentColor provides textColor) {
                            Row(
                                modifier = Modifier.padding(start = SvSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                content = trailing,
                            )
                        }
                    }
                }
                if (supportingText != null) {
                    Text(
                        supportingText,
                        modifier = Modifier.then(TakesTheFieldsWidth),
                        style = typography.bodySmall,
                        color = if (isError && enabled) colors.error else quietColor,
                    )
                }
            }
        },
    )
}

/**
 * For the text above and below the box of a field, its label and its supporting text: the text takes
 * the width the field is given and goes on to more lines in it, and asks for no width of its own. Only
 * the box, with what is typed and what stands beside it, then says how wide the field would like to
 * be, as when an [SvFieldRow] decides whether fields fit side by side, so a long label never pushes a
 * field off its row.
 */
private object TakesTheFieldsWidth : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = 0
}

/** The width an [SvFieldRow] gives one of its children while it lays them side by side. */
sealed interface SvFieldWidth {
    /** A width of its own, such as that of a quantity or of a person's part beside their name. */
    data class Fixed(val width: Dp) : SvFieldWidth

    /**
     * A share of what the fixed widths leave, in proportion to [weight], such as a price beside an
     * amount. With [mustFit] off the child is no field and takes the share it gets, such as a name
     * beside a field, which goes on to more lines instead, as long as its longest word or number fits:
     * one that would be broken in two puts the children one above the other.
     */
    data class Share(val weight: Float, val mustFit: Boolean = true) : SvFieldWidth {
        init {
            require(weight > 0f) { "A share must have a weight above 0, was $weight" }
        }
    }
}

/**
 * Fields side by side, such as the quantity, the price and the amount of an item, while what each
 * field holds fits the width it is given there, and one above the other across the whole width when
 * one does not, as with text at twice its size: a number in a narrow field is then never cut off.
 * [widths] gives each child of [content], in order, its width side by side. The children line up by
 * [alignment], at the bottom unless told otherwise, so that the boxes of fields line up below labels
 * of any length, with [gap] between them, side by side and one above the other. What is typed in a
 * field and what stands beside it, such as a currency, decide; a label longer than its field goes on
 * to more lines instead. So the row may change between the two while a number is typed in, but not
 * for a long name in a label. What is no field, such as a name, goes on to more lines beside the
 * fields, between words: a word or number of it too wide for its share decides as a field does.
 */
@Composable
fun SvFieldRow(
    widths: List<SvFieldWidth>,
    modifier: Modifier = Modifier,
    alignment: Alignment.Vertical = Alignment.Bottom,
    gap: Dp = SvSpacing.sm,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == widths.size) { "${widths.size} widths for ${measurables.size} children" }
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val space = gap.roundToPx()
        val gaps = space * (measurables.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + gaps
        }
        val slots = slotsOf(widths, room = width - gaps)
        val sideBySide = slots.all { it >= 0 } &&
            measurables.indices.all { index ->
                // A field must hold all it shows on one line; what is no field may go on to more lines, but
                // only between words, so its longest word or number must fit.
                val needs = if ((widths[index] as? SvFieldWidth.Share)?.mustFit == false) {
                    measurables[index].minIntrinsicWidth(Constraints.Infinity)
                } else {
                    measurables[index].maxIntrinsicWidth(Constraints.Infinity)
                }
                needs <= slots[index]
            }
        if (sideBySide) {
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints(minWidth = slots[index], maxWidth = slots[index], maxHeight = constraints.maxHeight))
            }
            val height = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var x = 0
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x, alignment.align(placeable.height, height))
                    x += slots[index] + space
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            val height = (placeables.sumOf { it.height } + gaps).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + space
                }
            }
        }
    }
}

/**
 * The width of each child side by side, in pixels, out of [room]: the fixed ones first, then the
 * shares of what they leave, the last share taking what rounding left over. A width below 0 says
 * that the fixed widths alone do not fit.
 */
private fun Density.slotsOf(widths: List<SvFieldWidth>, room: Int): List<Int> {
    val fixed = widths.sumOf { (it as? SvFieldWidth.Fixed)?.width?.roundToPx() ?: 0 }
    val left = room - fixed
    val weights = widths.sumOf { ((it as? SvFieldWidth.Share)?.weight ?: 0f).toDouble() }
    val lastShare = widths.indexOfLast { it is SvFieldWidth.Share }
    var given = 0
    return widths.mapIndexed { index, width ->
        when (width) {
            is SvFieldWidth.Fixed -> width.width.roundToPx()

            is SvFieldWidth.Share -> {
                val share = if (index == lastShare) left - given else (left * width.weight / weights).toInt()
                given += share
                share
            }
        }
    }.let { slots -> if (left < 0) slots.map { -1 } else slots }
}

/**
 * A switch that turns a setting on or off, such as letting the phone's online speech service hear
 * the user: an ink track with a lime knob when it is on. Put it at the end of a row that is
 * toggleable as a whole, with the text that says what it does, and pass null for [onCheckedChange],
 * so that a screen reader reads the row as one switch with its text. With [onCheckedChange] it is a
 * switch of its own with a touch target of 48dp, which then needs a label from around it.
 */
@Composable
fun SvSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = tickColor(),
            checkedTrackColor = colors.inverseSurface,
            checkedBorderColor = colors.inverseSurface,
            checkedIconColor = colors.inverseSurface,
            // The same outline and colour as a tick box that is not ticked.
            uncheckedThumbColor = colors.onSurface,
            uncheckedTrackColor = Color.Transparent,
            uncheckedBorderColor = colors.onSurface,
            uncheckedIconColor = colors.surface,
        ),
    )
}

/**
 * A tick box that turns one thing on or off, such as also deleting what is on the phone. Put it at
 * the start of a row that is toggleable as a whole, with the text it belongs to, and pass null for
 * [onCheckedChange], so that a screen reader reads the row as one tick box with its text. With
 * [onCheckedChange] it is a tick box of its own with a touch target of 48dp, which then needs a label
 * from around it. It is drawn like the tick box of [SvPersonPickRow].
 */
@Composable
fun SvCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val control = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                interactionSource = null,
                indication = ripple(bounded = false, radius = CONTROL_RIPPLE_RADIUS),
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
    }
    Box(modifier = modifier.then(control), contentAlignment = Alignment.Center) {
        TickBox(ticked = checked, modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
    }
}

/**
 * One choice out of a few, such as a plan to buy, drawn as the same box as [SvCheckbox], ticked for
 * the chosen one, as [SvPersonChoiceRow] draws it. Put it in a row that is selectable as a whole,
 * with the text of the choice, and pass null for [onClick], so that a screen reader reads the row as
 * one radio button with its text. With [onClick] it is a radio button of its own with a touch target
 * of 48dp, which then needs a label from around it.
 */
@Composable
fun SvRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val control = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = ripple(bounded = false, radius = CONTROL_RIPPLE_RADIUS),
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
    }
    Box(modifier = modifier.then(control), contentAlignment = Alignment.Center) {
        TickBox(ticked = selected, modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
    }
}

/** The text cut to [maxLength] characters, never in the middle of a character that takes two, such as an emoji. */
private fun String.cutAt(maxLength: Int): String {
    if (length <= maxLength) return this
    val end = if (this[maxLength - 1].isHighSurrogate()) maxLength - 1 else maxLength
    return substring(0, end)
}

/**
 * How many characters a field takes unless it says otherwise: long enough for any name or label,
 * short enough to keep what is stored small. A field of more than one line is cut at it too, so
 * such a field must pass a cap of its own, such as the 500 characters that the details of a
 * payment method may take.
 */
const val SV_TEXT_FIELD_MAX_LENGTH = 80

private val SingleLineKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
private val MultiLineKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)
private const val MULTI_LINE_MIN_LINES = 3

/** What a screen reader says of a field whose text cannot be used, as Material's own fields say. */
private const val ERROR_DESCRIPTION = "Invalid input"

private val FIELD_HEIGHT = 52.dp

/**
 * Taller than the 24sp of the body text: the marks above stacked Vietnamese capitals, as in "Ẳ",
 * reach above a line of 24sp, and a field of one line cuts off what reaches past its line.
 */
private val FIELD_LINE_HEIGHT = 26.sp

/** Centres a line of [FIELD_LINE_HEIGHT] in a field of [FIELD_HEIGHT]. */
private val TEXT_PADDING = 13.dp
private val LABEL_GAP = 6.dp
private val TITLE_LABEL_GAP = SvSpacing.sm
private val PREFIX_GAP = 2.dp
private val ICON_SIZE = 24.dp
private val OUTLINE_WIDTH = 1.5.dp
private val STRONG_OUTLINE_WIDTH = 2.dp
private val CONTROL_RIPPLE_RADIUS = 20.dp

/** As Material dims a control that cannot be used. */
private const val DISABLED_ALPHA = 0.38f
