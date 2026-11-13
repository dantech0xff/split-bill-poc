package com.example.splitbill.llm

import com.example.splitbill.domain.Bill
import com.example.splitbill.domain.SplitHint
import com.example.splitbill.ocr.OcrResult

/**
 * Turns the OCR output of a bill photo plus the user's instruction into a structured bill.
 *
 * The app only depends on this interface. [ClaudeBillAnalyzer] calls the Anthropic API directly,
 * which is fine for a proof of concept; a production app would implement this against its own
 * backend so the API key never ships inside the app.
 */
interface BillAnalyzer {
    /**
     * @param localCurrency ISO 4217 code of the currency the user's payment app works in, used
     *   when the bill does not show its own.
     * @throws BillAnalyzerException when the model could not be reached or gave no usable answer.
     */
    suspend fun analyze(ocr: OcrResult, instruction: String, localCurrency: String): BillAnalysis
}

sealed interface BillAnalysis {
    /** The bill was read. [message] is a one-line summary for the user. */
    data class Ready(val bill: Bill, val splitHint: SplitHint, val message: String) : BillAnalysis

    /** The photo is not a bill, or too little of it could be read. [reason] is shown to the user. */
    data class Rejected(val reason: String) : BillAnalysis
}

class BillAnalyzerException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        /** The API rejected the key. */
        INVALID_API_KEY,
        /** The key is valid but may not use this model or feature. */
        NOT_ALLOWED,
        /** The configured model id does not exist or is not available to this account. */
        MODEL_NOT_FOUND,
        RATE_LIMITED,
        /** The request was rejected as invalid (this is also how the API reports "no credit left"). */
        BAD_REQUEST,
        /** The API answered with a server-side error or is overloaded. */
        SERVICE_ERROR,
        /** No usable connection to the API. */
        NETWORK,
        /** The API was reached but did not answer in time. */
        TIMEOUT,
        /** The model declined to answer. */
        REFUSED,
        /** The answer was cut off or was not the structured bill we asked for. */
        BAD_RESPONSE,
    }
}
