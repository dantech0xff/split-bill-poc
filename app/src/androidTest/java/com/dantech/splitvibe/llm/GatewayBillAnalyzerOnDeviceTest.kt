package com.dantech.splitvibe.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.ocr.OcrLine
import com.dantech.splitvibe.ocr.OcrResult
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves on the Android runtime what the local tests cannot: OkHttp reads the streamed answer line
 * by line, and the session and the account's address survive the real Keystore encryption, read
 * back by the next start of the app.
 */
@RunWith(AndroidJUnit4::class)
class GatewayBillAnalyzerOnDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var account: AccountStore
    private lateinit var analyzer: GatewayBillAnalyzer

    private val ocr = OcrResult(
        imageWidth = 1000,
        imageHeight = 1000,
        lines = listOf(
            OcrLine.upright("Pho bo", 40f, 300f, 300f, 340f),
            OcrLine.upright("65.000", 820f, 302f, 960f, 342f),
        ),
    )

    @Before
    fun setUp() {
        server.start()
        account = AccountStore(context, proxySetUp = true)
        analyzer = analyzerFor(account)
    }

    @After
    fun tearDown() {
        account.clearSession()
        server.shutdown()
    }

    @Test
    fun theStreamedAnswerIsReadOnAndroid() = runBlocking {
        account.saveSession(
            Session("signed-session", expiresAt = System.currentTimeMillis() + 60_000),
            signedInAs = "minh.tran@example.com",
        )
        // As at the next start of the app: both are decrypted from what was stored.
        val restarted = AccountStore(context, proxySetUp = true)
        assertEquals("minh.tran@example.com", restarted.account.value.signedInAs)
        server.enqueue(
            ndjson(
                """{"type":"wait"}""",
                """{"type":"result","answer":$ANSWER,"usage":{"limit":10,"used":1,"remaining":9,"period":"2026-11","resetsAt":1798761600000}}""",
            ),
        )

        val analysis = analyzerFor(restarted).analyze(listOf(ocr), "Split with Minh", localCurrency = "VND")

        analysis as BillAnalysis.Ready
        assertEquals(65_000L, analysis.bill.totalMinor)
        assertEquals(listOf("Minh"), analysis.splitHint.people)
        assertEquals(BigDecimal(9), restarted.account.value.callsLeft?.remaining)
        val request = server.takeRequest()
        assertEquals("/v1/bill-analysis", request.requestUrl?.encodedPath)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("Split with Minh"))
    }

    @Test
    fun aRefusalOfTheProxyIsUnderstoodOnAndroid() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("content-type", "application/json").setBody("""{"error":"ai_off"}"""))

        try {
            analyzer.analyze(listOf(ocr), "", localCurrency = "VND")
            fail("A proxy with AI switched off must not look like a readable bill")
        } catch (e: BillAnalyzerException) {
            assertEquals(BillAnalyzerException.Kind.AI_OFF, e.kind)
        }
    }

    private fun analyzerFor(store: AccountStore) =
        GatewayBillAnalyzer(ProxyApi(server.url("/").toString(), store, appVersion = 1), store, canSignIn = true)

    private fun ndjson(vararg lines: String): MockResponse =
        MockResponse().setHeader("content-type", "application/x-ndjson; charset=utf-8").setBody(lines.joinToString("\n", postfix = "\n"))

    private companion object {
        const val ANSWER = """{"status":"ok","merchant":"Pho 24","date":"","currency":"VND","amount_decimals":0,""" +
            """"items":[{"name":"Pho bo","quantity":1,"unit_price":65000,"amount":65000}],"charges":[],"total":65000,""" +
            """"split":{"people":["Minh"],"user_included":true,"people_count":null,"item_assignments":[]},""" +
            """"warnings":[],"message":"Read 1 item."}"""
    }
}
