package com.dantech.splitvibe.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.components.SvTextField

/**
 * A field for something stored as soon as it is typed, such as an item's name. What the person
 * types stays as typed, even where the stored value differs from it, such as a name with a space at
 * the end on the way to the next word. [text] is the value as stored: it replaces what was typed
 * only when [sameValue] says the two are different values, which happens when the value was
 * changed elsewhere.
 */
@Composable
internal fun InputField(
    text: String,
    onTextChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sameValue: (String, String) -> Boolean = { typed, stored -> typed.trim() == stored.trim() },
    isError: (String) -> Boolean = { false },
    suffix: String? = null,
    placeholder: String? = null,
    maxLength: Int = MAX_TEXT_CHARS,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
) {
    var typed by rememberSaveable(label) { mutableStateOf(text) }
    LaunchedEffect(text) { if (!sameValue(typed, text)) typed = text }
    // The field cuts what is typed at maxLength before it arrives here.
    SvTextField(
        value = typed,
        onValueChange = { entered ->
            typed = entered
            onTextChange(entered)
        },
        label = label,
        modifier = modifier,
        isError = isError(typed),
        placeholder = placeholder,
        suffix = suffix,
        singleLine = true,
        maxLength = maxLength,
        keyboardOptions = keyboardOptions,
    )
}

/**
 * A number the person types, such as an amount, a percentage or a quantity. What they type stays as
 * typed while it is not a number yet, such as "12." on the way to "12.50"; a value is passed on only
 * once [parse] reads one.
 */
@Composable
internal fun <T : Any> NumberField(
    label: String,
    text: String,
    parse: (String) -> T?,
    onValue: (T) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    placeholder: String? = null,
    /** Whether an empty field is a value of its own, such as no printed total. */
    allowEmpty: Boolean = false,
    onEmpty: () -> Unit = {},
    /** The value an empty field stands for when it is not one of its own, such as an amount of 0 shown as an empty field. */
    emptyValue: T? = null,
) {
    // Read through here, an empty field and the value it stands for are the same, so a typed "0" stays.
    val read: (String) -> T? = { typed -> if (typed.isBlank() && emptyValue != null) emptyValue else parse(typed) }
    InputField(
        text = text,
        onTextChange = { entered ->
            if (allowEmpty && entered.isBlank()) onEmpty() else read(entered)?.let(onValue)
        },
        label = label,
        modifier = modifier,
        sameValue = { typed, stored -> if (emptyValue != null) read(typed) == read(stored) else sameNumber(typed, stored, parse) },
        isError = { typed -> !(allowEmpty && typed.isBlank()) && read(typed) == null },
        suffix = suffix,
        placeholder = placeholder,
        maxLength = MAX_NUMBER_CHARS,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

/** Two empty fields say the same; otherwise the numbers the two read as decide. */
private fun <T : Any> sameNumber(typed: String, stored: String, parse: (String) -> T?): Boolean =
    if (typed.isBlank() || stored.isBlank()) typed.isBlank() == stored.isBlank() else parse(typed) == parse(stored)

/** Wide enough for an amount in any currency, narrow enough to stand next to a name. */
internal val NUMBER_FIELD_WIDTH = 148.dp

/** Long enough for any name or label, short enough to keep the stored bill small. */
internal const val MAX_TEXT_CHARS = 80

private const val MAX_NUMBER_CHARS = 20
