package com.dantech.splitvibe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
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
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvCheckbox
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvMeter
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSwitch
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.common.openLink
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
 * The app's own settings: the Google account the user is signed in with, the AI calls left with the
 * plan they are counted against, the currency the app works in and the name the user goes by, and,
 * once the user let it hear them, the phone's online speech service. Once the user agreed to the AI,
 * the consent can be taken back here. The ways to get paid, the privacy policy and the terms of use
 * are a tap away, and signing out and deleting the account come last.
 */
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
    /** Takes back the user's consent to the AI; the consent screen asks again before the next call to the AI. */
    onWithdrawAiConsent: () -> Unit = aiConsentWithdrawal(),
) {
    var localCurrency by rememberSaveable { mutableStateOf(settings.localCurrency) }
    // Starts again from the stored name when that changes, as deleting the phone's data with the account does.
    var myName by rememberSaveable(settings.myName) { mutableStateOf(settings.myName) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val currencyIsValid = BillCurrency.isKnownCode(localCurrency)
    // Offered once the user let the speech service hear them, and for the rest of this visit, so that
    // a switch turned off can be turned on again. Without the consent, the mic asks before it listens.
    val speechServiceShown = rememberSaveable { settings.speechServiceAllowed }
    // Offered once the user agreed to the AI, and for the rest of this visit, which then says it was taken back.
    val aiConsentShown = rememberSaveable { settings.aiAllowed }
    val signedIn = account.session != null

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            SvTopBar(title = stringResource(R.string.settings_title), backLabel = stringResource(R.string.common_back), onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = SvSpacing.sm, bottom = SvSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = SvSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                if (signedIn) Profile(name = settings.myName, email = account.signedInAs)

                AccountCard(account = account, accountUi = accountUi, canSignIn = canSignIn, actions = actions)

                SvTextField(
                    value = localCurrency,
                    onValueChange = { localCurrency = it.uppercase().take(CURRENCY_CODE_LENGTH) },
                    label = stringResource(R.string.settings_local_currency),
                    modifier = Modifier.fillMaxWidth(),
                    isError = !currencyIsValid,
                    supportingText = stringResource(
                        if (currencyIsValid) R.string.settings_local_currency_hint else R.string.settings_local_currency_invalid,
                    ),
                    maxLength = CURRENCY_CODE_LENGTH,
                    // A code is no word, so the keyboard must not correct it into one.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                )

                SvTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    label = stringResource(R.string.settings_your_name),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = stringResource(R.string.settings_your_name_hint),
                    maxLength = SettingsStore.MAX_NAME,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )

                if (speechServiceShown) SpeechServiceSwitch(allowed = settings.speechServiceAllowed, onChange = onAllowSpeechService)

                if (aiConsentShown) AiConsent(allowed = settings.aiAllowed, onWithdraw = onWithdrawAiConsent)
            }

            // The rows of a group keep the margin of the screen themselves.
            Links(onOpenPaymentMethods = onOpenPaymentMethods, legalLinks = legalLinks)

            Column(
                modifier = Modifier.padding(horizontal = SvSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                SvPrimaryButton(
                    text = stringResource(R.string.common_save),
                    onClick = { onSave(localCurrency, myName) },
                    enabled = currencyIsValid,
                )

                // While the account works, or its sign-in has run out, the card of the calls says what can be done.
                if (signedIn && !account.signInExpired && !accountUi.working) {
                    SvQuietButton(
                        stringResource(R.string.settings_sign_out),
                        onClick = actions.onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SvTextAction(
                        stringResource(R.string.settings_delete_account),
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        destructive = true,
                    )
                }
            }
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

/** Who is signed in: the user's tile, with the initials of the name they go by, or of "You", and the Google account. */
@Composable
private fun Profile(name: String, email: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        SvAvatar(name.ifBlank { stringResource(R.string.common_you) }, size = PROFILE_SIZE)
        // It changes when a sign-in has worked, which TalkBack then reads out.
        Text(
            if (email == null) stringResource(R.string.settings_signed_in) else stringResource(R.string.settings_signed_in_as, email),
            modifier = Modifier
                .weight(1f)
                .then(announced),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The AI calls: the plan they are counted against with the way to Pro, the calls left in words and
 * as a meter, what each use of the AI takes, and how signing in stands, with the button that does
 * what is left to do: signing in, or signing out when the sign-in ran out.
 */
@Composable
private fun AccountCard(account: Account, accountUi: AccountUiState, canSignIn: Boolean, actions: AccountActions) {
    val signedIn = account.session != null
    val aiSetUp = account.ai != AiStatus.NOT_SET_UP
    val calls = account.callsLeft
    val email = account.signedInAs
    // Without a count, what stands in its place leads the card: AI that is not set up, or a sign-in that ran out.
    val leading = when {
        !aiSetUp -> account.ai.notice?.resolve()

        account.signInExpired -> if (email == null) {
            stringResource(R.string.settings_sign_in_expired)
        } else {
            stringResource(R.string.settings_sign_in_expired_as, email)
        }

        else -> null
    }
    // Signed out, a word below the count on how to get more calls; signed in, the row above the card says who.
    val note = when {
        !aiSetUp || account.signInExpired || signedIn -> null
        canSignIn -> stringResource(R.string.settings_sign_in_for_calls)
        else -> stringResource(R.string.settings_sign_in_not_set_up)
    }

    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            // Getting Pro is the card's main action only once nothing else is left to do in it.
            CallsHeader(
                plan = calls?.plan,
                offered = aiSetUp,
                proIsMain = signedIn && calls?.plan != Plan.PRO,
                onOpenPro = actions.onOpenPro,
            )
            calls?.let {
                Text(callsLeftText(it).resolve(), style = MaterialTheme.typography.bodyMediumEmphasized)
                SvMeter(value = it.remaining.toFloat(), max = it.limit.toFloat(), description = callsMeterText(it).resolve())
            }
            // These change when a sign-in or sign-out has worked, which TalkBack then reads out.
            leading?.let { Text(it, modifier = announced, style = MaterialTheme.typography.bodyMediumEmphasized) }
            if (aiSetUp) {
                Text(callCosts().resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            note?.let {
                Text(
                    it,
                    modifier = announced,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                accountUi.working -> Row(
                    modifier = announced,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
                ) {
                    SvLoadingIndicator(modifier = Modifier.size(WORKING_SIZE))
                    Text(stringResource(R.string.common_working), style = MaterialTheme.typography.bodyMedium)
                }

                // Deleting the account needs a session the proxy accepts, so it waits for a new sign-in.
                account.signInExpired -> Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                    if (canSignIn && aiSetUp) {
                        SvPrimaryButton(stringResource(R.string.common_sign_in_with_google), onClick = actions.onSignIn)
                    }
                    SvQuietButton(
                        stringResource(R.string.settings_sign_out),
                        onClick = actions.onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                !signedIn && canSignIn && aiSetUp -> {
                    SvPrimaryButton(stringResource(R.string.common_sign_in_with_google), onClick = actions.onSignIn)
                }
            }

            accountUi.message?.let { SvMessageCard(text = it.resolve(), isError = accountUi.isError, modifier = announced) }
        }
    }
}

/**
 * The title of the card of the calls, the plan they are counted against, Free or Pro, once the proxy
 * has counted them, and, where the AI is set up, the way to the Pro screen: to get Pro, or to manage
 * it. With [proIsMain] that way is the card's main action.
 */
@Composable
private fun CallsHeader(plan: Plan?, offered: Boolean, proIsMain: Boolean, onOpenPro: () -> Unit) {
    val pro = plan == Plan.PRO
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
        // With large text the plan goes below the title, and the way to Pro keeps its place at the end.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_ai_calls),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            // Before the count there is no plan to name, and an empty text would be a blank stop for a screen reader.
            if (offered && plan != null) {
                SvTag(
                    stringResource(if (pro) R.string.settings_pro_plan else R.string.settings_free_plan),
                    kind = if (pro) SvTagKind.Accent else SvTagKind.Quiet,
                )
            }
        }
        if (offered) {
            val label = stringResource(if (pro) R.string.settings_manage_pro else R.string.common_get_pro)
            if (proIsMain) SvPrimaryButton(label, onClick = onOpenPro, compact = true) else SvQuietButton(label, onClick = onOpenPro)
        }
    }
}

/**
 * The way to the ways to get paid, and the privacy policy and the terms of use where this build has
 * them, which open in the browser, as one card of rows; or a word when no app on the phone opens web
 * pages.
 */
@Composable
private fun Links(onOpenPaymentMethods: () -> Unit, legalLinks: LegalLinks?) {
    val context = LocalContext.current
    var cannotOpen by rememberSaveable { mutableStateOf(false) }
    val pages = legalLinks?.let {
        listOf(R.string.legal_privacy_policy to it.privacyPolicy, R.string.legal_terms_of_use to it.terms)
    }.orEmpty()
    val rows = pages.size + 1
    Column {
        SvListRow(
            position = SvRowPosition.of(0, rows),
            label = stringResource(R.string.settings_payment_methods),
            onClickLabel = stringResource(R.string.common_open),
            onClick = onOpenPaymentMethods,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_payment_methods), style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(
                    stringResource(R.string.settings_payment_methods_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
        pages.forEachIndexed { index, (label, url) ->
            val name = stringResource(label)
            SvListRow(
                position = SvRowPosition.of(index + 1, rows),
                label = name,
                onClickLabel = stringResource(R.string.common_open),
                onClick = { cannotOpen = !openLink(context, url) },
            ) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        if (cannotOpen) {
            // Read out by TalkBack as it appears, since nothing else happens after the tap.
            Text(
                stringResource(R.string.legal_cannot_open),
                modifier = Modifier
                    .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * What deleting the account does and does not do, and whether to delete the bills, people and payment
 * methods on this phone with it, which is off each time the dialog opens. Deleting cannot be undone,
 * so the button that goes ahead is red.
 */
@Composable
private fun DeleteAccountDialog(onDelete: (withPhoneData: Boolean) -> Unit, onDismiss: () -> Unit) {
    var withPhoneData by rememberSaveable { mutableStateOf(false) }
    SvDialog(
        title = stringResource(R.string.settings_delete_title),
        text = stringResource(R.string.settings_delete_text),
        confirmLabel = stringResource(R.string.common_delete),
        onConfirm = { onDelete(withPhoneData) },
        dismissLabel = stringResource(R.string.common_cancel),
        onDismiss = onDismiss,
        irreversible = true,
    ) {
        // The whole row is one tick box for a screen reader.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = withPhoneData, role = Role.Checkbox, onValueChange = { withPhoneData = it })
                .heightIn(min = TOUCH_TARGET),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            SvCheckbox(checked = withPhoneData, onCheckedChange = null)
            Text(stringResource(R.string.settings_delete_with_phone_data), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Whether the phone's online speech service may hear the user when they say a change, which they
 * agreed to the first time, on a phone that cannot turn speech into text by itself. The whole row is
 * one switch for a screen reader.
 */
@Composable
private fun SpeechServiceSwitch(allowed: Boolean, onChange: (Boolean) -> Unit) {
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = allowed, role = Role.Switch, onValueChange = onChange)
                .padding(SvSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                Text(stringResource(R.string.settings_speech_service), style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(
                    stringResource(R.string.settings_speech_service_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SvSwitch(checked = allowed, onCheckedChange = null)
        }
    }
}

/**
 * The user's consent to the AI, and the way to take it back. Taking it back only takes effect here:
 * agreeing again is done on the consent screen, which shows before the next call to the AI.
 */
@Composable
private fun AiConsent(allowed: Boolean, onWithdraw: () -> Unit) {
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            Text(stringResource(R.string.settings_ai_consent), style = MaterialTheme.typography.bodyMediumEmphasized)
            Text(
                stringResource(if (allowed) R.string.settings_ai_consent_given else R.string.settings_ai_consent_withdrawn),
                modifier = announced,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (allowed) {
                SvQuietButton(
                    stringResource(R.string.settings_ai_consent_withdraw),
                    onClick = onWithdraw,
                    modifier = Modifier.padding(top = SvSpacing.sm),
                )
            }
        }
    }
}

/** Takes the consent to the AI back in the app's settings, which both AI actions read before they call it. */
@Composable
private fun aiConsentWithdrawal(): () -> Unit {
    val store = koinInject<SettingsStore>()
    return remember(store) { { store.allowAi(false) } }
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

/**
 * What the meter of [calls] says to a screen reader, such as "9 of 10 calls left": the sentence of
 * [callsLeftText] above it says the same in full, with the day the calls come back.
 */
internal fun callsMeterText(calls: CallsLeft): UiText {
    val left = CallsLeft.text(calls.remaining)
    val limit = CallsLeft.text(calls.limit)
    val count = calls.limit.toInt()
    val id = if (calls.comesBackOn == null) R.plurals.settings_calls_meter_trial else R.plurals.settings_calls_meter_month
    return plural(id, count, left, limit)
}

/**
 * What each use of the AI takes of the calls, built each time it is shown, so its numbers have the
 * marks of the region the phone is set to then.
 */
internal fun callCosts(): UiText =
    plural(R.plurals.call_costs, CallsLeft.READ.toInt(), CallsLeft.text(CallsLeft.READ), CallsLeft.text(CallsLeft.REVISION))

/** A currency code has three letters, such as VND. */
private const val CURRENCY_CODE_LENGTH = 3

private val PROFILE_SIZE = 56.dp
private val WORKING_SIZE = 40.dp

/** The smallest size a finger can reliably hit. */
private val TOUCH_TARGET = 48.dp
