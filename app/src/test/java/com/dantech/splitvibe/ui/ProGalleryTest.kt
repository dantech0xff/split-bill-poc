package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.paywall.ProActions
import com.dantech.splitvibe.ui.paywall.ProScreen
import com.dantech.splitvibe.ui.paywall.ProUiState
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the Pro screen for a signed-in account without Pro and for a Pro member, Settings for a Pro
 * member, the whole of Settings for a signed-in account and the dialog that deletes the account, with
 * sample prices of Google Play, in the light and the dark theme, and the Pro screen and Settings with
 * text at twice the size, and compares them with the reference images in `src/test/screenshots`.
 * Record new images after an intended change with `-Pscreenshots=record`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TALL)
class ProGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the Pro screen of a signed-in account without Pro`() = shot("47-pro") { ProScreen(FREE_USER, NO_ACTIONS) }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the Pro screen without Pro in the dark theme`() = shot("47-pro-dark") { ProScreen(FREE_USER, NO_ACTIONS) }

    @Test
    fun `the Pro screen without Pro with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("47-pro-large-text") { ProScreen(FREE_USER, NO_ACTIONS) }
    }

    @Test
    fun `the Pro screen of a Pro member`() = shot("48-pro-member") { ProScreen(MEMBER, NO_ACTIONS) }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the Pro screen of a Pro member in the dark theme`() = shot("48-pro-member-dark") { ProScreen(MEMBER, NO_ACTIONS) }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `Settings of a Pro member`() = shot("49-settings-pro") { ProSettings() }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `Settings of a Pro member in the dark theme`() = shot("49-settings-pro-dark") { ProSettings() }

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_SETTINGS)
    fun `the whole of Settings for a signed-in account`() {
        shot("56-settings-whole-page") { SignedInSettings() }
        assertWholePage()
    }

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_SETTINGS_AT_NIGHT)
    fun `the whole of Settings in the dark theme`() {
        shot("56-settings-whole-page-dark") { SignedInSettings() }
        assertWholePage()
    }

    @Test
    fun `Settings with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("56-settings-large-text") { SignedInSettings() }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the dialog that deletes the account`() = shot("57-delete-account", open = DELETE_ACCOUNT) { SignedInSettings() }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `the dialog that deletes the account in the dark theme`() =
        shot("57-delete-account-dark", open = DELETE_ACCOUNT) { SignedInSettings() }

    /**
     * Settings of a signed-in account without Pro, with all it can hold: the speech service and the
     * consent to the AI, which the user gave, and the links to the privacy policy and the terms of use.
     */
    @Composable
    private fun SignedInSettings() = SettingsScreen(
        settings = AppSettings(localCurrency = "VND", speechServiceAllowed = true, aiAllowed = true),
        account = Account(
            installId = "gallery-install",
            session = Session("gallery-session", expiresAt = Long.MAX_VALUE),
            callsLeft = MONTH_CALLS,
            ai = AiStatus.AVAILABLE,
            signedInAs = "minh.tran@example.com",
        ),
        accountUi = AccountUiState(),
        canSignIn = true,
        actions = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}, onOpenPro = {}),
        onSave = { _, _ -> },
        onOpenPaymentMethods = {},
        onBack = {},
        legalLinks = LegalLinks.of("https://split-vibe-proxy.example.workers.dev"),
        onAllowSpeechService = {},
        onWithdrawAiConsent = {},
    )

    @Composable
    private fun ProSettings() = SettingsScreen(
        settings = AppSettings(localCurrency = "VND"),
        account = Account(
            installId = "gallery-install",
            session = Session("gallery-session", expiresAt = Long.MAX_VALUE),
            callsLeft = PRO_CALLS,
            ai = AiStatus.AVAILABLE,
            signedInAs = "minh.tran@example.com",
        ),
        accountUi = AccountUiState(),
        canSignIn = true,
        actions = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}, onOpenPro = {}),
        onSave = { _, _ -> },
        onOpenPaymentMethods = {},
        onBack = {},
        onAllowSpeechService = {},
    )

    /** Draws [content], scrolls to [open] and taps it when it is given, and compares the screen with the reference image [name]. */
    private fun shot(name: String, open: String? = null, content: @Composable () -> Unit) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        open?.let { compose.onNode(hasText(it) and hasClickAction()).performScrollTo().performClick() }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    /**
     * The picture is as tall as the window, so a page grown longer than the window would lose its end
     * without a word: it must have nothing left to scroll to.
     */
    private fun assertWholePage() {
        val page = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertFalse("Settings run on below the bottom of the picture", page.value() < page.maxValue())
    }

    private companion object {
        /** 1 December 2026, in UTC, when the month's calls come back. */
        const val NEXT_MONTH = 1_796_083_200_000L

        val FREE_CALLS = CallsLeft(BigDecimal(10), BigDecimal(3), BigDecimal(7), "2026-11", resetsAt = NEXT_MONTH, plan = Plan.FREE)
        val MONTH_CALLS = CallsLeft(BigDecimal(10), BigDecimal(1), BigDecimal(9), "2026-11", resetsAt = NEXT_MONTH, plan = Plan.FREE)
        const val DELETE_ACCOUNT = "Delete account"
        val PRO_CALLS = CallsLeft(BigDecimal(300), BigDecimal("41.2"), BigDecimal("258.8"), "2026-11", NEXT_MONTH, plan = Plan.PRO)

        /** Google Play's sample prices: $3.99 a month and $29.99 a year. */
        val FREE_USER = ProUiState(
            sold = true,
            signedIn = true,
            callsLeft = FREE_CALLS,
            plans = listOf(FakeBilling.MONTHLY, FakeBilling.YEARLY),
        )
        val MEMBER = FREE_USER.copy(callsLeft = PRO_CALLS, membership = FakeBilling.active(basePlanId = "monthly"))

        val NO_ACTIONS = ProActions(
            onSignIn = {},
            onSelect = {},
            onBuy = {},
            onSwitch = {},
            onRestore = {},
            onRetry = {},
            onDismissMessage = {},
            onBack = {},
        )
    }
}

private const val TALL = "w412dp-h1200dp-xhdpi"
private const val TALL_AT_NIGHT = "w412dp-h1200dp-night-xhdpi"
private const val PHONE = "w412dp-h915dp-xhdpi"
private const val PHONE_AT_NIGHT = "w412dp-h915dp-night-xhdpi"

/** Tall enough to show the whole of Settings at once, down to deleting the account. */
private const val WHOLE_SETTINGS = "w412dp-h1500dp-xhdpi"
private const val WHOLE_SETTINGS_AT_NIGHT = "w412dp-h1500dp-night-xhdpi"
