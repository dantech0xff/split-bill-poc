package com.dantech.splitvibe.llm

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.proxy.AnswerTimeoutException
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import com.dantech.splitvibe.ocr.OcrResult
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The longest instruction the AI proxy takes. The app and the proxy both count UTF-16 characters. */
const val MAX_INSTRUCTION_CHARS = 1_000

/**
 * Reads a bill through the app's AI proxy, which holds the Anthropic key, builds the prompt and
 * counts each person's calls. Only the OCR text and the user's instruction are sent; the photo
 * stays on the device.
 *
 * A read keeps its request id until it ends in an answer the app can use. Reading the same bill
 * again with the same instruction after a cancel, a cut-off answer or an answer that could not be
 * used is therefore the same request, which the proxy does not charge twice.
 */
class GatewayBillAnalyzer(
    private val api: ProxyApi,
    private val account: AccountStore,
    /** Whether this build can sign in with Google, which is how a person gets more reads. */
    private val canSignIn: Boolean,
) : BillAnalyzer {

    /** The content and request id of the latest read that has not ended in a usable answer. */
    @Volatile
    private var unfinishedRead: Pair<List<String>, String>? = null

    override suspend fun analyze(pages: List<OcrResult>, instruction: String, localCurrency: String): BillAnalysis {
        if (!api.isSetUp) throw BillAnalyzerException(Kind.AI_OFF, checkNotNull(AiStatus.NOT_SET_UP.notice))
        val content = listOf(OcrLayoutEncoder.encode(pages), instruction.trim(), localCurrency)
        // The proxy refuses longer text. Saying so here tells the person what to change.
        if (content[0].length >
            MAX_DOCUMENT_CHARS
        ) {
            throw BillAnalyzerException(Kind.BAD_REQUEST, if (pages.size > 1) TOO_MANY_PAGES else TOO_LONG)
        }
        val requestId = unfinishedRead?.takeIf { it.first == content }?.second ?: UUID.randomUUID().toString()
        // Kept from before the request goes out, so that a read cancelled while it waits keeps its id too.
        unfinishedRead = content to requestId
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
            // Under its old id the request cannot work again: it has run as often as it may, or the
            // proxy no longer takes it for this bill, for example after a change to the prompt.
            if (e.code == RETRY_LIMIT || e.code == INPUT) unfinishedRead = null
            e.callsLeft?.let(account::onCallsLeft)
            // The proxy checks its AI switch first, so any other answer of its own means AI is on. A
            // refused session says nothing about that: the app refuses a session that ran out itself.
            if (e.code != ProxyApi.SESSION_REFUSED && (e.status == 200 || e.status in 400..499)) account.onAiEnabled(true)
            throw failureOf(e)
        } catch (e: AnswerTimeoutException) {
            throw BillAnalyzerException(Kind.TIMEOUT, "Reading the bill took too long. Please try again.", e)
        } catch (e: IOException) {
            throw BillAnalyzerException(
                Kind.NETWORK,
                "Could not reach Split Vibe's server. Check the internet connection and try again.",
                e,
            )
        }
        reply.callsLeft?.let(account::onCallsLeft)
        account.onAiEnabled(true)
        return BillAnalysisParser.parse(reply.answer.toString(), localCurrency).also { unfinishedRead = null }
    }

    private fun failureOf(e: ProxyException): BillAnalyzerException = when (e.code) {
        "quota_exhausted" -> BillAnalyzerException(Kind.QUOTA_EXHAUSTED, quotaMessage(e.callsLeft), e)

        ProxyApi.SESSION_REFUSED -> {
            account.expireSession()
            BillAnalyzerException(Kind.SIGN_IN_EXPIRED, "Your sign-in has run out. Sign in again in Settings to use your monthly reads.", e)
        }

        "ai_off" -> {
            account.onAiEnabled(false)
            BillAnalyzerException(Kind.AI_OFF, checkNotNull(AiStatus.OFF.notice), e)
        }

        "update_required" -> BillAnalyzerException(Kind.UPDATE_REQUIRED, "Update Split Vibe to keep reading bills.", e)

        "rate_limited", "spend_limited" ->
            BillAnalyzerException(Kind.RATE_LIMITED, "Too many bills were read just now. ${waitHint(e.retryAfterSeconds)}", e)

        // The next try is a new request, which can run at once.
        RETRY_LIMIT -> BillAnalyzerException(Kind.SERVICE_ERROR, "Reading this bill did not work after several tries. Please try again.", e)

        "busy" -> BillAnalyzerException(Kind.BUSY, "The AI is busy right now. ${waitHint(e.retryAfterSeconds)}", e)

        "refused" -> BillAnalyzerException(Kind.REFUSED, "The AI declined to read this bill.", e)

        "too_long" -> BillAnalyzerException(Kind.BAD_RESPONSE, TOO_LONG, e)

        "bad_response" -> BillAnalyzerException(Kind.BAD_RESPONSE, "The AI's answer could not be read. Please try again.", e)

        "timeout" -> BillAnalyzerException(Kind.TIMEOUT, "Reading the bill took too long. Please try again.", e)

        INPUT, "install" -> BillAnalyzerException(Kind.BAD_REQUEST, "This bill could not be sent to be read. Please try again.", e)

        else -> BillAnalyzerException(Kind.SERVICE_ERROR, "Split Vibe's server had a problem. Try again in a moment.", e)
    }

    private fun quotaMessage(callsLeft: CallsLeft?): String {
        val date = callsLeft?.comesBackOn
        if (callsLeft != null && date != null) return "You have used this month's ${callsLeft.limit} reads. They come back on $date."
        val free = callsLeft?.limit?.let { "$it " }.orEmpty()
        val more = if (canSignIn) " Sign in with Google in Settings to get more each month." else ""
        return "You have used your ${free}free reads.$more"
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

    private companion object {
        /** The longest text of a bill the AI proxy takes. */
        const val MAX_DOCUMENT_CHARS = 20_000
        const val RETRY_LIMIT = "retry_limit"
        const val INPUT = "input"
        const val TOO_LONG =
            "This bill holds too much text to read in one go. Photograph only the lines with the items and totals, or type it in by hand."
        const val TOO_MANY_PAGES = "These pages hold too much text to read in one go. Try fewer pages at a time."
    }
}
