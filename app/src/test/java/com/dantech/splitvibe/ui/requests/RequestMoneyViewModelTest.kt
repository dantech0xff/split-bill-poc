package com.dantech.splitvibe.ui.requests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.PersonShare
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.english
import com.dantech.splitvibe.ui.split.PersonRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestMoneyViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private lateinit var proxy: TestProxy
    private val settings = MutableStateFlow(AppSettings("VND"))
    private val methods = PaymentMethodRepository(storage.database)

    private val vnd = BillCurrency("VND", 0)
    private val minh = Participant("p1", "Minh")
    private val saved = SavedBill(
        id = "bill-1",
        createdAt = 1,
        updatedAt = 1,
        bill = Bill("Pho 24", "2026-11-14", vnd, emptyList(), emptyList(), printedTotalMinor = 300_000),
        plan = SplitPlan(participants = listOf(Participant.ME, minh)),
    )
    private val request = PersonRequest(minh, PersonShare("p1", itemsMinor = 120_000, chargesMinor = 30_000))

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        runBlocking { methods.add(PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN")) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `before signing in, the request is a message with the payment details and code, kept nowhere`() = runBlocking {
        val viewModel = viewModel()

        val prepared = viewModel.prepare(saved, request, name = "")

        assertEquals(
            """
            Hi Minh! Your share of Pho 24, 2026-11-14 is ₫150,000.
            That is ₫120,000 for what you had and ₫30,000 of the shared charges.

            Pay with VietQR: scan the code in your banking app, or use these details.
            Bank: ACB (970416)
            Account: 000000000
            Name: DANA NGUYEN
            Amount: ₫150,000
            Note: Pho 24
            """.trimIndent(),
            prepared.text.english(),
        )
        assertTrue(prepared.paying!!.qrPayload!!.contains("5406150000"))
        assertFalse(prepared.tracked)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `signed in, the request is kept on the proxy and its link goes into the message`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(MockResponse().setBody("""{"request":${GroupFixtures.request()},"url":"https://proxy.example/r/token-1"}"""))
        val viewModel = viewModel()

        val prepared = viewModel.prepare(saved, request, name = "Dana")

        val text = prepared.text.english()
        assertTrue(text, text.endsWith("Open it in Split Vibe to say when you have paid: https://proxy.example/r/token-1"))
        assertTrue(prepared.tracked)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Dana", body["creditorName"]!!.jsonPrimitive.content)
        assertEquals("Minh", body["payerName"]!!.jsonPrimitive.content)
        assertEquals("150000", body["amountMinor"]!!.jsonPrimitive.content)
        assertEquals("Dana", settings.value.myName)
    }

    @Test
    fun `a corrected account makes a new request in place of the old one, which is withdrawn`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "DELETE") return MockResponse().setResponseCode(204)
                val sent = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                val id = sent["id"]!!.jsonPrimitive.content
                val view = GroupFixtures.request(id = id, method = sent["method"].toString())
                return MockResponse().setBody("""{"request":$view,"url":"https://proxy.example/r/#token-$id"}""")
            }
        }
        val viewModel = viewModel()
        val first = viewModel.prepare(saved, request, name = "Dana")

        // The account number had a typo; the same method is corrected in Settings.
        val method = methods.all.first().single()
        methods.replace(method.id, PaymentMethod.VietQr("970416", "0000000000", "DANA NGUYEN"))
        awaitOnMain { (viewModel.method.value?.method as? PaymentMethod.VietQr)?.account == "0000000000" }
        val second = viewModel.prepare(saved, request, name = "Dana")

        val posts = mutableListOf<RecordedRequest>()
        repeat(server.requestCount) { posts += server.takeRequest() }
        assertEquals(listOf("POST", "POST", "DELETE"), posts.map { it.method })
        // The account field itself, since a number of only zeros could also be part of another value.
        val corrected = Json.parseToJsonElement(posts[1].body.readUtf8()).jsonObject["method"]!!.jsonObject
        assertEquals("0000000000", corrected["account"]!!.jsonPrimitive.content)
        val firstId = first.text.english().substringAfter("#token-")
        assertEquals("/v1/requests/$firstId", posts[2].path)
        val secondText = second.text.english()
        assertTrue(secondText, secondText.contains("Account: 0000000000"))
        assertFalse(secondText.contains(firstId))
    }

    @Test
    fun `when the proxy cannot keep the request, the message still goes, without a link`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"not_configured"}"""))
        val viewModel = viewModel()

        val prepared = viewModel.prepare(saved, request, name = "Dana")

        assertFalse(prepared.tracked)
        assertFalse(prepared.text.english().contains("http"))
        assertTrue(viewModel.message.value != null)
    }

    @Test
    fun `without a payment method the message asks for the amount and nothing more`() = runBlocking {
        methods.all.first().forEach { methods.remove(it.id) }
        val viewModel = viewModel()
        awaitOnMain { viewModel.methods.value?.isEmpty() == true }

        val prepared = viewModel.prepare(saved, request, name = "")

        assertNull(prepared.paying)
        assertEquals(
            "Hi Minh! Your share of Pho 24, 2026-11-14 is ₫150,000.\n" +
                "That is ₫120,000 for what you had and ₫30,000 of the shared charges.",
            prepared.text.english(),
        )
    }

    private fun viewModel(): RequestMoneyViewModel {
        val viewModel = RequestMoneyViewModel(
            paymentMethods = methods,
            requests = RequestRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account),
            account = proxy.account.account,
            settings = settings,
            saveName = { settings.value = settings.value.copy(myName = it) },
        )
        awaitOnMain { viewModel.method.value != null || viewModel.methods.value?.isEmpty() == true }
        return viewModel
    }
}
