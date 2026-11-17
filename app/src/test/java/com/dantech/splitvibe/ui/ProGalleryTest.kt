package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.paywall.ProActions
import com.dantech.splitvibe.ui.paywall.ProScreen
import com.dantech.splitvibe.ui.paywall.ProUiState
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the Pro screen for a signed-in account without Pro and for a Pro member, and Settings for a
 * Pro member, with sample prices of Google Play, in the light and the dark theme, and compares them
 * with the reference images in `src/test/screenshots`. Record new images after an intended change
 * with `-Pscreenshots=record`.
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

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    private companion object {
        /** 1 December 2026, in UTC, when the month's calls come back. */
        const val NEXT_MONTH = 1_796_083_200_000L

        val FREE_CALLS = CallsLeft(BigDecimal(10), BigDecimal(3), BigDecimal(7), "2026-11", resetsAt = NEXT_MONTH, plan = Plan.FREE)
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
