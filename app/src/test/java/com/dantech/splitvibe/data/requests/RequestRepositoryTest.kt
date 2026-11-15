package com.dantech.splitvibe.data.requests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RequestRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private lateinit var requests: RequestRepository

    private val vietQr = PaymentMethod.VietQr("970416", "257678859", "DANA NGUYEN")
    private val newRequest = NewRequest(
        id = "r1",
        amountMinor = 150_000,
        currency = BillCurrency("VND", 0),
        payerName = "Minh",
        creditorName = "Dana",
        title = "Pho 24",
        method = vietQr,
        billId = "bill-1",
    )

    @Before
    fun setUp() {
        server.start()
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        requests = RequestRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `a request carries the one method it is sent with, and its link is kept to send again`() = runBlocking {
        server.enqueue(json("""{"request":${GroupFixtures.request()},"url":"https://proxy.example/r/token-1"}"""))

        val created = requests.create(newRequest)

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(
            """{"type":"vietqr","bankBin":"970416","account":"257678859","accountName":"DANA NGUYEN"}""",
            body["method"].toString(),
        )
        assertEquals("Dana", body["creditorName"]!!.jsonPrimitive.content)
        assertEquals("https://proxy.example/r/token-1", created.url)
        assertEquals("https://proxy.example/r/token-1", requests.linkOf("r1"))
        assertEquals(listOf("r1"), requests.requests.first()?.map { it.id })
        assertEquals(vietQr, requests.requests.first()?.single()?.paymentMethod)

        // Asking again for the same request sends nothing: it is the same request with the same link.
        assertEquals(created.url, requests.create(newRequest).url)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the payer's report and the requester's confirmation replace the kept copy`() = runBlocking {
        server.enqueue(json("""{"requests":[${GroupFixtures.request()}]}"""))
        requests.refresh()
        server.enqueue(json(GroupFixtures.request(reportedPaidAt = 1_763_200_000_000)))
        server.enqueue(json(GroupFixtures.request(reportedPaidAt = 1_763_200_000_000, confirmedPaidAt = 1_763_210_000_000)))

        assertEquals(RequestStatus.REPORTED_PAID, requests.reportPaid("r1").status)
        assertEquals(RequestStatus.PAID, requests.confirmPaid("r1").status)

        assertEquals(listOf(RequestStatus.PAID), requests.requests.first()?.map { it.status })
        server.takeRequest()
        assertEquals("/v1/requests/r1/reported-paid", server.takeRequest().path)
        assertEquals("/v1/requests/r1/confirmed-paid", server.takeRequest().path)
    }

    @Test
    fun `a request whose requester deleted their account has no method to pay with`() = runBlocking {
        server.enqueue(json("""{"requests":[${GroupFixtures.request(role = "debtor", method = "{}")}]}"""))

        requests.refresh()

        assertNull(requests.requests.first()?.single()?.paymentMethod)
    }

    @Test
    fun `deleting a request forgets it and its link`() = runBlocking {
        server.enqueue(json("""{"request":${GroupFixtures.request()},"url":"https://proxy.example/r/token-1"}"""))
        requests.create(newRequest)
        server.enqueue(MockResponse().setResponseCode(204))

        requests.delete("r1")

        assertEquals(emptyList<RequestView>(), requests.requests.first())
        assertNull(requests.linkOf("r1"))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
