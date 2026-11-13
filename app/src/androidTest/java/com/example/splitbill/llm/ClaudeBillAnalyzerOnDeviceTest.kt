package com.example.splitbill.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.splitbill.ocr.OcrLine
import com.example.splitbill.ocr.OcrResult
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
 * Proves that the Anthropic SDK (a JVM library built on Jackson and reflection) really works on the
 * Android runtime: it must build the request and read both answers and errors on a device.
 */
@RunWith(AndroidJUnit4::class)
class ClaudeBillAnalyzerOnDeviceTest {

    private val server = MockWebServer()

    private val ocr = OcrResult(
        imageWidth = 1000,
        imageHeight = 1000,
        lines = listOf(
            OcrLine.upright("Pho bo", 40f, 300f, 300f, 340f),
            OcrLine.upright("65.000", 820f, 302f, 960f, 342f),
        ),
    )

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun requestIsBuiltAndAnswerIsParsedOnAndroid() = runBlocking {
        server.enqueue(
            json(
                """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                 "content": [{"type": "text", "text": "{\"status\": \"ok\", \"merchant\": \"Pho 24\", \"date\": \"\", \"currency\": \"VND\", \"items\": [{\"name\": \"Pho bo\", \"quantity\": 1, \"unit_price\": 65000, \"amount\": 65000}], \"charges\": [], \"total\": 65000, \"split\": {\"people\": [\"Minh\"], \"user_included\": true, \"people_count\": null, \"item_assignments\": []}, \"warnings\": [], \"message\": \"Read 1 item.\"}"}],
                 "stop_reason": "end_turn", "usage": {"input_tokens": 900, "output_tokens": 120}}
                """.trimIndent(),
            ),
        )

        val analysis = ClaudeBillAnalyzer("test-key", baseUrl = server.url("/").toString()).use { analyzer ->
            analyzer.analyze(ocr, "Split with Minh", localCurrency = "VND")
        }

        analysis as BillAnalysis.Ready
        assertEquals(65_000L, analysis.bill.totalMinor)
        assertEquals("VND", analysis.bill.currency.code)
        assertEquals(listOf("Minh"), analysis.splitHint.people)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("/v1/messages", request.requestUrl?.encodedPath)
        assertTrue(body, body.contains("\"model\":\"claude-opus-5-5\""))
        assertTrue(body, body.contains("\"fallbacks\":\"default\""))
        assertTrue(body, body.contains("\"type\":\"json_schema\""))
        assertTrue(body, body.contains("Split with Minh"))
    }

    @Test
    fun apiErrorsAreUnderstoodOnAndroid() = runBlocking {
        server.enqueue(json("""{"type": "error", "error": {"type": "authentication_error", "message": "invalid x-api-key"}}""").setResponseCode(401))

        try {
            ClaudeBillAnalyzer("wrong-key", baseUrl = server.url("/").toString()).use { it.analyze(ocr, "", localCurrency = "VND") }
            fail("A rejected key must not look like a readable bill")
        } catch (e: BillAnalyzerException) {
            assertEquals(BillAnalyzerException.Kind.INVALID_API_KEY, e.kind)
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse().setHeader("content-type", "application/json").setBody(body)
}
