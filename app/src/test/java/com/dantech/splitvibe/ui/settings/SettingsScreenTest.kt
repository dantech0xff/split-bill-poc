package com.dantech.splitvibe.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What Settings offers about the phone's online speech service, which only the user's consent brings there. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(AppSettings(localCurrency = "VND"))
    private val answers = mutableListOf<Boolean>()

    @Test
    fun `the speech service is not offered before the user let it hear them`() {
        show()

        compose.onNodeWithText(SWITCH, substring = true).assertDoesNotExist()
    }

    @Test
    fun `the user can take the speech service back, and give it again while Settings is open`() {
        settings = settings.copy(speechServiceAllowed = true)
        show()
        val switch = compose.onNode(isToggleable()).performScrollTo()
        switch.assertIsOn()

        switch.performClick()

        // Still offered after it is turned off, so it can be turned on again.
        switch.assertIsOff()
        compose.onNodeWithText(SWITCH, substring = true).assertExists()
        switch.performClick()
        switch.assertIsOn()
        assertEquals(listOf(false, true), answers)
    }

    private fun show() = compose.setContent {
        SplitVibeTheme {
            SettingsScreen(
                settings = settings,
                account = Account(installId = "settings-install", session = null, callsLeft = null, ai = AiStatus.AVAILABLE),
                accountUi = AccountUiState(),
                canSignIn = true,
                actions = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}),
                onSave = { _, _ -> },
                onOpenPaymentMethods = {},
                onBack = {},
                onAllowSpeechService = { allowed ->
                    answers += allowed
                    settings = settings.copy(speechServiceAllowed = allowed)
                },
            )
        }
    }

    private companion object {
        const val SWITCH = "Use the phone's online speech service"
    }
}
