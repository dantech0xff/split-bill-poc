package com.dantech.splitvibe.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.MainActivity
import com.dantech.splitvibe.data.settings.SettingsStore
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Turns the AI down on the consent screen that a bill read brings up, and types the bill in by hand
 * instead: the split works as for any bill typed in by hand, and nothing of it goes to the model.
 */
@RunWith(AndroidJUnit4::class)
class DeclinedAiJourneyTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aBillTypedInByHandAfterTurningTheAiDownIsSplitWithoutTheModel() {
        val currency = appCurrency()

        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("Use the sample bill").performScrollTo().performClick()
        compose.awaitNode(hasText("Read and split the bill") and isEnabled())
        compose.onNodeWithText("Read and split the bill").tap()
        compose.answerTheAiConsent("Type it in by hand")
        compose.fillInTheBill(where = "Corner Cafe", item = "Lunch", amount = "90")

        compose.onNodeWithText("Corner Cafe").assertIsDisplayed()
        compose.assertShare("You", currency.format(currency.toMinor(BigDecimal(90))))
        val modelCalls = fake.proxy.received.filter { it.path == "/v1/bill-analysis" || it.path == "/v1/split-revision" }
        assertEquals("Calls to the model: ${modelCalls.map { "${it.method} ${it.path}" }}", 0, modelCalls.size)
        assertFalse("turning it down is no consent", GlobalContext.get().get<SettingsStore>().settings.value.aiAllowed)
    }
}
