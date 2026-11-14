package com.dantech.splitvibe.llm

import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.ocr.OcrLine
import com.dantech.splitvibe.ocr.OcrResult
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

/**
 * Exercises the real Anthropic SDK client against a local HTTP server, so these tests pin down the
 * exact request the app sends and how every kind of API answer is handled.
 */
class ClaudeBillAnalyzerTest {

    private val server = MockWebServer()
    private lateinit var analyzer: ClaudeBillAnalyzer

    private val ocr = OcrResult(
        imageWidth = 1000,
        imageHeight = 1000,
        lines = listOf(
            OcrLine.upright("Pho bo", 40f, 300f, 300f, 340f),
            OcrLine.upright("65.000", 820f, 302f, 960f, 342f),
            OcrLine.upright("Tong cong", 40f, 480f, 330f, 525f),
            OcrLine.upright("65.000", 820f, 478f, 960f, 526f),
        ),
    )

    private val billAnswer = """
        {"status": "ok", "merchant": "Pho 24", "date": "", "currency": "VND",
         "items": [{"name": "Pho bo", "quantity": 1, "unit_price": 65000, "amount": 65000}],
         "charges": [], "total": 65000,
         "split": {"people": [], "user_included": true, "people_count": null, "item_assignments": []},
         "warnings": [], "message": "Read 1 item."}
    """.trimIndent()

    @Before
    fun setUp() {
        server.start()
        analyzer = ClaudeBillAnalyzer(apiKey = "test-key", baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        analyzer.close()
        server.shutdown()
    }

    @Test
    fun `sends OCR rows, the instruction and the answer schema, then parses the bill`() = runTest {
        server.enqueue(messageResponse(billAnswer))

        val analysis = analyzer.analyze(ocr, "Split with Minh", "VND") as BillAnalysis.Ready

        assertEquals(65_000L, analysis.bill.totalMinor)
        assertEquals("Pho 24", analysis.bill.merchant)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/messages", request.requestUrl?.encodedPath)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(request.getHeader("anthropic-beta").orEmpty().contains("server-side-fallback-2026-07-01"))

        val body = request.json()
        assertEquals("claude-opus-5-5", body.string("model"))
        assertEquals("16000", body.getValue("max_tokens").jsonPrimitive.content)
        assertEquals("default", body.string("fallbacks"))
        assertEquals(BillPrompt.SYSTEM, body.string("system"))
        assertFalse("sampling parameters are rejected by current models", "temperature" in body)
        assertFalse("thinking must stay adaptive", "thinking" in body)

        val content = body.getValue("messages").jsonArray.single().jsonObject.getValue("content").jsonPrimitive.content
        assertTrue(content, content.contains("""1 | [0,0,283,177] "Pho bo" | [848,9,1000,186] "65.000""""))
        assertTrue(
            content,
            content.endsWith(
                "<app_settings>\nlocal_currency: VND\n</app_settings>\n\n<user_instruction>\nSplit with Minh\n</user_instruction>",
            ),
        )

        val outputConfig = body.getValue("output_config").jsonObject
        assertEquals("medium", outputConfig.string("effort"))
        val format = outputConfig.getValue("format").jsonObject
        assertEquals("json_schema", format.string("type"))
        val schema = format.getValue("schema").jsonObject
        assertEquals("false", schema.getValue("additionalProperties").jsonPrimitive.content)
        assertEquals(
            listOf(
                "status", "merchant", "date", "currency", "amount_decimals", "items",
                "charges", "total", "split", "warnings", "message",
            ),
            schema.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `retries without the refusal fallback when the API does not accept it`() = runTest {
        server.enqueue(
            errorResponse(
                400,
                "invalid_request_error",
                "Unexpected value(s) `server-side-fallback-2026-07-01` for the `anthropic-beta` header.",
            ),
        )
        server.enqueue(messageResponse(billAnswer))

        val analysis = analyzer.analyze(ocr, "", "VND")

        assertTrue(analysis is BillAnalysis.Ready)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val retry = server.takeRequest()
        assertNull(retry.getHeader("anthropic-beta"))
        assertFalse("fallbacks" in retry.json())
        assertTrue(retry.json().getValue("messages").toString().contains(BillPrompt.DEFAULT_INSTRUCTION))
    }

    @Test
    fun `refusal fallback that was turned down is not requested again`() = runTest {
        server.enqueue(errorResponse(400, "invalid_request_error", "fallbacks: not supported for this model."))
        server.enqueue(messageResponse(billAnswer))
        server.enqueue(messageResponse(billAnswer))

        analyzer.analyze(ocr, "", "VND")
        analyzer.analyze(ocr, "", "VND")

        assertEquals(3, server.requestCount)
        repeat(2) { server.takeRequest() }
        assertFalse("the third request must go straight out without the fallback", "fallbacks" in server.takeRequest().json())
    }

    @Test
    fun `answer served by a fallback model is read like any other`() = runTest {
        server.enqueue(
            messageResponse(billAnswer) {
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "fallback")
                                putJsonObject("from") { put("model", "claude-opus-5-5") }
                                putJsonObject("to") { put("model", "claude-opus-4-8") }
                            },
                        )
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", billAnswer)
                            },
                        )
                    },
                )
            },
        )

        val analysis = analyzer.analyze(ocr, "", "VND") as BillAnalysis.Ready

        assertEquals(65_000L, analysis.bill.totalMinor)
    }

    @Test
    fun `answer that takes too long is reported as a timeout after one retry`() = runTest {
        repeat(2) { server.enqueue(messageResponse(billAnswer).setHeadersDelay(5, TimeUnit.SECONDS)) }
        // The address is spelled out: on a machine where "localhost" also means ::1, the retry would
        // go to that address, where the test server does not listen, and fail to connect instead.
        val impatient =
            ClaudeBillAnalyzer(apiKey = "test-key", baseUrl = "http://127.0.0.1:${server.port}/", requestTimeout = Duration.ofMillis(300))

        val error = try {
            failure { impatient.analyze(ocr, "", "VND") }
        } finally {
            impatient.close()
        }

        // The chain of causes is the message, so a failure on another machine shows what was thrown there.
        val causes = generateSequence<Throwable>(error) { it.cause }.joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
        assertEquals(causes, Kind.TIMEOUT, error.kind)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `request that stays invalid reports what the API said`() = runTest {
        repeat(2) {
            server.enqueue(errorResponse(400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API."))
        }

        val error = failure { analyzer.analyze(ocr, "", "VND") }

        assertEquals(Kind.BAD_REQUEST, error.kind)
        assertTrue(error.message, error.message!!.contains("Your credit balance is too low"))
    }

    @Test
    fun `rejected key and unknown model are told apart`() = runTest {
        server.enqueue(errorResponse(401, "authentication_error", "invalid x-api-key"))
        assertEquals(Kind.INVALID_API_KEY, failure { analyzer.analyze(ocr, "", "VND") }.kind)

        server.enqueue(errorResponse(404, "not_found_error", "model: claude-opus-5-5"))
        assertEquals(Kind.MODEL_NOT_FOUND, failure { analyzer.analyze(ocr, "", "VND") }.kind)

        server.enqueue(errorResponse(403, "permission_error", "Not allowed."))
        assertEquals(Kind.NOT_ALLOWED, failure { analyzer.analyze(ocr, "", "VND") }.kind)
    }

    @Test
    fun `refusal is reported instead of being parsed as a bill`() = runTest {
        server.enqueue(
            messageResponse(text = null, stopReason = "refusal") {
                putJsonObject("stop_details") {
                    put("type", "refusal")
                    put("category", "cyber")
                    put("explanation", "This request was declined.")
                }
            },
        )

        val error = failure { analyzer.analyze(ocr, "", "VND") }

        assertEquals(Kind.REFUSED, error.kind)
        assertTrue(error.message, error.message!!.contains("This request was declined."))
    }

    @Test
    fun `answer cut off by the token limit is not trusted`() = runTest {
        server.enqueue(messageResponse("""{"status": "ok", "items": [""", stopReason = "max_tokens"))

        assertEquals(Kind.BAD_RESPONSE, failure { analyzer.analyze(ocr, "", "VND") }.kind)
    }

    @Test
    fun `photo that is not a bill comes back as rejected`() = runTest {
        server.enqueue(
            messageResponse("""{"status": "not_a_bill", "items": [], "charges": [], "total": 0, "message": "This is a street sign."}"""),
        )

        assertEquals(BillAnalysis.Rejected("This is a street sign."), analyzer.analyze(ocr, "", "VND"))
    }

    private suspend fun failure(block: suspend () -> Unit): BillAnalyzerException = try {
        block()
        throw AssertionError("Expected a BillAnalyzerException")
    } catch (e: BillAnalyzerException) {
        e
    }

    private fun messageResponse(
        text: String?,
        stopReason: String = "end_turn",
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ): MockResponse {
        val body = buildJsonObject {
            put("id", "msg_test")
            put("type", "message")
            put("role", "assistant")
            put("model", "claude-opus-5-5")
            put(
                "content",
                buildJsonArray {
                    if (text != null) {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", text)
                            },
                        )
                    }
                },
            )
            put("stop_reason", stopReason)
            putJsonObject("usage") {
                put("input_tokens", 1200)
                put("output_tokens", 300)
            }
            extra()
        }
        return MockResponse().setHeader("content-type", "application/json").setBody(body.toString())
    }

    private fun errorResponse(status: Int, type: String, message: String): MockResponse {
        val body = buildJsonObject {
            put("type", "error")
            putJsonObject("error") {
                put("type", type)
                put("message", message)
            }
        }
        return MockResponse().setResponseCode(status).setHeader("content-type", "application/json").setBody(body.toString())
    }

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body.clone().readUtf8()).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
