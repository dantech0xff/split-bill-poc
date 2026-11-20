package com.dantech.splitvibe.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.MainActivity
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Types a bill in by hand, from the home screen, and splits it three ways. Nothing of the bill
 * goes to the model: the fake proxy hears of no bill read and no change of a split.
 */
@RunWith(AndroidJUnit4::class)
class ManualEntryJourneyTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aBillTypedInByHandIsSplitEquallyWithoutTheModel() {
        val currency = appCurrency()

        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.typeBillByHand(where = "Corner Cafe", item = "Lunch", amount = "90")
        compose.onNodeWithText("Corner Cafe").assertIsDisplayed()
        compose.addPerson("Minh")
        compose.addPerson("Lan")

        val third = currency.format(currency.toMinor(BigDecimal(30)))
        compose.assertShare("You", third)
        compose.assertShare("Minh", third)
        compose.assertShare("Lan", third)
        // The user paid, so the other two are asked for their shares.
        compose.onNodeWithText("Request ${currency.format(currency.toMinor(BigDecimal(60)))} from 2 people").assertIsDisplayed()

        val modelCalls = fake.proxy.received.filter { it.path == "/v1/bill-analysis" || it.path == "/v1/split-revision" }
        assertEquals("Calls to the model: ${modelCalls.map { "${it.method} ${it.path}" }}", 0, modelCalls.size)
    }
}
