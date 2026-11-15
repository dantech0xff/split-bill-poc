package com.dantech.splitvibe.data.share

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.payments.PaymentPayload
import com.dantech.splitvibe.data.share.ShareRepository.Projection
import java.util.Collections
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
    private lateinit var proxy: TestProxy
    private lateinit var shares: ShareRepository
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    /** The link of the fixtures, as the bill keeps it once it was made. */
    private val link = ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL, expiresAt = EXPIRES_AT, createdAt = CREATED_AT)
    private val linked = ShareFixtures.saved.copy(share = link)

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        shares = ShareRepository(proxy.api, retryDelayMs = { 0 }, clock = { NOW })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a link is made with what the page shows of the bill, nulls and all, and the bill keeps what it needs of it`() = runBlocking {
        server.enqueue(json(ShareFixtures.owned()))
        val saved = ShareFixtures.saved.copy(bill = ShareFixtures.saved.bill.copy(merchant = null))

        val (owned, kept) = shares.publish(ShareFixtures.SHARE_ID, saved, "Dana")

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
        assertEquals(
            ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL, EXPIRES_AT, CREATED_AT, ShareRepository.fingerprint(saved, "Dana")),
            kept,
        )
    }

    @Test
    fun `a save that changes nothing the page shows sends nothing, even as a newer revision`() = runBlocking {
        val shown = linked.copy(share = link.copy(shown = ShareRepository.fingerprint(linked, "Dana")))

        // Marking the bill finished, for example, moves neither the bill on the page nor the shares.
        assertNull(shares.project(shown.copy(revision = 9, status = BillStatus.FINAL), "Dana"))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a change the page shows is sent, but never a copy older than one already sent`() = runBlocking {
        server.enqueue(json(ShareFixtures.owned()))
        server.enqueue(json(ShareFixtures.owned(ShareFixtures.view(revision = 2))))
        shares.publish(ShareFixtures.SHARE_ID, linked, "Dana")
        val renamed = linked.copy(plan = linked.plan.withParticipantRenamed("p1", "Minh Nguyen"))

        assertNull("An older copy is not sent after a newer one", shares.project(renamed.copy(revision = 2), "Dana"))
        val projected = shares.project(renamed.copy(revision = 4), "Dana") as Projection.Shown

        assertEquals(ShareRepository.fingerprint(renamed, "Dana"), projected.link.shown)
        assertEquals(false, projected.unsettled)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `nothing is sent to a link that has ended, which the bill forgets`() = runBlocking {
        val ended = linked.copy(revision = 5, share = link.copy(expiresAt = NOW - 1))

        assertEquals(Projection.Forget, shares.project(ended, "Dana"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a link the daily run deleted, made again by a copy sent to it, is taken down and forgotten`() = runBlocking {
        server.dispatcher = recording { request ->
            when (request.method) {
                "PUT" -> json(ShareFixtures.owned().replace("\"createdAt\":$CREATED_AT", "\"createdAt\":${NOW - 5}"))
                else -> MockResponse().setResponseCode(204)
            }
        }

        assertEquals(Projection.Forget, shares.project(linked.copy(revision = 5), "Dana"))

        assertEquals(listOf("PUT", "DELETE"), requests.map { it.method })
    }

    @Test
    fun `a link the proxy no longer has is forgotten`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(410).setBody("""{"error":"gone"}"""))

        assertEquals(Projection.Forget, shares.project(linked.copy(revision = 5), "Dana"))
    }

    @Test
    fun `a change that takes the settled amounts off the page says so`() = runBlocking {
        server.enqueue(json(ShareFixtures.owned(ShareFixtures.view(ShareView.LOCKING, revision = 6))))
        val settled = linked.copy(revision = 5, share = link.copy(settled = true, shown = "before the change"))

        val projected = shares.project(settled, "Dana") as Projection.Shown

        assertTrue(projected.unsettled)
        assertEquals(false, projected.link.settled)
    }

    @Test
    fun `settled amounts go with how to pay and who paid, and the copy they came from is not sent again after them`() = runBlocking {
        server.enqueue(json(ShareFixtures.share(ShareFixtures.view(status = ShareView.LOCKED, revision = 5))))
        val payment = PaymentPayload(
            link = "https://paypal.me/dana/71500VND",
            qrPayload = null,
            details = "PayPal: paypal.me/dana",
            amountFilledIn = true,
        )
        val amounts = mapOf("me" to ShareAmount(44_000, null), "p1" to ShareAmount(99_000, payment), "p2" to ShareAmount(11_000, null))

        val view = shares.finalize(linked, "Dana", amounts, paidBy = "me")

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
        assertEquals("\"me\"", body["paidBy"].toString())
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
        server.dispatcher = recording { request ->
            when (request.path) {
                "/v1/shares/live" -> MockResponse().withWebSocketUpgrade(
                    sending("pong", """{"type":"share","share":${ShareFixtures.view(revision = 7)}}""", """{"type":"gone"}"""),
                )

                "/v1/shares/open" -> json(ShareFixtures.share(ShareFixtures.view(revision = 6)))

                else -> MockResponse().setResponseCode(404)
            }
        }

        val events = withTimeout(10_000) { shares.live(ShareFixtures.URL).toList() }

        assertEquals(
            "splitvibe.v1, ${ShareFixtures.TOKEN}",
            requests.first {
                it.path == "/v1/shares/live"
            }.getHeader("Sec-WebSocket-Protocol"),
        )
        assertEquals(LiveShare.Connected, events.first())
        assertTrue(events.filterIsInstance<LiveShare.Changed>().any { it.share.revision == 7L })
        assertEquals(LiveShare.Gone, events.last())
    }

    @Test
    fun `a dropped connection is opened again, and a link that no longer works ends it`() = runBlocking {
        val attempts = AtomicInteger()
        server.dispatcher = recording { request ->
            when (request.path) {
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

        assertEquals(LiveShare.Gone, events.last())
        assertTrue(LiveShare.Lost in events)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `a connection that stops answering pings is dropped and opened again`() = runBlocking {
        val attempts = AtomicInteger()
        val silent = ShareRepository(proxy.api, retryDelayMs = { 0 }, pingMs = 50)
        server.dispatcher = recording { request ->
            when (request.path) {
                // The first connection never answers, as one that died without closing does.
                "/v1/shares/live" -> if (attempts.incrementAndGet() == 1) {
                    MockResponse().withWebSocketUpgrade(ServerSide())
                } else {
                    MockResponse().setResponseCode(410)
                }

                "/v1/shares/open" -> json(ShareFixtures.share(ShareFixtures.view(revision = 6)))

                else -> MockResponse().setResponseCode(404)
            }
        }

        val events = withTimeout(10_000) { silent.live(ShareFixtures.URL).toList() }

        assertEquals(2, attempts.get())
        assertEquals(listOf(LiveShare.Connected), events.filter { it == LiveShare.Connected })
        assertEquals(LiveShare.Gone, events.last())
    }

    @Test
    fun `an answer the app cannot read drops only that connection`() = runBlocking {
        val attempts = AtomicInteger()
        server.dispatcher = recording { request ->
            when (request.path) {
                "/v1/shares/live" -> if (attempts.incrementAndGet() == 1) {
                    MockResponse().withWebSocketUpgrade(ServerSide())
                } else {
                    MockResponse().setResponseCode(410)
                }

                "/v1/shares/open" -> json("""{"share":5}""")

                else -> MockResponse().setResponseCode(404)
            }
        }

        val events = withTimeout(10_000) { shares.live(ShareFixtures.URL).toList() }

        assertEquals(listOf(LiveShare.Connected, LiveShare.Lost, LiveShare.Gone), events)
    }

    private fun recording(answer: (RecordedRequest) -> MockResponse) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            return answer(request)
        }
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

    /** Answers the app's close, so the server can stop once the test is over; says nothing otherwise. */
    private open class ServerSide : WebSocketListener() {
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        const val NOW = 1_763_200_000_000L
        const val CREATED_AT = 1_763_190_000_000L
        const val EXPIRES_AT = 4_102_444_800_000L
    }
}
