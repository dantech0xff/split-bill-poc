package com.dantech.splitvibe.ui.paywall

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.billing.ProPlan
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.ui.settings.callsLeftText

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
 * their plan, when it renews or ends, a payment Google Play could not take, and the switch to the
 * other plan. Signing in comes first, as only a signed-in account buys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(state: ProUiState, actions: ProActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Vibe Pro") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = { BottomPart(state, actions) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(SvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
        ) {
            when {
                !state.sold -> SvMessageCard(text = NOT_SOLD, isError = false)
                state.member -> MemberCard(state)
                else -> BenefitCard()
            }
            state.callsLeft?.let { Text(callsLeftText(it), style = MaterialTheme.typography.bodyLarge) }
            if (state.sold) {
                Offer(state, actions)
                Terms(state, actions)
            }
        }
    }
}

/** What Pro gives, and that Split Vibe works without it. */
@Composable
private fun BenefitCard() = HeroCard {
    Text(
        "${CallsLeft.PRO_MONTHLY} AI calls a month",
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    Text(
        "Pro gives you ${CallsLeft.PRO_MONTHLY} AI calls a month instead of ${CallsLeft.FREE_MONTHLY}, for reading bills and " +
            "changing splits by a sentence.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(EVERYTHING_ELSE_FREE, style = MaterialTheme.typography.bodyMedium)
}

/** The Pro the person has: its plan and price, when it renews or ends, and a payment Google Play could not take. */
@Composable
private fun MemberCard(state: ProUiState) {
    val membership = state.membership
    HeroCard {
        Text("You have Pro", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            "${CallsLeft.PRO_MONTHLY} AI calls a month. Everything else in Split Vibe stays free.",
            style = MaterialTheme.typography.bodyMedium,
        )
        state.currentPlan?.let { plan ->
            Text("${nameOf(plan.period)} plan, ${priceText(plan)}", style = MaterialTheme.typography.amountMedium)
        }
        membership?.let { Text(renewalText(it), style = MaterialTheme.typography.bodyMedium) }
    }
    if (membership?.billingIssue == true) SvMessageCard(text = BILLING_ISSUE, isError = true)
}

/** The accent card the screen is about. */
@Composable
private fun HeroCard(content: @Composable ColumnScope.() -> Unit) {
    SvCard(modifier = Modifier.fillMaxWidth(), elevation = SvElevation.Level2, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.padding(SvSpacing.xl), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm), content = content)
    }
}

/** What there is to buy: signing in first, then the plans, or for a Pro member the switch to the other plan. */
@Composable
private fun Offer(state: ProUiState, actions: ProActions) {
    val loadError = state.loadError
    val switchTarget = state.switchTarget
    when {
        !state.signedIn -> SvMessageCard(text = if (state.signInExpired) SIGN_IN_AGAIN else SIGN_IN_FIRST, isError = false)

        state.loading -> Row(
            modifier = announced,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            SvLoadingIndicator(modifier = Modifier.size(40.dp))
            Text("Loading the plans…", style = MaterialTheme.typography.bodyMedium)
        }

        loadError != null -> SvMessageCard(text = loadError, isError = true, action = "Try again", onAction = actions.onRetry)

        state.member -> if (switchTarget != null) SwitchCard(switchTarget, enabled = state.working == null, onSwitch = actions.onSwitch)

        state.plans.isEmpty() -> SvMessageCard(text = NOTHING_ON_SALE, isError = false)

        else -> SvCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(vertical = SvSpacing.sm)
                    .selectableGroup(),
            ) {
                state.plans.forEach { plan ->
                    PlanRow(
                        plan = plan,
                        selected = plan.period == state.chosen?.period,
                        enabled = state.working == null,
                        onSelect = { actions.onSelect(plan.period) },
                    )
                }
            }
        }
    }
}

/**
 * One plan to pick: its name, Google Play's price for each period, and the terms of its offer. The
 * whole row is one radio button for a screen reader, which reads all of it.
 */
@Composable
private fun PlanRow(plan: ProPlan, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val price = priceText(plan)
    val offer = offerText(plan)
    ListItem(
        headlineContent = { Text(nameOf(plan.period), style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column {
                Text(price, style = MaterialTheme.typography.amountMedium)
                offer?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = listOfNotNull(nameOf(plan.period), price, offer).joinToString(", ") }
            .padding(horizontal = SvSpacing.sm),
    )
}

/** The other plan, which a Pro member can switch to at once. */
@Composable
private fun SwitchCard(target: ProPlan, enabled: Boolean, onSwitch: (ProPlan) -> Unit) {
    val name = nameOf(target.period).lowercase()
    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Text("Switch to the $name plan", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text(priceText(target), style = MaterialTheme.typography.amountMedium)
            offerText(target)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(SWITCH_TERMS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onSwitch(target) }, enabled = enabled) { Text("Switch to $name") }
        }
    }
}

/** How a plan renews and where to cancel it, and restoring purchases made before. */
@Composable
private fun Terms(state: ProUiState, actions: ProActions) {
    val context = LocalContext.current
    var cannotOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
        Text(RENEWAL_TERMS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // A flow row: with large text the second button moves to its own line instead of wrapping its label.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            OutlinedButton(onClick = { cannotOpen = !open(context, state.manageUrl) }) { Text("Manage subscriptions in Google Play") }
            if (state.signedIn) TextButton(onClick = actions.onRestore, enabled = state.working == null) { Text("Restore purchases") }
        }
        if (cannotOpen) {
            Text(
                "Google Play could not be opened on this phone. Its Payments and subscriptions page manages Pro.",
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
        !state.sold || state.member -> MainAction.NONE
        !state.signedIn -> MainAction.SIGN_IN
        chosen != null && !state.loading && state.loadError == null -> MainAction.BUY
        else -> MainAction.NONE
    }
    val message = state.message
    if (main == MainAction.NONE && message == null) return
    SvBottomBar {
        message?.let {
            SvMessageCard(text = it.text, isError = it.isError, action = "OK", onAction = actions.onDismissMessage, modifier = announced)
            if (main != MainAction.NONE) Spacer(Modifier.height(SvSpacing.md))
        }
        when (main) {
            MainAction.WORKING -> Row(
                modifier = announced,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                SvLoadingIndicator(modifier = Modifier.size(40.dp))
                Text(workingText(state.working), style = MaterialTheme.typography.bodyMedium)
            }

            MainAction.SIGN_IN -> SvPrimaryButton("Sign in with Google", onClick = actions.onSignIn)

            MainAction.BUY -> if (chosen != null) SvPrimaryButton("Subscribe for ${priceText(chosen)}", onClick = actions.onBuy)

            MainAction.NONE -> Unit
        }
    }
}

private enum class MainAction { WORKING, SIGN_IN, BUY, NONE }

private fun workingText(work: ProWork?): String = when (work) {
    ProWork.SIGNING_IN -> "Signing in…"
    ProWork.BUYING, ProWork.SWITCHING -> "Waiting for Google Play…"
    ProWork.RESTORING -> "Restoring your purchases…"
    null -> ""
}

/** Opens [url], in Google Play when it is installed; false when no app on the phone opens it. */
private fun open(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** Read out by TalkBack when it appears or changes: the outcome of what the person just did. */
private val announced = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }

private const val NOT_SOLD = "Pro is not available in this build of the app. Everything else works as it does."
private const val EVERYTHING_ELSE_FREE = "Everything else stays free: typing bills in by hand, groups, insights, exports and " +
    "recurring bills. Split Vibe works without a subscription."
private val SIGN_IN_FIRST = "Sign in with Google to buy Pro, so that it stays with your account. Signing in alone gives you " +
    "${CallsLeft.FREE_MONTHLY} AI calls a month, free."
private const val SIGN_IN_AGAIN = "Your sign-in has run out. Sign in with Google again to buy or manage Pro."
private const val NOTHING_ON_SALE = "Pro cannot be bought right now. Please try again later."
private const val RENEWAL_TERMS = "A plan renews automatically at the end of each month or year, at its price shown here, until " +
    "you cancel it. You can cancel any time in Google Play, and Pro then lasts until the end of the period you paid for."
private const val SWITCH_TERMS = "The new plan starts now, and Google Play charges its price from your next renewal."
private const val BILLING_ISSUE = "Google Play could not take your last payment. Update your payment method in Google Play to keep Pro."
