package com.dantech.splitvibe.e2e

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.splitvibe.MainActivity
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.domain.payments.PaymentMethod
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Asks someone to pay, signed in: a bill typed in by hand is split with Minh, the request to him
 * goes out through the share sheet and is kept on the fake proxy as the user's, and the list of
 * requests shows it. The session comes from the app's own store, as Google's sign-in cannot run in
 * a test; see [FakeProxyRule].
 */
@RunWith(AndroidJUnit4::class)
class PaymentRequestJourneyTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule(signedIn = true)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** The way to get paid this test saved, to remove again; null when the phone had one already. */
    private var savedMethod: String? = null

    /** A request is kept on the proxy only with a way to get paid on it, which stays on the phone. */
    @Before
    fun haveAWayToGetPaid() {
        val methods = GlobalContext.get().get<PaymentMethodRepository>()
        val none = runBlocking { methods.all.first().isEmpty() }
        if (none) savedMethod = runBlocking { methods.add(PaymentMethod.PayPalMe("splitvibe")) }
    }

    @After
    fun removeTheWayToGetPaid() {
        val saved = savedMethod ?: return
        runBlocking { GlobalContext.get().get<PaymentMethodRepository>().remove(saved) }
    }

    @Test
    fun aRequestAfterASplitIsKeptOnTheProxyAndListed() {
        val currency = appCurrency()
        val owed = currency.format(currency.toMinor(BigDecimal(45)))

        compose.onNodeWithText("Split a bill").performClick()
        compose.typeBillByHand(where = "Pho 24", item = "Pho bo", amount = "90")
        compose.addPerson("Minh")
        compose.onNodeWithText("Request $owed from 1 person").performClick()
        compose.awaitNode(hasText("Ask for the money"))
        compose.list().performScrollToNode(hasSetTextAction() and hasText("Your name on the requests"))
        compose.field("Your name on the requests").performTextReplacement("Lan")

        // The share sheet is the system's and would cover the app, so it is answered without opening.
        val shareSheet = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER), null, true)
        try {
            val send = hasText("Send") and hasClickAction()
            compose.list().performScrollToNode(send)
            compose.onNode(send).performClick()
            compose.awaitNode(hasText("Again"))
            assertEquals(1, shareSheet.hits)
        } finally {
            instrumentation.removeMonitor(shareSheet)
        }

        // Kept on the proxy as the signed-in user's request to Minh for his share of the bill.
        val created = fake.proxy.callsTo("POST", "/v1/requests").single()
        assertEquals("Bearer ${FakeProxy.SESSION}", created.header("Authorization"))
        val request = checkNotNull(created.json) { "The request was not JSON: ${created.body}" }
        assertEquals("Minh", request.textOf("payerName"))
        assertEquals("Lan", request.textOf("creditorName"))
        assertEquals("Pho 24", request.textOf("title"))
        assertEquals(currency.toMinor(BigDecimal(45)), request.getValue("amountMinor").jsonPrimitive.longOrNull)
        assertEquals(currency.code, (request.getValue("currency") as JsonObject).textOf("code"))
        assertTrue("The request carries no way to get paid: ${created.body}", (request["method"] as? JsonObject)?.isNotEmpty() == true)

        // The list of requests, as the proxy gives it, shows the request as the user's own.
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Requests").performClick()
        compose.waitUntil(TIMEOUT_MS) { fake.proxy.callsTo("GET", "/v1/requests").isNotEmpty() }
        compose.awaitNode(hasText("You asked for"))
        compose.onNodeWithText("Minh · Pho 24").assertIsDisplayed()
        compose.onNodeWithText(owed).assertIsDisplayed()
        compose.onNodeWithText("Not opened in the app yet").assertIsDisplayed()
    }

    private fun JsonObject.textOf(key: String): String? = this[key]?.jsonPrimitive?.content
}
