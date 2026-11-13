package com.example.splitbill.llm

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.example.splitbill.llm.BillAnalyzerException.Kind
import com.example.splitbill.ocr.OcrLayoutEncoder
import com.example.splitbill.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.InterruptedIOException
import java.time.Duration

/**
 * Reads a bill with Claude through the official Anthropic SDK.
 *
 * Only the OCR text and the user's instruction are sent; the photo stays on the device. The answer
 * is constrained to [BillSchema] with structured outputs, so it always parses.
 *
 * @param baseUrl overrides the API endpoint. Tests point it at a local server; a production app
 *   would point it at its own backend instead of shipping an API key in the app.
 * @param requestTimeout how long one attempt may take before it is abandoned.
 */
class ClaudeBillAnalyzer(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val baseUrl: String? = null,
    private val requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
) : BillAnalyzer, AutoCloseable {

    // Built on first use, on the background thread of the first request: setting up the SDK's JSON
    // machinery takes long enough on a phone that it must stay off the main thread.
    private val lazyClient = lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .apply { if (baseUrl != null) baseUrl(baseUrl) }
            .timeout(requestTimeout)
            // One retry covers a dropped connection or a brief overload. More would make a person
            // wait several minutes, and every retried attempt that reached the model is billed.
            .maxRetries(MAX_RETRIES)
            .build()
    }
    private val client: AnthropicClient by lazyClient

    private val answerFormat: BetaJsonOutputFormat by lazy {
        BetaJsonOutputFormat.builder()
            .schema(
                BetaJsonOutputFormat.Schema.builder()
                    .putAllAdditionalProperties(BillSchema.SCHEMA.mapValues { JsonValue.from(it.value) })
                    .build(),
            )
            .build()
    }

    /** Cleared when the API turns the refusal fallback down, so later requests do not ask again. */
    @Volatile
    private var refusalFallbackAccepted = true

    override suspend fun analyze(ocr: OcrResult, instruction: String): BillAnalysis {
        val userMessage = BillPrompt.userMessage(OcrLayoutEncoder.encode(ocr), instruction)
        // The SDK call blocks. runInterruptible interrupts it when the caller is cancelled.
        return runInterruptible(Dispatchers.IO) { interpret(send(userMessage)) }
    }

    override fun close() {
        if (lazyClient.isInitialized()) client.close()
    }

    private fun send(userMessage: String): BetaMessage =
        try {
            if (refusalFallbackAccepted) sendWithRefusalFallback(userMessage) else create(userMessage, refusalFallback = false)
        } catch (e: UnauthorizedException) {
            throw BillAnalyzerException(Kind.INVALID_API_KEY, "The Anthropic API key was rejected. Check it in Settings.", e)
        } catch (e: PermissionDeniedException) {
            throw BillAnalyzerException(Kind.NOT_ALLOWED, "This API key is not allowed to do this: ${e.detail()}", e)
        } catch (e: NotFoundException) {
            throw BillAnalyzerException(Kind.MODEL_NOT_FOUND, "The model \"$model\" is not available to this API key. Check the model name in Settings.", e)
        } catch (e: RateLimitException) {
            throw BillAnalyzerException(Kind.RATE_LIMITED, "The Anthropic API is rate limiting this key. Wait a moment and try again.", e)
        } catch (e: BadRequestException) {
            throw BillAnalyzerException(Kind.BAD_REQUEST, "The Anthropic API rejected the request: ${e.detail()}", e)
        } catch (e: AnthropicServiceException) {
            throw BillAnalyzerException(Kind.SERVICE_ERROR, "The Anthropic API had a problem (HTTP ${e.statusCode()}). Try again in a moment.", e)
        } catch (e: AnthropicIoException) {
            if (e.isTimeout()) {
                throw BillAnalyzerException(Kind.TIMEOUT, "Claude took too long to answer. Please try again.", e)
            }
            throw BillAnalyzerException(Kind.NETWORK, "Could not reach the Anthropic API. Check the internet connection and try again.", e)
        } catch (e: AnthropicException) {
            throw BillAnalyzerException(Kind.BAD_RESPONSE, "The Anthropic API sent an answer that could not be read. Please try again.", e)
        }

    /**
     * The refusal fallback is a beta feature and the only optional part of the request. If the
     * account or the chosen model does not accept it, the bill is still readable without it, so the
     * request is sent again without it. When that works, the fallback was the problem and is left
     * out from then on; when it fails too, the error is about the request itself and is reported.
     */
    private fun sendWithRefusalFallback(userMessage: String): BetaMessage =
        try {
            create(userMessage, refusalFallback = true)
        } catch (rejected: BadRequestException) {
            val message = create(userMessage, refusalFallback = false)
            refusalFallbackAccepted = false
            message
        }

    private fun create(userMessage: String, refusalFallback: Boolean): BetaMessage {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
            .system(BillPrompt.SYSTEM)
            .addUserMessage(userMessage)
            .outputConfig(BetaOutputConfig.builder().effort(EFFORT).format(answerFormat).build())
        if (refusalFallback) {
            // If a safety classifier declines the request, the API re-runs it on another model
            // instead of returning a refusal.
            builder.addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01).fallbacksDefault()
        }
        return client.beta().messages().create(builder.build())
    }

    private fun interpret(message: BetaMessage): BillAnalysis {
        val stopReason = message.stopReason().orElse(null)
        if (stopReason == BetaStopReason.REFUSAL) {
            val explanation = message.stopDetails().flatMap { it.explanation() }.orElse("")
            throw BillAnalyzerException(Kind.REFUSED, "Claude declined to read this bill. $explanation".trim())
        }
        if (stopReason == BetaStopReason.MAX_TOKENS) {
            throw BillAnalyzerException(Kind.BAD_RESPONSE, "Claude's answer was cut off because the bill is very long. Try photographing it in two parts.")
        }
        // Text blocks only: a response served by a fallback model also carries a marker block.
        val answer = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        if (answer.isBlank()) {
            throw BillAnalyzerException(Kind.BAD_RESPONSE, "Claude returned an empty answer. Please try again.")
        }
        return BillAnalysisParser.parse(answer)
    }

    private fun AnthropicServiceException.detail(): String =
        apiMessage().orElse(null) ?: message ?: "HTTP ${statusCode()}"

    /** A timeout surfaces as an interrupted I/O exception somewhere in the cause chain. */
    private fun Throwable.isTimeout(): Boolean =
        generateSequence(this) { it.cause }.any { it is InterruptedIOException }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        /** A bill usually takes 5 to 15 seconds; this leaves room for a very long one. */
        val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofMinutes(2)

        /** Room for the model's reasoning plus the JSON answer of a long bill. */
        private const val MAX_TOKENS = 16_000L
        private const val MAX_RETRIES = 1
        private val EFFORT = BetaOutputConfig.Effort.MEDIUM
    }
}
