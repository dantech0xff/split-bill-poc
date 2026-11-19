package com.dantech.splitvibe.llm

import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.text.text
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
    api: ProxyApi,
    account: AccountStore,
    /** Whether this build can sign in with Google, which is how a person gets more calls. */
    canSignIn: Boolean,
    /** Whether this build sells Pro, which gives the most calls. */
    sellsPro: Boolean,
) : BillAnalyzer {

    private val call = ProxyModelCall(api, account, canSignIn, sellsPro, WORDS, api::readBill)

    override suspend fun analyze(pages: List<OcrResult>, instruction: String, localCurrency: String): BillAnalysis = call.ask(
        content = {
            val document = OcrLayoutEncoder.encode(pages)
            // The proxy refuses longer text. Saying so here tells the person what to change.
            if (document.length > MAX_DOCUMENT_CHARS) {
                throw modelCallFailure(Kind.BAD_REQUEST, text(if (pages.size > 1) R.string.read_too_many_pages else R.string.read_too_long))
            }
            buildJsonObject {
                put("schemaVersion", 1)
                put("ocrDocument", document)
                put("instruction", instruction.trim())
                put("localCurrency", localCurrency)
            }
        },
        read = { answer -> BillAnalysisParser.parse(answer.toString(), localCurrency) },
    )

    private companion object {
        /** The longest text of a bill the AI proxy takes. */
        const val MAX_DOCUMENT_CHARS = 20_000

        val WORDS = ModelCallWords(
            notSetUp = checkNotNull(AiStatus.NOT_SET_UP.notice),
            switchedOff = checkNotNull(AiStatus.OFF.notice),
            updateRequired = text(R.string.read_update_required),
            tooMany = text(R.string.read_too_many),
            failedTries = text(R.string.read_failed_tries),
            refused = text(R.string.read_refused),
            tooLong = text(R.string.read_too_long),
            tooSlow = text(R.string.read_too_slow),
            notTaken = text(R.string.read_not_taken),
            cost = text(R.string.read_cost),
        )
    }
}
