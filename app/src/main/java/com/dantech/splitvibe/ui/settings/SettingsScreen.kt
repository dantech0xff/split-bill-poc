package com.dantech.splitvibe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
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
import com.dantech.splitvibe.ui.paywall.GET_PRO
import org.koin.compose.koinInject

/** What the account part of Settings can do. */
class AccountActions(val onSignIn: () -> Unit, val onSignOut: () -> Unit, val onDeleteAccount: () -> Unit, val onOpenPro: () -> Unit)

/**
 * The app's own settings: the AI calls left with the plan they are counted against and the Google
 * account behind them, the currency the app works in, and, once the user let it hear them, the
 * phone's online speech service.
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
    /** Takes back, or gives again, the user's consent to the phone's online speech service; it takes effect at once. */
    onAllowSpeechService: (Boolean) -> Unit = speechServiceConsent(),
) {
    var localCurrency by rememberSaveable { mutableStateOf(settings.localCurrency) }
    var myName by rememberSaveable { mutableStateOf(settings.myName) }
    val currencyIsValid = BillCurrency.isKnownCode(localCurrency)
    // Offered once the user let the speech service hear them, and for the rest of this visit, so that
    // a switch turned off can be turned on again. Without the consent, the mic asks before it listens.
    val speechServiceShown = rememberSaveable { settings.speechServiceAllowed }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                label = { Text("Local currency") },
                singleLine = true,
                isError = !currencyIsValid,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                supportingText = {
                    Text(
                        if (currencyIsValid) {
                            "The currency this app works in, for example VND. Used when a bill does not show its currency."
                        } else {
                            "Enter a three-letter currency code such as VND, USD or EUR."
                        },
                    )
                },
            )

            OutlinedTextField(
                value = myName,
                onValueChange = { myName = it.take(SettingsStore.MAX_NAME) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                supportingText = { Text("How you show in groups and on the requests you send.") },
            )

            if (speechServiceShown) SpeechServiceSwitch(allowed = settings.speechServiceAllowed, onChange = onAllowSpeechService)

            SvListRow(
                position = SvRowPosition.Single,
                label = "Ways to get paid",
                onClickLabel = "Open",
                onClick = onOpenPaymentMethods,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ways to get paid", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Bank accounts, PayPal, UPI or links that your requests carry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }

            SvPrimaryButton("Save", onClick = { onSave(localCurrency, myName) }, enabled = currencyIsValid)
        }
    }
}

@Composable
private fun AccountCard(account: Account, accountUi: AccountUiState, canSignIn: Boolean, actions: AccountActions) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val signedIn = account.session != null

    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            Text("AI calls", style = MaterialTheme.typography.titleMedium)

            if (account.ai != AiStatus.NOT_SET_UP) PlanRow(account.callsLeft?.plan, onOpenPro = actions.onOpenPro)
            account.callsLeft?.let { Text(callsLeftText(it), style = MaterialTheme.typography.bodyLarge) }
            if (account.ai != AiStatus.NOT_SET_UP) {
                Text(CALL_COSTS, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val about = when {
                account.ai == AiStatus.NOT_SET_UP -> account.ai.notice

                account.signInExpired ->
                    "Your sign-in${account.signedInAs?.let { " as $it" }.orEmpty()} has run out. Sign in again to use your monthly calls."

                signedIn -> "Signed in with Google as ${account.signedInAs ?: "your Google account"}."

                canSignIn -> "Sign in with Google to get calls every month."

                else -> "Signing in is not set up in this build of the app."
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
                    Text("Working…", style = MaterialTheme.typography.bodyMedium)
                }

                // Deleting the account needs a session the proxy accepts, so it waits for a new sign-in.
                account.signInExpired -> Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    if (canSignIn && account.ai != AiStatus.NOT_SET_UP) SvPrimaryButton("Sign in with Google", onClick = actions.onSignIn)
                    OutlinedButton(onClick = actions.onSignOut) { Text("Sign out") }
                }

                signedIn -> FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    OutlinedButton(onClick = actions.onSignOut) { Text("Sign out") }
                    TextButton(
                        onClick = { confirmingDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete account")
                    }
                }

                canSignIn && account.ai != AiStatus.NOT_SET_UP -> SvPrimaryButton("Sign in with Google", onClick = actions.onSignIn)
            }

            accountUi.message?.let { SvMessageCard(text = it, isError = accountUi.isError, modifier = announced) }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete your account?") },
            text = { Text(DELETE_ACCOUNT_TEXT) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        actions.onDeleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * The plan the calls are counted against, Free or Pro, once the proxy has counted them, and the way
 * to the Pro screen: to get Pro, or to manage it.
 */
@Composable
private fun PlanRow(plan: Plan?, onOpenPro: () -> Unit) {
    val pro = plan == Plan.PRO
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        Text(
            text = when {
                plan == null -> ""
                pro -> "Pro plan"
                else -> "Free plan"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        FilledTonalButton(onClick = onOpenPro) { Text(if (pro) "Manage Pro" else GET_PRO) }
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
            Text("Use the phone's online speech service", style = MaterialTheme.typography.bodyLarge)
            Text(
                "For saying a change when the phone cannot turn speech into text by itself. It may send what you say over the internet.",
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

/** The calls left, such as "2.8 of 3 free calls left on this phone." */
internal fun callsLeftText(calls: CallsLeft): String {
    val left = "${CallsLeft.text(calls.remaining)} of ${CallsLeft.text(calls.limit)}"
    val date = calls.comesBackOn
    return if (date == null) "$left free calls left on this phone." else "$left calls left this month. They come back on $date."
}

/** What each use of the AI takes of the calls. */
internal val CALL_COSTS = "Reading a bill takes ${CallsLeft.text(CallsLeft.READ)} call, " +
    "and changing a split by a sentence takes ${CallsLeft.text(CallsLeft.REVISION)}."

/** What deleting the account does and does not do, before the person confirms it. */
internal const val DELETE_ACCOUNT_TEXT = "Split Vibe's server deletes what it keeps about your Google account: the count of " +
    "your calls and your plan. Bills on this phone stay as they are. Deleting the account does not cancel Pro, so " +
    "if you have it, cancel it in Google Play first."
