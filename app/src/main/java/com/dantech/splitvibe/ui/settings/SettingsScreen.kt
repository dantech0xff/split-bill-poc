package com.dantech.splitvibe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.common.LegalLinksRow
import org.koin.compose.koinInject

/** What the account part of Settings can do. */
class AccountActions(
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    /** Deletes the account, and with `withPhoneData` also what only this phone keeps. */
    val onDeleteAccount: (withPhoneData: Boolean) -> Unit,
    val onOpenPro: () -> Unit,
)

/**
 * The app's own settings: the AI calls left with the plan they are counted against and the Google
 * account behind them, the currency the app works in, and, once the user let it hear them, the
 * phone's online speech service. The privacy policy and the terms of use are linked at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    account: Account,
    accountUi: AccountUiState,
    canSignIn: Boolean,
    actions: AccountActions,
    onSave: (localCurrency: String, myName: String) -> Unit,
    onOpenPaymentMethods: () -> Unit,
    onBack: () -> Unit,
    /** The privacy policy and the terms of use; none in a build without the proxy's address. */
    legalLinks: LegalLinks? = null,
    /** Takes back, or gives again, the user's consent to the phone's online speech service; it takes effect at once. */
    onAllowSpeechService: (Boolean) -> Unit = speechServiceConsent(),
) {
    var localCurrency by rememberSaveable { mutableStateOf(settings.localCurrency) }
    // Starts again from the stored name when that changes, as deleting the phone's data with the account does.
    var myName by rememberSaveable(settings.myName) { mutableStateOf(settings.myName) }
    val currencyIsValid = BillCurrency.isKnownCode(localCurrency)
    // Offered once the user let the speech service hear them, and for the rest of this visit, so that
    // a switch turned off can be turned on again. Without the consent, the mic asks before it listens.
    val speechServiceShown = rememberSaveable { settings.speechServiceAllowed }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(SvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
        ) {
            AccountCard(account = account, accountUi = accountUi, canSignIn = canSignIn, actions = actions)

            OutlinedTextField(
                value = localCurrency,
                onValueChange = { localCurrency = it.uppercase().take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_local_currency)) },
                singleLine = true,
                isError = !currencyIsValid,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                supportingText = {
                    val hint = if (currencyIsValid) R.string.settings_local_currency_hint else R.string.settings_local_currency_invalid
                    Text(stringResource(hint))
                },
            )

            OutlinedTextField(
                value = myName,
                onValueChange = { myName = it.take(SettingsStore.MAX_NAME) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_your_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                supportingText = { Text(stringResource(R.string.settings_your_name_hint)) },
            )

            if (speechServiceShown) SpeechServiceSwitch(allowed = settings.speechServiceAllowed, onChange = onAllowSpeechService)

            SvListRow(
                position = SvRowPosition.Single,
                label = stringResource(R.string.settings_payment_methods),
                onClickLabel = stringResource(R.string.common_open),
                onClick = onOpenPaymentMethods,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_payment_methods), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_payment_methods_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }

            SvPrimaryButton(stringResource(R.string.common_save), onClick = { onSave(localCurrency, myName) }, enabled = currencyIsValid)

            legalLinks?.let { LegalLinksRow(it) }
        }
    }
}

@Composable
private fun AccountCard(account: Account, accountUi: AccountUiState, canSignIn: Boolean, actions: AccountActions) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val signedIn = account.session != null

    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            Text(stringResource(R.string.settings_ai_calls), style = MaterialTheme.typography.titleMedium)

            if (account.ai != AiStatus.NOT_SET_UP) PlanRow(account.callsLeft?.plan, onOpenPro = actions.onOpenPro)
            account.callsLeft?.let { Text(callsLeftText(it).resolve(), style = MaterialTheme.typography.bodyLarge) }
            if (account.ai != AiStatus.NOT_SET_UP) {
                Text(CALL_COSTS.resolve(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val email = account.signedInAs
            val about = when {
                account.ai == AiStatus.NOT_SET_UP -> account.ai.notice?.resolve()

                account.signInExpired -> if (email == null) {
                    stringResource(R.string.settings_sign_in_expired)
                } else {
                    stringResource(R.string.settings_sign_in_expired_as, email)
                }

                signedIn -> if (email == null) {
                    stringResource(R.string.settings_signed_in)
                } else {
                    stringResource(R.string.settings_signed_in_as, email)
                }

                canSignIn -> stringResource(R.string.settings_sign_in_for_calls)

                else -> stringResource(R.string.settings_sign_in_not_set_up)
            }
            // It changes when a sign-in or sign-out has worked, which TalkBack then reads out.
            about?.let {
                Text(
                    it,
                    modifier = announced,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                accountUi.working -> Row(
                    modifier = announced,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
                ) {
                    SvLoadingIndicator(modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.common_working), style = MaterialTheme.typography.bodyMedium)
                }

                // Deleting the account needs a session the proxy accepts, so it waits for a new sign-in.
                account.signInExpired -> Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    if (canSignIn && account.ai != AiStatus.NOT_SET_UP) {
                        SvPrimaryButton(stringResource(R.string.common_sign_in_with_google), onClick = actions.onSignIn)
                    }
                    OutlinedButton(onClick = actions.onSignOut) { Text(stringResource(R.string.settings_sign_out)) }
                }

                signedIn -> FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    OutlinedButton(onClick = actions.onSignOut) { Text(stringResource(R.string.settings_sign_out)) }
                    TextButton(
                        onClick = { confirmingDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.settings_delete_account))
                    }
                }

                canSignIn && account.ai != AiStatus.NOT_SET_UP -> {
                    SvPrimaryButton(stringResource(R.string.common_sign_in_with_google), onClick = actions.onSignIn)
                }
            }

            accountUi.message?.let { SvMessageCard(text = it.resolve(), isError = accountUi.isError, modifier = announced) }
        }
    }

    if (confirmingDelete) {
        DeleteAccountDialog(
            onDelete = { withPhoneData ->
                confirmingDelete = false
                actions.onDeleteAccount(withPhoneData)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * What deleting the account does and does not do, and whether to delete the bills, people and payment
 * methods on this phone with it, which is off each time the dialog opens.
 */
@Composable
private fun DeleteAccountDialog(onDelete: (withPhoneData: Boolean) -> Unit, onDismiss: () -> Unit) {
    var withPhoneData by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_title)) },
        text = {
            // Scrolls when large text makes it taller than the dialog.
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                Text(stringResource(R.string.settings_delete_text))
                // The whole row is one checkbox for a screen reader.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = withPhoneData, role = Role.Checkbox, onValueChange = { withPhoneData = it }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    Checkbox(checked = withPhoneData, onCheckedChange = null)
                    Text(stringResource(R.string.settings_delete_with_phone_data), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDelete(withPhoneData) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.common_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The plan the calls are counted against, Free or Pro, once the proxy has counted them, and the way
 * to the Pro screen: to get Pro, or to manage it.
 */
@Composable
private fun PlanRow(plan: Plan?, onOpenPro: () -> Unit) {
    val pro = plan == Plan.PRO
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        // Before the count there is no plan to name, and an empty text would be a blank stop for a screen reader.
        if (plan == null) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(
                stringResource(if (pro) R.string.settings_pro_plan else R.string.settings_free_plan),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        FilledTonalButton(onClick = onOpenPro) { Text(stringResource(if (pro) R.string.settings_manage_pro else R.string.common_get_pro)) }
    }
}

/**
 * Whether the phone's online speech service may hear the user when they say a change, which they
 * agreed to the first time, on a phone that cannot turn speech into text by itself. The whole row is
 * one switch for a screen reader.
 */
@Composable
private fun SpeechServiceSwitch(allowed: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = allowed, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_speech_service), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_speech_service_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = allowed, onCheckedChange = null)
    }
}

/** Keeps the answer about the speech service in the app's settings, where the assistant reads it before it listens. */
@Composable
private fun speechServiceConsent(): (Boolean) -> Unit {
    val store = koinInject<SettingsStore>()
    return remember(store) { store::allowSpeechService }
}

/** Read out by TalkBack when it appears or changes: the outcome of what the person just did. */
private val announced = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }

/** The calls left, such as "2.8 of 3 free calls left on this phone.", in words that follow the number the allowance holds. */
internal fun callsLeftText(calls: CallsLeft): UiText {
    val left = CallsLeft.text(calls.remaining)
    val limit = CallsLeft.text(calls.limit)
    val date = calls.comesBackOn
    val count = calls.limit.toInt()
    return if (date == null) {
        plural(R.plurals.calls_left_trial, count, left, limit)
    } else {
        plural(R.plurals.calls_left_month, count, left, limit, date)
    }
}

/** What each use of the AI takes of the calls. */
internal val CALL_COSTS: UiText =
    plural(R.plurals.call_costs, CallsLeft.READ.toInt(), CallsLeft.text(CallsLeft.READ), CallsLeft.text(CallsLeft.REVISION))
