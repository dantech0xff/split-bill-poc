package com.dantech.splitvibe.ui.payments

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * What a payment method holds, such as an account number, an IBAN or a UPI ID, is kept as it is
 * typed and out of the keyboard's memory: the keyboard corrects none of the sheet's fields, offers a
 * password keyboard, which neither suggests nor learns, for the three that are private, and still
 * shows what is typed. Every field, of one line or of more, takes the 500 characters the proxy takes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PaymentMethodSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun openTheSheet() {
        compose.setContent { SplitVibeTheme { PaymentMethodSheet(editing = null, onSave = {}, onDismiss = {}) } }
    }

    @Test
    fun `an account number gets a password keyboard that corrects nothing, and is cut at its longest`() {
        // VietQR, the first kind the sheet offers.
        val account = keyboardOf(field("Account number"))

        assertPrivate("the account number", account)
        field("Account number").performTextInput("1".repeat(LONGEST + 20))
        assertEquals(LONGEST, textOf(field("Account number")).length)
        // The password keyboard does not hide the number: the field shows it as typed.
        assertTrue(textOf(field("Account number")).all { it == '1' })
    }

    @Test
    fun `an IBAN gets a password keyboard in capitals that corrects nothing`() {
        compose.onNodeWithText("SEPA").performClick()

        val iban = keyboardOf(field("IBAN"))

        assertPrivate("the IBAN", iban)
        assertTrue("Capitals were not asked for", iban.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0)
        field("IBAN").performTextInput("DE89 3704 0044 0532 0130 00")
        assertEquals("DE89 3704 0044 0532 0130 00", textOf(field("IBAN")))
    }

    @Test
    fun `a UPI ID gets a password keyboard that corrects nothing`() {
        compose.onNodeWithText("UPI").performClick()

        assertPrivate("the UPI ID", keyboardOf(field("UPI ID")))
    }

    @Test
    fun `the name on the account is corrected by no keyboard either, and keeps its capitals`() {
        val name = keyboardOf(field("Name on the account"))

        assertEquals(InputType.TYPE_CLASS_TEXT, name.inputType and TYPE_AND_VARIATION)
        assertEquals("The keyboard may correct the name", 0, name.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertTrue("Capitals were not asked for", name.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0)
    }

    @Test
    fun `the details of another way take more than one line and as much as any field, not the shorter default of a field`() {
        compose.onNodeWithText("Other").performClick()

        val details = keyboardOf(field("Details"))
        field("Details").performTextInput("x".repeat(LONGEST + 20))

        assertTrue("The details take one line only", details.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
        assertEquals("The keyboard may correct the details", 0, details.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertEquals(LONGEST, textOf(field("Details")).length)
    }

    private fun field(label: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText(label))

    /** Fails unless [keyboard], of [what], is a password keyboard that corrects nothing. */
    private fun assertPrivate(what: String, keyboard: EditorInfo) {
        assertEquals(
            "$what does not get a password keyboard",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            keyboard.inputType and TYPE_AND_VARIATION,
        )
        assertEquals("The keyboard may correct $what", 0, keyboard.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    }

    /**
     * What [field] asks of the keyboard once it has the cursor, as the keyboard is told it. The sheet
     * lies in a window of its own, the one of the last dialog shown, whose view with the focus holds
     * the field.
     */
    private fun keyboardOf(field: SemanticsNodeInteraction): EditorInfo {
        field.performClick()
        val info = EditorInfo()
        compose.runOnIdle {
            val window = checkNotNull(ShadowDialog.getLatestDialog()?.window) { "The sheet is not open" }
            val focused: View = checkNotNull(window.decorView.findFocus()) { "Nothing in the sheet has the focus" }
            assertNotNull("The field started no input", focused.onCreateInputConnection(info))
        }
        return info
    }

    private fun textOf(field: SemanticsNodeInteraction): String =
        field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private companion object {
        /** The most characters any field of the sheet takes, as the proxy does. */
        const val LONGEST = 500

        val TYPE_AND_VARIATION = InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION
    }
}
