package com.dantech.splitvibe.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.MainActivity
import com.dantech.splitvibe.domain.BillCurrency
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Splits the sample bill through AI, from the home screen to what each person pays, agreeing to
 * the AI on the consent screen first. The bill's text is read on the phone by the real recognizer,
 * and the app's own client sends it to the fake proxy, which answers with what Claude said about this
 * bill when it was recorded.
 */
@RunWith(AndroidJUnit4::class)
class SampleBillJourneyTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theSampleBillIsReadThroughTheProxyAndSplitBetweenThreePeople() {
        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("Use the sample bill").performScrollTo().performClick()
        compose.awaitNode(hasText("Read and split the bill") and isEnabled())
        compose.field("How should it be split?").performScrollTo().performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").tap()

        // The first call to the AI waits for the person's consent; nothing went to the proxy before it.
        compose.awaitNode(hasText("Use the AI for your bills?"))
        assertTrue(fake.proxy.callsTo("POST", "/v1/bill-analysis").isEmpty())
        compose.answerTheAiConsent("Agree")

        // The bill as Claude read it, under the restaurant's name.
        compose.awaitNode(hasText("Bill total"))
        compose.onNodeWithText("QUÁN ỐC BA MIỂN").assertIsDisplayed()

        // One bill read went out, as this install that nobody signed in on, with the text read on
        // the phone and the instruction, and nothing else: never the photo.
        val read = fake.proxy.callsTo("POST", "/v1/bill-analysis").single()
        assertNotNull(read.header(FakeProxy.INSTALL_HEADER))
        assertNull(read.header("Authorization"))
        val body = checkNotNull(read.json) { "The bill read was not JSON: ${read.body}" }
        assertEquals(setOf("schemaVersion", "requestId", "ocrDocument", "instruction", "localCurrency"), body.keys)
        assertEquals(INSTRUCTION, body.getValue("instruction").jsonPrimitive.content)
        val document = body.getValue("ocrDocument").jsonPrimitive.content
        assertTrue("The total was not in the text sent:\n$document", document.contains("902.664"))

        // Minh had the beers, so his share is the largest, and the dong left over by rounding is the user's.
        val vnd = BillCurrency("VND", 0)
        compose.assertShare("You", vnd.format(250_993))
        compose.assertShare("Minh", vnd.format(400_680))
        compose.assertShare("Lan", vnd.format(250_991))
    }

    private companion object {
        /** What the user typed when the answer was recorded. */
        const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
    }
}
