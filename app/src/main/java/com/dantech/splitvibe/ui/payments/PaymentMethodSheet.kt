package com.dantech.splitvibe.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.domain.payments.VietQrBanks
import java.util.Locale

/** The kinds of payment method, in the order the sheet offers them. */
internal enum class MethodKind(val label: String) {
    VIETQR("VietQR"),
    PROMPTPAY("PromptPay"),
    UPI("UPI"),
    PAYPAL("PayPal.Me"),
    SEPA("SEPA"),
    LINK("Payment link"),
    DETAILS("Other"),
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
        title = if (editing == null) "Add a way to get paid" else "Change ${initial?.title}",
        supportingText = "Kept on this phone. A request carries only the one you send it with.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = { onSave(method) }, enabled = problem == null) { Text("Save") }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SvSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            // A method keeps its kind once saved; another kind is another method.
            if (editing == null) SvChoiceChips(MethodKind.entries.map { it.label }, selected = kind, onSelect = { kind = it })

            when (chosen) {
                MethodKind.VIETQR -> {
                    BankPicker(bankBin = bankBin, onPick = { bankBin = it })
                    Field("Account number", first, { first = it }, keyboard = KeyboardType.Number)
                    Field("Name on the account", second, { second = it }, capitalization = KeyboardCapitalization.Characters)
                }

                MethodKind.PROMPTPAY -> Field(
                    "Mobile number or ID",
                    first,
                    { first = it },
                    keyboard = KeyboardType.Phone,
                    hint = "A Thai mobile number, or the 13-digit ID PromptPay is linked to.",
                )

                MethodKind.UPI -> {
                    Field("UPI ID", first, { first = it }, keyboard = KeyboardType.Email, hint = "Such as name@okaxis.")
                    Field("Name on the account", second, { second = it }, capitalization = KeyboardCapitalization.Words)
                }

                MethodKind.PAYPAL -> Field("PayPal.Me name", first, { first = it }, prefix = "paypal.me/")

                MethodKind.SEPA -> {
                    Field("Name on the account", first, { first = it }, capitalization = KeyboardCapitalization.Words)
                    Field("IBAN", second, { second = it }, capitalization = KeyboardCapitalization.Characters)
                    Field("BIC (if your bank asks for it)", third, { third = it }, capitalization = KeyboardCapitalization.Characters)
                }

                MethodKind.LINK -> {
                    Field(
                        "Link",
                        first,
                        { first = it },
                        keyboard = KeyboardType.Uri,
                        hint = "A link you made in Revolut, Wise, Venmo, Cash App or another payment app.",
                    )
                    (method as? PaymentMethod.PastedLink)?.host?.let { host ->
                        Text("Opens $host", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                MethodKind.DETAILS -> {
                    Field("What it is", first, {
                        first = it
                    }, capitalization = KeyboardCapitalization.Words, hint = "Such as Zelle or Bank transfer.")
                    Field("Details", second, {
                        second = it
                    }, singleLine = false, hint = "What the payer types in, such as an email or an account.")
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
    Field("Bank", query.ifEmpty { picked?.shortName.orEmpty() }, { query = it }, hint = picked?.let { "Code ${it.bin}" })
    val search = query.trim().lowercase(Locale.ROOT)
    if (search.isNotEmpty()) {
        val matches = VietQrBanks.ALL.filter {
            it.shortName.lowercase(Locale.ROOT).contains(search) || it.bin.startsWith(search)
        }.take(MATCHES)
        if (matches.isEmpty()) Text("No bank goes by that name.", style = MaterialTheme.typography.bodyMedium)
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
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_FIELD)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        prefix = prefix?.let { { Text(it) } },
        supportingText = hint?.let { { Text(it) } },
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
private const val MAX_FIELD = 500
