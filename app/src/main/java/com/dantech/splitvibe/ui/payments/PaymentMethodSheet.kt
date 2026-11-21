package com.dantech.splitvibe.ui.payments

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.domain.payments.VietQrBanks
import java.util.Locale

/** The kinds of payment method, in the order the sheet offers them. */
internal enum class MethodKind(@param:StringRes val label: Int) {
    VIETQR(R.string.payments_kind_vietqr),
    PROMPTPAY(R.string.payments_kind_promptpay),
    UPI(R.string.payments_kind_upi),
    PAYPAL(R.string.payments_kind_paypal),
    SEPA(R.string.payments_kind_sepa),
    LINK(R.string.payments_kind_link),
    DETAILS(R.string.payments_kind_other),
    ;

    companion object {
        fun of(method: PaymentMethod): MethodKind = when (method) {
            is PaymentMethod.VietQr -> VIETQR
            is PaymentMethod.PromptPay -> PROMPTPAY
            is PaymentMethod.Upi -> UPI
            is PaymentMethod.PayPalMe -> PAYPAL
            is PaymentMethod.SepaQr -> SEPA
            is PaymentMethod.PastedLink -> LINK
            is PaymentMethod.PlainDetails -> DETAILS
        }
    }
}

/**
 * Adds a way to get paid, or changes [editing]. Each kind asks for what its network needs, and
 * Save stays off until the method checks out; what is wrong shows once something is typed.
 */
@Composable
fun PaymentMethodSheet(editing: SavedPaymentMethod?, onSave: (PaymentMethod) -> Unit, onDismiss: () -> Unit) {
    val initial = editing?.method
    var kind by rememberSaveable { mutableIntStateOf(initial?.let { MethodKind.of(it).ordinal } ?: 0) }
    // One set of fields for every kind, so switching kinds back and forth keeps what was typed.
    var first by rememberSaveable { mutableStateOf(firstFieldOf(initial)) }
    var second by rememberSaveable { mutableStateOf(secondFieldOf(initial)) }
    var third by rememberSaveable { mutableStateOf(thirdFieldOf(initial)) }
    var bankBin by rememberSaveable { mutableStateOf((initial as? PaymentMethod.VietQr)?.bankBin.orEmpty()) }

    val chosen = MethodKind.entries[kind]
    val method = methodOf(chosen, bankBin, first, second, third)
    val problem = method.problem()
    val typed = listOf(first, second, third).any { it.isNotBlank() }

    SvBottomSheet(
        title = if (editing == null) {
            stringResource(R.string.requests_add_method)
        } else {
            stringResource(R.string.payments_change, initial?.title.orEmpty())
        },
        supportingText = stringResource(R.string.payments_sheet_hint),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.common_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.common_save),
                onClick = { onSave(method) },
                modifier = Modifier.weight(1f),
                enabled = problem == null,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SvSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            // A method keeps its kind once saved; another kind is another method.
            if (editing == null) {
                SvChoiceChips(MethodKind.entries.map { stringResource(it.label) }, selected = kind, onSelect = { kind = it })
            }

            when (chosen) {
                MethodKind.VIETQR -> {
                    BankPicker(bankBin = bankBin, onPick = { bankBin = it })
                    Field(stringResource(R.string.payments_account_number), first, { first = it }, keyboard = PRIVATE_KEYBOARD)
                    Field(
                        stringResource(R.string.payments_account_name),
                        second,
                        { second = it },
                        capitalization = KeyboardCapitalization.Characters,
                    )
                }

                MethodKind.PROMPTPAY -> Field(
                    stringResource(R.string.payments_promptpay_id),
                    first,
                    { first = it },
                    keyboard = KeyboardType.Phone,
                    hint = stringResource(R.string.payments_promptpay_hint),
                )

                MethodKind.UPI -> {
                    Field(
                        stringResource(R.string.payments_upi_id),
                        first,
                        { first = it },
                        keyboard = PRIVATE_KEYBOARD,
                        hint = stringResource(R.string.payments_upi_hint),
                    )
                    Field(
                        stringResource(R.string.payments_account_name),
                        second,
                        { second = it },
                        capitalization = KeyboardCapitalization.Words,
                    )
                }

                MethodKind.PAYPAL -> Field(stringResource(R.string.payments_paypal_name), first, { first = it }, prefix = "paypal.me/")

                MethodKind.SEPA -> {
                    Field(
                        stringResource(R.string.payments_account_name),
                        first,
                        { first = it },
                        capitalization = KeyboardCapitalization.Words,
                    )
                    Field(
                        stringResource(R.string.payments_iban),
                        second,
                        { second = it },
                        keyboard = PRIVATE_KEYBOARD,
                        capitalization = KeyboardCapitalization.Characters,
                    )
                    Field(stringResource(R.string.payments_bic), third, { third = it }, capitalization = KeyboardCapitalization.Characters)
                }

                MethodKind.LINK -> {
                    Field(
                        stringResource(R.string.payments_link),
                        first,
                        { first = it },
                        keyboard = KeyboardType.Uri,
                        hint = stringResource(R.string.payments_link_hint),
                    )
                    (method as? PaymentMethod.PastedLink)?.host?.let { host ->
                        Text(
                            stringResource(R.string.payments_opens, host),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                MethodKind.DETAILS -> {
                    Field(stringResource(R.string.payments_what_it_is), first, {
                        first = it
                    }, capitalization = KeyboardCapitalization.Words, hint = stringResource(R.string.payments_what_it_is_hint))
                    Field(stringResource(R.string.payments_details), second, {
                        second = it
                    }, singleLine = false, hint = stringResource(R.string.payments_details_hint))
                }
            }

            if (problem != null && typed) {
                Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Finds the bank by part of its name; the banks shown are those that take transfers through NAPAS. */
@Composable
private fun BankPicker(bankBin: String, onPick: (String) -> Unit) {
    val picked = VietQrBanks.byBin(bankBin)
    var query by rememberSaveable { mutableStateOf("") }
    Field(
        stringResource(R.string.payments_bank),
        query.ifEmpty { picked?.shortName.orEmpty() },
        { query = it },
        hint = picked?.let { stringResource(R.string.payments_bank_code, it.bin) },
    )
    val search = query.trim().lowercase(Locale.ROOT)
    if (search.isNotEmpty()) {
        val matches = VietQrBanks.ALL.filter {
            it.shortName.lowercase(Locale.ROOT).contains(search) || it.bin.startsWith(search)
        }.take(MATCHES)
        if (matches.isEmpty()) Text(stringResource(R.string.payments_no_bank), style = MaterialTheme.typography.bodyMedium)
        Column {
            matches.forEach { bank ->
                SvPersonChoiceRow(
                    name = bank.shortName,
                    selected = bank.bin == bankBin,
                    onSelect = {
                        onPick(bank.bin)
                        query = ""
                    },
                    detail = bank.bin,
                )
            }
        }
    }
}

/**
 * A field of the sheet. The keyboard corrects none of what is typed, as what a method holds is a
 * name or a number to be kept as typed, and every field, of one line or of more, takes up to
 * [MAX_FIELD] characters, which the proxy checks again.
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    prefix: String? = null,
    hint: String? = null,
) {
    SvTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        supportingText = hint,
        prefix = prefix,
        singleLine = singleLine,
        maxLength = MAX_FIELD,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = false,
            keyboardType = keyboard,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

/** The method the fields describe for [kind], checked or not. */
private fun methodOf(kind: MethodKind, bankBin: String, first: String, second: String, third: String): PaymentMethod = when (kind) {
    MethodKind.VIETQR -> PaymentMethod.VietQr(bankBin, first.trim(), second.trim())
    MethodKind.PROMPTPAY -> PaymentMethod.PromptPay(first.trim())
    MethodKind.UPI -> PaymentMethod.Upi(first.trim(), second.trim())
    MethodKind.PAYPAL -> PaymentMethod.PayPalMe(first.trim().removePrefix("paypal.me/"))
    MethodKind.SEPA -> PaymentMethod.SepaQr(first.trim(), second.trim(), third.trim().ifEmpty { null })
    MethodKind.LINK -> PaymentMethod.PastedLink(first.trim())
    MethodKind.DETAILS -> PaymentMethod.PlainDetails(first.trim(), second.trim())
}

private fun firstFieldOf(method: PaymentMethod?): String = when (method) {
    is PaymentMethod.VietQr -> method.account
    is PaymentMethod.PromptPay -> method.id
    is PaymentMethod.Upi -> method.vpa
    is PaymentMethod.PayPalMe -> method.handle
    is PaymentMethod.SepaQr -> method.name
    is PaymentMethod.PastedLink -> method.url
    is PaymentMethod.PlainDetails -> method.label
    null -> ""
}

private fun secondFieldOf(method: PaymentMethod?): String = when (method) {
    is PaymentMethod.VietQr -> method.accountName
    is PaymentMethod.Upi -> method.name
    is PaymentMethod.SepaQr -> method.groupedIban
    is PaymentMethod.PlainDetails -> method.details
    else -> ""
}

private fun thirdFieldOf(method: PaymentMethod?): String = (method as? PaymentMethod.SepaQr)?.bic.orEmpty()

private const val MATCHES = 6

/** The most characters any field of the sheet takes. */
private const val MAX_FIELD = 500

/**
 * The keyboard of an account number, an IBAN or a UPI ID, which a keyboard must not suggest from or
 * learn: a password keyboard does neither. The field still shows what is typed, as it hides nothing.
 */
private val PRIVATE_KEYBOARD = KeyboardType.Password
