package com.dantech.splitvibe.data.fx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestProxy
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The day's exchange rates: fetched from the proxy, kept for use without a connection. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FxRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val file = File(context.filesDir, "fx-rates-test.json")
    private var now = 1_000_000L

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.shutdown()
        file.delete()
    }

    private fun repository() = FxRepository(TestProxy(server, context).api, file) { now }

    @Test
    fun `the rate between two currencies comes from the proxy's rates against the euro`() = runBlocking {
        server.enqueue(rates())

        val rate = checkNotNull(repository().rate("USD", "VND"))

        assertEquals(0, BigDecimal("25350").compareTo(rate.rate))
        assertEquals("2026-11-13", rate.date)
        assertEquals("Frankfurter", rate.source)
        assertEquals("/v1/fx-rates", server.takeRequest().path)
    }

    @Test
    fun `rates kept less than an hour are used without asking again`() = runBlocking {
        server.enqueue(rates())
        val fx = repository()
        fx.rate("USD", "VND")

        now += 59 * 60 * 1000L
        fx.rate("EUR", "VND")

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `when the rates cannot be fetched, those kept last time are used, and fetching waits a few minutes`() = runBlocking {
        server.enqueue(rates())
        repository().rate("USD", "VND")
        now += 2 * 60 * 60 * 1000L
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"fx_unavailable"}"""))

        // A new start of the app reads the rates kept on the phone.
        val fx = repository()
        val rate = fx.rate("USD", "VND")
        fx.rate("USD", "VND")

        assertEquals("2026-11-13", rate?.date)
        assertEquals("the second read does not try again at once", 2, server.requestCount)
    }

    @Test
    fun `without any rates there is no rate, and a rate the source lacks is none either`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"fx_unavailable"}"""))
        assertNull(repository().rate("USD", "VND"))

        now += 10 * 60 * 1000L
        server.enqueue(rates())
        assertNull(repository().rate("USD", "XAU"))
    }

    @Test
    fun `a rate that is not a positive number with a day is left out`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"base":"EUR","source":"Frankfurter","fetchedAt":1,"rates":{"EUR":{"rate":1,"date":"2026-11-14"},""" +
                    """"USD":{"rate":"1.08","date":"2026-11-14"},"VND":{"rate":-2,"date":"2026-11-14"},"THB":{"rate":38.1}}}""",
            ),
        )

        val snapshot = checkNotNull(repository().snapshot())

        assertEquals(setOf("EUR"), snapshot.rates.keys)
    }

    private fun rates() = MockResponse()
        .setHeader("content-type", "application/json")
        .setBody(
            """{"base":"EUR","source":"Frankfurter","fetchedAt":1763130000000,"rates":{""" +
                """"EUR":{"rate":1,"date":"2026-11-14"},"USD":{"rate":1.08,"date":"2026-11-14"},""" +
                """"VND":{"rate":27378,"date":"2026-11-13"},"THB":{"rate":38.12,"date":"2026-11-14"}}}""",
        )
}
