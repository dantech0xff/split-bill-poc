package com.dantech.splitvibe.data.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.payments.PaymentPayload
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var shares: ShareRepository
    private val linked = ShareFixtures.saved.copy(share = ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL))

    @Before
    fun setUp() {
        server.start()
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        shares = ShareRepository(proxy.api, retryDelayMs = { 0 })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a link is made with what the page shows of the bill, nulls and all`() = runBlocking {
        server.enqueue(json(ShareFixtures.owned()))

        val owned = shares.publish(
            ShareFixtures.SHARE_ID,
            ShareFixtures.saved.copy(bill = ShareFixtures.saved.bill.copy(merchant = null)),
            "Dana",
        )

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/shares/${ShareFixtures.SHARE_ID}", request.path)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("\"bill-1\"", body["billId"].toString())
        assertEquals("3", body["billRevision"].toString())
        assertEquals("null", body["bill"]!!.jsonObject["merchant"].toString())
        assertEquals(ShareFixtures.URL, owned.url)
        assertEquals(ShareView.OPEN, owned.share.status)
    }

    @Test
    fun `a copy of the bill is sent after a change, but never one older than a copy already sent`() = runBlocking {
        server.enqueue(json(ShareFixtures.owned()))
        server.enqueue(json(ShareFixtures.owned(ShareFixtures.view(revision = 2))))
        shares.publish(ShareFixtures.SHARE_ID, linked, "Dana")

        assertNull("The same revision again sends nothing", shares.project(linked, "Dana"))
        assertNull("An older one neither", shares.project(linked.copy(revision = 2), "Dana"))
        shares.project(linked.copy(revision = 4), "Dana")

        assertEquals(2, server.requestCount)
        assertNull("A bill without a link has nothing to show", shares.project(ShareFixtures.saved.copy(revision = 9), "Dana"))
    }

    @Test
    fun `settled amounts go with how to pay, and the copy they came from is not sent again after them`() = runBlocking {
        server.enqueue(json(ShareFixtures.share(ShareFixtures.view(status = ShareView.LOCKED, revision = 5))))
        val payment =
            PaymentPayload(
                link = "https://paypal.me/dana/71500VND",
                qrPayload = null,
                details = "PayPal: paypal.me/dana",
                amountFilledIn = true,
            )

        val view = shares.finalize(
            linked,
            "Dana",
            mapOf(
                "me" to ShareAmount(44_000, null),
                "p1" to ShareAmount(99_000, payment),
                "p2" to ShareAmount(11_000, null),
            ),
        )

        val request = server.takeRequest()
        assertEquals("/v1/shares/${ShareFixtures.SHARE_ID}/finalize", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(
            """{"me":{"amountMinor":44000,"payment":null},""" +
                """"p1":{"amountMinor":99000,"payment":{"link":"https://paypal.me/dana/71500VND",""" +
                """"qrPayload":null,"details":"PayPal: paypal.me/dana","amountFilledIn":true}},""" +
                """"p2":{"amountMinor":11000,"payment":null}}""",
            body["amounts"].toString(),
        )
        assertEquals(ShareView.LOCKED, view.status)
        assertNull(shares.project(linked, "Dana"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `anyone with the link reads it without a session or an install id`() = runBlocking {
        server.enqueue(json(ShareFixtures.share(ShareFixtures.view(claims = ShareFixtures.CLAIMS))))

        val view = shares.open(ShareFixtures.URL)

        val request = server.takeRequest()
        assertEquals("/v1/shares/open", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-SplitVibe-Install"))
        assertEquals("""{"token":"${ShareFixtures.TOKEN}"}""", request.body.readUtf8())
        assertEquals(listOf("minh", "Stranger"), view.claims.map { it.name })
    }

    @Test
    fun `the live link carries its token in the protocols, and passes on changes until it is gone`() = runBlocking {
        val upgrades = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/v1/shares/live" -> {
                    upgrades += request
                    MockResponse().withWebSocketUpgrade(
                        sending("pong", """{"type":"share","share":${ShareFixtures.view(revision = 7)}}""", """{"type":"gone"}"""),
                    )
                }

                "/v1/shares/open" -> json(ShareFixtures.share(ShareFixtures.view(revision = 6)))

                else -> MockResponse().setResponseCode(404)
            }
        }

        val events = withTimeout(10_000) { shares.live(ShareFixtures.URL).toList() }

        assertEquals("splitvibe.v1, ${ShareFixtures.TOKEN}", upgrades.single().getHeader("Sec-WebSocket-Protocol"))
        assertTrue(events.filterIsInstance<LiveShare.Changed>().any { it.share.revision == 7L })
        assertEquals(LiveShare.Gone, events.last())
    }

    @Test
    fun `a dropped connection is opened again, and a link that no longer works ends it`() = runBlocking {
        val attempts = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                // The first connection fails, the second closes at once, the third finds the link gone.
                "/v1/shares/live" -> when (attempts.incrementAndGet()) {
                    1 -> MockResponse().setResponseCode(503)
                    2 -> MockResponse().withWebSocketUpgrade(closing())
                    else -> MockResponse().setResponseCode(410)
                }

                else -> MockResponse().setResponseCode(503)
            }
        }

        val events = withTimeout(10_000) { shares.live(ShareFixtures.URL).toList() }

        assertEquals(listOf(LiveShare.Gone), events)
        assertEquals(3, attempts.get())
    }

    /** A server side that sends [messages] once the connection is open, and answers a close as a server does. */
    private fun sending(vararg messages: String) = object : ServerSide() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            messages.forEach(webSocket::send)
        }
    }

    private fun closing() = object : ServerSide() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.close(1000, null)
        }
    }

    /** Answers the app's close, so the server can stop once the test is over. */
    private open class ServerSide : WebSocketListener() {
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
