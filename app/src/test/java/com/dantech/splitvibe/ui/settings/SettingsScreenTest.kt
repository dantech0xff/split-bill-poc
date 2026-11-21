package com.dantech.splitvibe.ui.settings

import android.app.Application
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.common.LegalLinks
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Settings offers about the phone's online speech service, which only the user's consent brings
 * there, the plan beside the calls left, the keyboard of the local currency, and what deleting the
 * account says about Pro and how its dialog warns that it cannot be undone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(AppSettings(localCurrency = "VND"))
    private var account by mutableStateOf(Account(installId = "settings", session = null, callsLeft = null, ai = AiStatus.AVAILABLE))
    private val answers = mutableListOf<Boolean>()
    private var proOpened = 0
    private var legalLinks: LegalLinks? = null
    private val deletions = mutableListOf<Boolean>()
    private var withdrawals = 0

    /** The view the screen is drawn in, which the keyboard talks to. */
    private lateinit var view: View

    /** The colour of errors in the theme the screen is drawn with. */
    private var errorColour = Color.Unspecified

    @Test
    fun `the consent to the AI is not offered before the user agreed to it`() {
        show()

        compose.onNodeWithText(AI_CONSENT).assertDoesNotExist()
    }

    @Test
    fun `the user can take the consent to the AI back, after which Settings says it is asked again`() {
        settings = settings.copy(aiAllowed = true)
        show()
        compose.onNodeWithText(AI_CONSENT).performScrollTo().assertExists()

        compose.onNodeWithText("Take back").performScrollTo().performClick()

        assertEquals(1, withdrawals)
        compose.onNodeWithText("Taken back. Split Vibe asks you again before it next uses the AI.").assertExists()
        // Agreeing again is only for the consent screen, at the next use of the AI.
        compose.onNodeWithText("Take back").assertDoesNotExist()
    }

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

    @Test
    fun `the plan shows beside the calls left, with a way to get Pro or to manage it`() {
        show(callsLeft = MONTH)

        compose.onNodeWithText("Free plan").assertExists()
        compose.onNodeWithText("7 of 10 calls left this month", substring = true).assertExists()
        compose.onNode(hasText("Get Pro") and hasClickAction()).performClick()

        account = account.copy(callsLeft = MONTH.copy(limit = BigDecimal(300), remaining = BigDecimal(297), plan = Plan.PRO))
        compose.onNodeWithText("Pro plan").assertExists()
        compose.onNode(hasText("Manage Pro") and hasClickAction()).performClick()
        assertEquals(2, proOpened)
    }

    @Test
    fun `before the proxy has counted the calls, Pro is still a tap away, and without AI there is no plan at all`() {
        show()
        compose.onNodeWithText("Free plan").assertDoesNotExist()
        compose.onNode(hasText("Get Pro") and hasClickAction()).assertExists()
        // A text with nothing in it would be a stop that a screen reader has nothing to say for.
        compose.onAllNodes(emptyText, useUnmergedTree = true).assertCountEquals(0)

        account = account.copy(ai = AiStatus.NOT_SET_UP)
        compose.onNodeWithText("Get Pro").assertDoesNotExist()
    }

    @Test
    fun `deleting the account says it does not cancel Pro, which is cancelled in Google Play first`() {
        show(callsLeft = MONTH, signedIn = true)

        compose.onNode(hasText("Delete account") and hasClickAction()).performScrollTo().performClick()

        compose.onNodeWithText("Deleting the account does not cancel Pro", substring = true).assertExists()
        compose.onNodeWithText("cancel it in Google Play first", substring = true).assertExists()
    }

    @Test
    fun `the phone's data is deleted with the account only when the person ticks the box, which starts off each time`() {
        show(callsLeft = MONTH, signedIn = true)
        val deleteAccount = hasText("Delete account") and hasClickAction()
        val withPhoneData = compose.onNode(hasText(WITH_PHONE_DATA) and isToggleable())

        compose.onNode(deleteAccount).performScrollTo().performClick()
        withPhoneData.assertIsOff()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()

        compose.onNode(deleteAccount).performScrollTo().performClick()
        withPhoneData.assertIsOff().performClick()
        withPhoneData.assertIsOn()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()

        compose.onNode(deleteAccount).performScrollTo().performClick()
        withPhoneData.assertIsOff()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(listOf(false, true), deletions)
    }

    @Test
    // The colour is read off the drawn picture.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `deleting the account cannot be undone, so the button that goes ahead is in the colour of errors`() {
        show(callsLeft = MONTH, signedIn = true)

        compose.onNode(hasText("Delete account") and hasClickAction()).performScrollTo().performClick()

        val delete = compose.onNode(hasText("Delete") and hasClickAction()).captureToImage().toPixelMap()
        // The middle of the round end of the pill, clear of its label.
        val middle = delete.height / 2
        assertEquals("The colour of the button that deletes the account", errorColour, delete[middle, middle])
    }

    @Test
    fun `the local currency takes three capitals, which the keyboard does not correct into a word`() {
        show()
        val field = compose.onNode(hasSetTextAction() and hasText("Local currency"))

        field.performClick()
        val keyboard = EditorInfo()
        compose.runOnIdle { assertNotNull("The field started no input", view.onCreateInputConnection(keyboard)) }
        assertTrue("Capitals were not asked for", keyboard.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0)
        assertEquals("The keyboard may correct the code", 0, keyboard.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)

        field.performTextReplacement("usdx")
        field.assert(hasText("USD"))
    }

    @Test
    fun `the name field follows the stored name when deleting the phone's data forgets it`() {
        settings = settings.copy(myName = "Minh")
        show(signedIn = true)
        compose.onNode(hasText("Minh") and hasSetTextAction()).assertExists()

        settings = settings.copy(myName = "")

        compose.onNode(hasText("Minh") and hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `the privacy policy and the terms of use open on the proxy's address`() {
        legalLinks = LegalLinks.of(PROXY)
        show()

        compose.onNode(hasText("Privacy policy") and hasClickAction()).performScrollTo().performClick()
        assertEquals("$PROXY/privacy", nextOpened()?.dataString)
        compose.onNode(hasText("Terms of use") and hasClickAction()).performScrollTo().performClick()
        assertEquals("$PROXY/terms", nextOpened()?.dataString)
    }

    @Test
    fun `a build without the proxy's address shows no link to the privacy policy or the terms`() {
        show()

        compose.onNodeWithText("Privacy policy").assertDoesNotExist()
        compose.onNodeWithText("Terms of use").assertDoesNotExist()
    }

    private fun nextOpened(): Intent? = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        ?.takeIf { it.action == Intent.ACTION_VIEW }

    private fun show(callsLeft: CallsLeft? = null, signedIn: Boolean = false) {
        account = Account(
            installId = "settings-install",
            session = if (signedIn) Session("settings-session", expiresAt = Long.MAX_VALUE) else null,
            callsLeft = callsLeft,
            ai = AiStatus.AVAILABLE,
            signedInAs = if (signedIn) "minh.tran@example.com" else null,
        )
        content()
    }

    private fun content() = compose.setContent {
        view = LocalView.current
        SplitVibeTheme {
            errorColour = MaterialTheme.colorScheme.error
            SettingsScreen(
                settings = settings,
                account = account,
                accountUi = AccountUiState(),
                canSignIn = true,
                actions = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = { deletions += it }, onOpenPro = { proOpened++ }),
                onSave = { _, _ -> },
                onOpenPaymentMethods = {},
                onBack = {},
                legalLinks = legalLinks,
                onAllowSpeechService = { allowed ->
                    answers += allowed
                    settings = settings.copy(speechServiceAllowed = allowed)
                },
                onWithdrawAiConsent = {
                    withdrawals++
                    settings = settings.copy(aiAllowed = false)
                },
            )
        }
    }

    private companion object {
        val emptyText = SemanticsMatcher("a text that says nothing") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.isEmpty() } == true
        }
        const val SWITCH = "Use the phone's online speech service"
        const val AI_CONSENT = "Reading bills and changing splits with AI"
        const val PROXY = "https://split-vibe-proxy.example.workers.dev"
        const val WITH_PHONE_DATA = "Also delete the bills, people and payment methods on this phone"
        val MONTH = CallsLeft(BigDecimal(10), BigDecimal(3), BigDecimal(7), period = "2026-11", resetsAt = 1_796_083_200_000)
    }
}
