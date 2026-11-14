package com.dantech.splitvibe.llm

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import com.dantech.splitvibe.ocr.OcrResult
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads a bill through the app's AI proxy, which holds the Anthropic key, builds the prompt and
 * counts each person's calls. Only the OCR text and the user's instruction are sent; the photo
 * stays on the device.
 *
 * When a read fails and the same bill is read again with the same instruction, the request keeps
 * its id, so the proxy does not charge a second call for a read that was cut off.
 */
class GatewayBillAnalyzer(private val api: ProxyApi, private val account: AccountStore) : BillAnalyzer {

    /** The content and request id of the last read, when it failed. */
    @Volatile
    private var failedRead: Pair<List<String>, String>? = null

    override suspend fun analyze(ocr: OcrResult, instruction: String, localCurrency: String): BillAnalysis {
        if (!api.isSetUp) throw BillAnalyzerException(Kind.AI_OFF, checkNotNull(AiStatus.NOT_SET_UP.notice))
        val content = listOf(OcrLayoutEncoder.encode(ocr), instruction.trim(), localCurrency)
        val requestId = failedRead?.takeIf { it.first == content }?.second ?: UUID.randomUUID().toString()
        val body = buildJsonObject {
            put("schemaVersion", 1)
            put("requestId", requestId)
            put("ocrDocument", content[0])
            put("instruction", content[1])
            put("localCurrency", content[2])
        }

        val reply = try {
            api.readBill(body)
        } catch (e: ProxyException) {
            // A request that has run as often as it may needs a new id to be tried again.
            failedRead = if (e.code == "retry_limit") null else content to requestId
            e.callsLeft?.let(account::onCallsLeft)
            // The proxy checks its AI switch first, so any other answer of its own means AI is on.
            if (e.status == 200 || e.status in 400..499) account.onAiEnabled(true)
            throw failureOf(e)
        } catch (e: IOException) {
            failedRead = content to requestId
            throw if (e is InterruptedIOException) {
                BillAnalyzerException(Kind.TIMEOUT, "Reading the bill took too long. Please try again.", e)
            } else {
                BillAnalyzerException(Kind.NETWORK, "Could not reach Split Vibe's server. Check the internet connection and try again.", e)
            }
        }
        failedRead = null
        reply.callsLeft?.let(account::onCallsLeft)
        account.onAiEnabled(true)
        return BillAnalysisParser.parse(reply.answer.toString(), localCurrency)
    }

    private fun failureOf(e: ProxyException): BillAnalyzerException = when (e.code) {
        "quota_exhausted" -> BillAnalyzerException(Kind.QUOTA_EXHAUSTED, quotaMessage(e.callsLeft), e)

        "session" -> {
            account.clearSession()
            BillAnalyzerException(Kind.SIGN_IN_EXPIRED, "Your sign-in has run out. Sign in again in Settings to use your monthly reads.", e)
        }

        "ai_off" -> {
            account.onAiEnabled(false)
            BillAnalyzerException(Kind.AI_OFF, checkNotNull(AiStatus.OFF.notice), e)
        }

        "update_required" -> BillAnalyzerException(Kind.UPDATE_REQUIRED, "Update Split Vibe to keep reading bills.", e)

        "rate_limited", "spend_limited", "retry_limit" ->
            BillAnalyzerException(Kind.RATE_LIMITED, "Too many bills were read just now. ${waitHint(e.retryAfterSeconds)}", e)

        "busy" -> BillAnalyzerException(Kind.BUSY, "The AI is busy right now. ${waitHint(e.retryAfterSeconds)}", e)

        "refused" -> BillAnalyzerException(Kind.REFUSED, "The AI declined to read this bill.", e)

        "too_long" -> BillAnalyzerException(
            Kind.BAD_RESPONSE,
            "The bill is too long to read in one go. Try photographing it in two parts.",
            e,
        )

        "bad_response" -> BillAnalyzerException(Kind.BAD_RESPONSE, "The AI's answer could not be read. Please try again.", e)

        "timeout" -> BillAnalyzerException(Kind.TIMEOUT, "Reading the bill took too long. Please try again.", e)

        "input", "install" -> BillAnalyzerException(Kind.BAD_REQUEST, "This bill could not be sent to be read. Try another photo.", e)

        else -> BillAnalyzerException(Kind.SERVICE_ERROR, "Split Vibe's server had a problem. Try again in a moment.", e)
    }

    private fun quotaMessage(callsLeft: CallsLeft?): String {
        val resetsAt = callsLeft?.resetsAt
        if (callsLeft == null || resetsAt == null) {
            val free = callsLeft?.limit?.let { "$it " }.orEmpty()
            return "You have used your ${free}free reads. Sign in with Google in Settings to get more each month."
        }
        val date = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH).format(Instant.ofEpochMilli(resetsAt).atZone(ZoneOffset.UTC))
        return "You have used this month's ${callsLeft.limit} reads. They come back on $date."
    }

    private fun waitHint(seconds: Long?): String {
        if (seconds == null) return "Try again later."
        val minutes = (seconds + 59) / 60
        return when {
            minutes <= 1 -> "Try again in a minute."
            minutes < 120 -> "Try again in about $minutes minutes."
            else -> "Try again in about ${(minutes + 59) / 60} hours."
        }
    }
}
