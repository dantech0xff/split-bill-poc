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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A field to type text into, such as the name of a place or an amount, with its [label] above it.
 * The field takes the width it is given, so give it `Modifier.fillMaxWidth()` or a weight in a row.
 *
 * What is typed is cut at [maxLength] characters. [keyboardOptions] reach the keyboard as they are
 * given, so a field for an account number can turn off the keyboard's corrections and suggestions;
 * by default the first letter of each sentence is a capital and the keyboard's action goes to the
 * next field, or adds a new line in a field of more than one line. [keyboardActions] say what the
 * keyboard's action does.
 *
 * [placeholder] shows while the field is empty, such as an example of what to type. [prefix] and
 * [suffix] stand before and after what is typed, such as "paypal.me/" or a currency code, and
 * [leadingIcon] is a decorative icon at its start, such as a magnifier in a search field. [trailing]
 * holds buttons at its end, such as sending what was typed; give each button a label. With [isError]
 * the field shows that what was typed cannot be used, and [supportingText] below it says why, or
 * what to type. With [singleLine] off the field grows with the text, from [minLines] lines.
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
    maxLength: Int = SV_TEXT_FIELD_MAX_LENGTH,
    keyboardOptions: KeyboardOptions = if (singleLine) SingleLineKeyboard else MultiLineKeyboard,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    require(maxLength > 0) { "maxLength must be at least 1, was $maxLength" }
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
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        minLines = minLines,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) colors.error else colors.primary),
        // The label and the supporting text lie inside the field, so that they are read with it and a
        // tap on the label puts the cursor in the field.
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
                Text(label, style = typography.smallLabel, color = if (isError && enabled) colors.error else textColor)
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
                        style = typography.bodySmall,
                        color = if (isError && enabled) colors.error else quietColor,
                    )
                }
            }
        },
    )
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

/** Long enough for any name or label, short enough to keep what is stored small. */
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
private val PREFIX_GAP = 2.dp
private val ICON_SIZE = 24.dp
private val OUTLINE_WIDTH = 1.5.dp
private val STRONG_OUTLINE_WIDTH = 2.dp
private val CONTROL_RIPPLE_RADIUS = 20.dp

/** As Material dims a control that cannot be used. */
private const val DISABLED_ALPHA = 0.38f
