package com.dantech.splitvibe.ui.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.billing.ProPlan
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroKind
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvMeter
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRadio
import com.dantech.splitvibe.designsystem.components.SvSmallAction
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.common.LegalLinksRow
import com.dantech.splitvibe.ui.common.openLink
import com.dantech.splitvibe.ui.settings.callsLeftText
import com.dantech.splitvibe.ui.settings.callsMeterText

/** What the Pro screen asks the app to do. */
class ProActions(
    val onSignIn: () -> Unit,
    val onSelect: (ProPeriod) -> Unit,
    val onBuy: () -> Unit,
    val onSwitch: (ProPlan) -> Unit,
    val onRestore: () -> Unit,
    val onRetry: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * Pro: what it gives, that everything else stays free, the plans with Google Play's prices and the
 * terms of any offer, how a plan renews and where to cancel it, and the calls left. A Pro member sees
 * their plan, when it renews or ends, a payment Google Play could not take, the calls left as a meter
 * too, and the switch to the other plan; so does someone whose subscription Google Play is still
 * renewing. Signing in comes first, as only a signed-in account buys.
 */
@Composable
fun ProScreen(
    state: ProUiState,
    actions: ProActions,
    /** The privacy policy and the terms of use, beside the terms of renewal; none in a build without the proxy's address. */
    legalLinks: LegalLinks? = null,
) {
    val context = LocalContext.current
    // Set when no app on the phone opens Google Play's subscriptions.
    var cannotOpen by rememberSaveable { mutableStateOf(false) }
    val manage = { cannotOpen = !openLink(context, state.manageUrl) }
    Scaffold(
        topBar = {
            SvTopBar(title = stringResource(R.string.pro_title), backLabel = stringResource(R.string.common_back), onBack = actions.onBack)
        },
        bottomBar = { BottomPart(state, actions) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            when {
                !state.sold -> SvMessageCard(text = stringResource(R.string.pro_not_sold), isError = false)
                state.subscribed -> MemberCard(state, onManage = manage)
                else -> BenefitCard()
            }
            state.callsLeft?.let { calls ->
                Text(callsLeftText(calls).resolve(), style = MaterialTheme.typography.bodyMediumEmphasized)
                // Someone with Pro follows how much of it is left; someone choosing a plan only needs the words.
                if (state.subscribed) {
                    SvMeter(value = calls.remaining.toFloat(), max = calls.limit.toFloat(), description = callsMeterText(calls).resolve())
                }
            }
            if (state.sold) {
                Offer(state, actions)
                Terms(state, actions, legalLinks, onManage = manage, cannotOpen = cannotOpen)
            }
        }
    }
}

/** What Pro gives, and that Split Vibe works without it. */
@Composable
private fun BenefitCard() = SvHero {
    Text(
        plural(R.plurals.pro_calls_a_month, CallsLeft.PRO_MONTHLY).resolve(),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.displayMedium,
    )
    Text(plural(R.plurals.pro_benefit, CallsLeft.PRO_MONTHLY, CallsLeft.PRO_MONTHLY, CallsLeft.FREE_MONTHLY).resolve())
    Text(stringResource(R.string.pro_everything_else_free))
}

/**
 * The Pro the person has, on the lime block of good news: its plan and price, when it renews or ends,
 * and a payment Google Play could not take, with the way to Google Play to fix it. A subscription that
 * Google Play is still renewing shows here too, in place of the offer to buy, although RevenueCat does
 * not report it active.
 */
@Composable
private fun MemberCard(state: ProUiState, onManage: () -> Unit) {
    val membership = state.membership
    SvHero(kind = SvHeroKind.Accent) {
        Text(
            stringResource(if (state.member) R.string.pro_you_have_pro else R.string.pro_your_subscription),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displayMedium,
        )
        if (state.member) Text(plural(R.plurals.pro_member_calls, CallsLeft.PRO_MONTHLY).resolve())
        val plan = state.currentPlan
        val renewal = membership?.let { renewalText(it) }
        if (plan != null || renewal != null) {
            Column {
                plan?.let {
                    Text(
                        stringResource(R.string.pro_member_plan, nameOf(it.period).resolve(), priceText(it).resolve()),
                        style = MaterialTheme.typography.bodyMediumEmphasized,
                    )
                }
                renewal?.let { Text(it.resolve()) }
            }
        }
    }
    if (membership?.billingIssue == true) {
        SvMessageCard(
            text = stringResource(R.string.pro_billing_issue),
            isError = true,
            action = stringResource(R.string.pro_open_google_play),
            onAction = onManage,
        )
    }
}

/** What there is to buy: signing in first, then the plans, or for a Pro member the switch to the other plan. */
@Composable
private fun Offer(state: ProUiState, actions: ProActions) {
    val loadError = state.loadError
    val switchTarget = state.switchTarget
    when {
        !state.signedIn -> SvMessageCard(
            text = if (state.signInExpired) {
                stringResource(R.string.pro_sign_in_again)
            } else {
                plural(R.plurals.pro_sign_in_first, CallsLeft.FREE_MONTHLY).resolve()
            },
            isError = false,
        )

        state.loading -> Row(
            modifier = announced,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            SvLoadingIndicator(modifier = Modifier.size(WORKING_SIZE))
            Text(stringResource(R.string.pro_loading), style = MaterialTheme.typography.bodyMedium)
        }

        loadError != null -> SvMessageCard(
            text = loadError.resolve(),
            isError = true,
            action = stringResource(R.string.common_try_again),
            onAction = actions.onRetry,
        )

        // Neither a plan to buy nor a switch, while Split Vibe's server could not count them.
        !state.onSale -> SvMessageCard(text = NOTHING_ON_SALE.resolve(), isError = false)

        state.subscribed -> if (switchTarget != null) SwitchCard(switchTarget, enabled = state.working == null, onSwitch = actions.onSwitch)

        state.plans.isEmpty() -> SvMessageCard(text = NOTHING_ON_SALE.resolve(), isError = false)

        else -> Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.period == state.chosen?.period,
                    enabled = state.working == null,
                    onSelect = { actions.onSelect(plan.period) },
                )
            }
        }
    }
}

/**
 * One plan to pick, as a card of its own, outlined while it is the chosen one: its name, Google
 * Play's price for each period, and the terms of its offer. The whole card is one radio button for a
 * screen reader, which reads all of it.
 */
@Composable
private fun PlanCard(plan: ProPlan, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val name = nameOf(plan.period).resolve()
    val price = priceText(plan).resolve()
    val offer = offerText(plan)?.resolve()
    val shape = MaterialTheme.shapes.large
    SvCard(
        // On the card's own element, so that each plan is one of the group of plans to a screen
        // reader, which then says how many there are and which of them this is.
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = listOfNotNull(name, price, offer).joinToString(", ") },
        shape = shape,
        outlined = selected,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(price, style = MaterialTheme.typography.amountMedium)
                offer?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            SvRadio(selected = selected, onClick = null, enabled = enabled)
        }
    }
}

/** The other plan, which a Pro member can switch to at once. */
@Composable
private fun SwitchCard(target: ProPlan, enabled: Boolean, onSwitch: (ProPlan) -> Unit) {
    val name = nameInSentence(target.period).resolve()
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            Text(
                stringResource(R.string.pro_switch_title, name),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(priceText(target).resolve(), style = MaterialTheme.typography.amountMedium)
            offerText(target)?.let { Text(it.resolve(), style = MaterialTheme.typography.bodySmall) }
            Text(
                stringResource(R.string.pro_switch_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(SvSpacing.xs))
            SvSmallAction(stringResource(R.string.pro_switch_button, name), onClick = { onSwitch(target) }, enabled = enabled)
        }
    }
}

/** How a plan renews and where to cancel it, the privacy policy and the terms of use, and restoring purchases made before. */
@Composable
private fun Terms(state: ProUiState, actions: ProActions, legalLinks: LegalLinks?, onManage: () -> Unit, cannotOpen: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
        Text(
            stringResource(R.string.pro_renewal_terms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        legalLinks?.let { LegalLinksRow(it) }
        SvQuietButton(stringResource(R.string.pro_manage), onClick = onManage, modifier = Modifier.fillMaxWidth())
        if (state.signedIn) {
            SvTextAction(
                stringResource(R.string.pro_restore),
                onClick = actions.onRestore,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = state.working == null,
            )
        }
        if (cannotOpen) {
            Text(
                stringResource(R.string.pro_cannot_open_play),
                modifier = announced,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The bar at the bottom: how the last action ended, and the main action, or what the screen waits for. */
@Composable
private fun BottomPart(state: ProUiState, actions: ProActions) {
    val chosen = state.chosen
    val main = when {
        state.working != null -> MainAction.WORKING
        !state.sold || state.subscribed -> MainAction.NONE
        !state.signedIn -> MainAction.SIGN_IN
        chosen != null && state.onSale && !state.loading && state.loadError == null -> MainAction.BUY
        else -> MainAction.NONE
    }
    val message = state.message
    if (main == MainAction.NONE && message == null) return
    SvBottomBar {
        message?.let {
            SvMessageCard(
                text = it.text.resolve(),
                isError = it.isError,
                action = stringResource(R.string.common_ok),
                onAction = actions.onDismissMessage,
                modifier = announced,
            )
            if (main != MainAction.NONE) Spacer(Modifier.height(SvSpacing.md))
        }
        when (main) {
            MainAction.WORKING -> Row(
                modifier = announced,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                SvLoadingIndicator(modifier = Modifier.size(WORKING_SIZE))
                workingText(state.working)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }

            MainAction.SIGN_IN -> SvPrimaryButton(stringResource(R.string.common_sign_in_with_google), onClick = actions.onSignIn)

            MainAction.BUY -> if (chosen != null) {
                SvPrimaryButton(stringResource(R.string.pro_subscribe, priceText(chosen).resolve()), onClick = actions.onBuy)
            }

            MainAction.NONE -> Unit
        }
    }
}

private enum class MainAction { WORKING, SIGN_IN, BUY, NONE }

private fun workingText(work: ProWork?): Int? = when (work) {
    ProWork.SIGNING_IN -> R.string.pro_signing_in
    ProWork.BUYING, ProWork.SWITCHING -> R.string.pro_waiting_for_play
    ProWork.RESTORING -> R.string.pro_restoring
    null -> null
}

/** Read out by TalkBack when it appears or changes: the outcome of what the person just did. */
private val announced = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }

private val WORKING_SIZE = 40.dp
