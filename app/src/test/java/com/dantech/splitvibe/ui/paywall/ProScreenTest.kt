package com.dantech.splitvibe.ui.paywall

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.billing.OfferPhase
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.billing.ProPlan
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.ui.common.LegalLinks
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What the Pro screen offers in each state, and how a screen reader reads it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h1200dp-xhdpi")
class ProScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(ProUiState(sold = true))
    private val done = mutableListOf<String>()
    private val actions = ProActions(
        onSignIn = { done += "sign in" },
        onSelect = { state = state.copy(selected = it) },
        onBuy = { done += "buy ${state.chosen?.period}" },
        onSwitch = { plan: ProPlan -> done += "switch to ${plan.period}" },
        onRestore = { done += "restore" },
        onRetry = { done += "retry" },
        onDismissMessage = { done += "dismiss" },
        onBack = {},
    )

    @Test
    fun `signed out, the main action is signing in, which alone gives 10 calls a month`() {
        show(ProUiState(sold = true, callsLeft = TRIAL))

        compose.onNode(hasText("Sign in with Google") and hasClickAction()).performClick()

        compose.onNodeWithText("Signing in alone gives you 10 AI calls a month", substring = true).assertExists()
        compose.onNodeWithText("Split Vibe works without a subscription", substring = true).assertExists()
        compose.onNodeWithText("Subscribe", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Restore purchases").assertDoesNotExist()
        assertEquals(listOf("sign in"), done)
    }

    @Test
    fun `signed in, the plans are radio buttons with Google Play's prices, and the main button buys the one picked`() {
        show(SIGNED_IN)

        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(2)
        val monthly = compose.onNode(hasContentDescription("Monthly, $3.99 a month"))
        val yearly = compose.onNode(hasContentDescription("Yearly, $29.99 a year"))
        monthly.assertIsSelected()
        yearly.assertIsNotSelected()

        yearly.performClick()
        yearly.assertIsSelected()
        compose.onNode(hasText("Subscribe for $29.99 a year") and hasClickAction()).performClick()

        compose.onNodeWithText("renews automatically", substring = true).assertExists()
        compose.onNode(hasText("Manage subscriptions in Google Play") and hasClickAction()).assertExists()
        compose.onNode(hasText("Restore purchases") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("buy YEARLY", "restore"), done)
    }

    @Test
    fun `the plans are one group of radio buttons to a screen reader, each of them a direct part of it`() {
        show(SIGNED_IN)

        val plans = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, plans.size)
        // TalkBack counts the choices of a group, and says which one is which, only of its direct parts.
        for (plan in plans) {
            val group = plan.parent?.config
            assertTrue("${plan.config[SemanticsProperties.ContentDescription]} is not a part of the group", group?.contains(GROUP) == true)
        }
    }

    @Test
    fun `a plan with an offer says its terms, and a screen reader reads them with the plan`() {
        val trial = FakeBilling.MONTHLY.copy(offer = listOf(OfferPhase(price = null, FakeBilling.MONTHLY.billingPeriod, cycles = 1)))
        show(SIGNED_IN.copy(plans = listOf(trial, FakeBilling.YEARLY)))

        compose.onNodeWithText("Free for a month, then $3.99 a month.").assertExists()
        compose.onNode(hasContentDescription("Monthly, $3.99 a month, Free for a month, then $3.99 a month.")).assertExists()
    }

    @Test
    fun `a Pro member sees the plan, the renewal, a payment that failed, and the switch to the other plan`() {
        show(MEMBER.copy(membership = FakeBilling.active(billingIssue = true)))

        compose.onNode(hasText("You have Pro") and isHeading()).assertExists()
        compose.onNodeWithText("Monthly plan, $3.99 a month").assertExists()
        compose.onNodeWithText("Renews on December 17, 2026.").assertExists()
        compose.onNodeWithText("Google Play could not take your last payment", substring = true).assertExists()
        compose.onNodeWithText("Subscribe", substring = true).assertDoesNotExist()

        compose.onNode(hasText("Switch to yearly") and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf("switch to YEARLY"), done)
    }

    @Test
    fun `a subscription Google Play is still renewing shows as the person's, never as one to buy`() {
        show(SIGNED_IN.copy(membership = FakeBilling.active(willRenew = true).copy(active = false)))

        compose.onNode(hasText("Your Pro subscription") and isHeading()).assertExists()
        compose.onNodeWithText("Monthly plan, $3.99 a month").assertExists()
        compose.onNodeWithText("Renewal pending: Google Play is renewing your subscription", substring = true).assertExists()
        assertNoOffer()
    }

    @Test
    fun `a payment Google Play could not take shows the notice, which opens the subscription in Google Play`() {
        show(SIGNED_IN.copy(membership = FakeBilling.active(willRenew = false, billingIssue = true).copy(active = false)))

        compose.onNodeWithText("Google Play could not take your last payment", substring = true).assertExists()
        compose.onNode(hasText("Open Google Play") and hasClickAction()).performClick()

        val opened = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://play.google.com/store/account/subscriptions?sku=pro&package=${BuildConfig.APPLICATION_ID}", opened.dataString)
        assertNoOffer()
    }

    @Test
    fun `the proxy's Pro shows as Pro although RevenueCat reports the subscription ended`() {
        show(MEMBER.copy(membership = FakeBilling.active(willRenew = false).copy(active = false)))

        compose.onNode(hasText("You have Pro") and isHeading()).assertExists()
        compose.onNodeWithText("Ends on", substring = true).assertDoesNotExist()
        assertNoOffer()
    }

    @Test
    fun `while Pro is not on sale there is no plan to buy and no switch, but restoring and managing stay`() {
        show(SIGNED_IN.copy(onSale = false))

        compose.onNodeWithText("Pro cannot be bought right now", substring = true).assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(0)
        compose.onNodeWithText("Subscribe", substring = true).assertDoesNotExist()
        compose.onNode(hasText("Manage subscriptions in Google Play") and hasClickAction()).assertExists()
        compose.onNode(hasText("Restore purchases") and hasClickAction()).performScrollTo().performClick()

        state = MEMBER.copy(membership = FakeBilling.active(), onSale = false)
        compose.onNode(hasText("You have Pro") and isHeading()).assertExists()
        compose.onNodeWithText("Pro cannot be bought right now", substring = true).assertExists()
        compose.onNodeWithText("Switch to", substring = true).assertDoesNotExist()
        assertEquals(listOf("restore"), done)
    }

    @Test
    fun `while the plans load it says so, and when they cannot it offers to try again`() {
        show(SIGNED_IN.copy(plans = emptyList(), loading = true))
        compose.onNodeWithText("Loading the plans…").assertExists()

        state = SIGNED_IN.copy(plans = emptyList(), loadError = UiText.Raw("There is no connection. Check it and try again."))
        compose.onNode(hasText("Try again") and hasClickAction()).performClick()
        compose.onNodeWithText("Subscribe", substring = true).assertDoesNotExist()
        assertEquals(listOf("retry"), done)
    }

    @Test
    fun `what an action came to is said above the main button, and can be put away`() {
        val pending = UiText.Raw("The payment is pending. Pro starts once Google Play confirms it.")
        show(SIGNED_IN.copy(message = ProMessage(pending, isError = false)))

        compose.onAllNodesWithText("The payment is pending. Pro starts once Google Play confirms it.").assertCountEquals(1)
        compose.onNode(hasText("OK") and hasClickAction()).performClick()
        assertEquals(listOf("dismiss"), done)
    }

    @Test
    fun `a build that does not sell Pro says so, and offers nothing`() {
        show(ProUiState(sold = false, callsLeft = TRIAL))

        compose.onNodeWithText("Pro is not available in this build of the app.", substring = true).assertExists()
        compose.onNodeWithText("Sign in with Google").assertDoesNotExist()
        compose.onNodeWithText("Restore purchases").assertDoesNotExist()
    }

    @Test
    fun `the privacy policy and the terms of use are beside the terms of renewal, on the proxy's address`() {
        show(SIGNED_IN, legalLinks = LegalLinks.of(PROXY))

        compose.onNodeWithText("A plan renews automatically", substring = true).performScrollTo()
        compose.onNode(hasText("Privacy policy") and hasClickAction()).performScrollTo().performClick()
        assertEquals("$PROXY/privacy", shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity.dataString)
        compose.onNode(hasText("Terms of use") and hasClickAction()).performScrollTo().performClick()
        assertEquals("$PROXY/terms", shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity.dataString)
    }

    @Test
    fun `a build without the proxy's address shows no link to the privacy policy or the terms`() {
        show(SIGNED_IN, legalLinks = null)

        compose.onNodeWithText("A plan renews automatically", substring = true).assertExists()
        compose.onNodeWithText("Privacy policy").assertDoesNotExist()
        compose.onNodeWithText("Terms of use").assertDoesNotExist()
    }

    private fun show(shown: ProUiState, legalLinks: LegalLinks? = null) {
        state = shown
        compose.setContent { SplitVibeTheme { ProScreen(state = state, actions = actions, legalLinks = legalLinks) } }
    }

    /** Neither Pro's pitch nor a plan to buy or switch to. */
    private fun assertNoOffer() {
        compose.onNodeWithText("${CallsLeft.PRO_MONTHLY} AI calls a month").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(0)
        compose.onNodeWithText("Subscribe", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Switch to", substring = true).assertDoesNotExist()
    }

    private companion object {
        const val PROXY = "https://split-vibe-proxy.example.workers.dev"

        /** What makes a part of a screen a group of choices of one, to a screen reader. */
        val GROUP = SemanticsProperties.SelectableGroup
        val TRIAL = CallsLeft(BigDecimal(3), BigDecimal(1), BigDecimal(2), period = "trial", resetsAt = null)
        val FREE = CallsLeft(BigDecimal(10), BigDecimal(3), BigDecimal(7), "2026-11", resetsAt = 1_796_083_200_000, plan = Plan.FREE)
        val SIGNED_IN = ProUiState(sold = true, signedIn = true, callsLeft = FREE, plans = listOf(FakeBilling.MONTHLY, FakeBilling.YEARLY))
        val MEMBER = SIGNED_IN.copy(
            callsLeft = FREE.copy(limit = BigDecimal(300), used = BigDecimal(12), remaining = BigDecimal(288), plan = Plan.PRO),
            selected = ProPeriod.MONTHLY,
        )
    }
}
