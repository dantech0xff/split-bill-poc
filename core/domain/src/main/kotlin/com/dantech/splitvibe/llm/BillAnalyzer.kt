package com.dantech.splitvibe.llm

import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.SplitHint
import com.dantech.splitvibe.ocr.OcrResult

/**
 * Turns the OCR output of a bill photo plus the user's instruction into a structured bill.
 *
 * The app only depends on this interface. Its implementation in the app asks the app's AI proxy,
 * which holds the Anthropic key, builds the prompt and counts each person's calls; no key ever
 * ships inside the app.
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

class BillAnalyzerException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {

    enum class Kind {
        /** The calls this install or account may make are used up, for good or until the month turns. */
        QUOTA_EXHAUSTED,

        /** The sign-in was not accepted or has run out; signing in again fixes it. */
        SIGN_IN_EXPIRED,

        /** Reading bills with AI is switched off on the server for now. */
        AI_OFF,

        /** This version of the app is too old to read bills. */
        UPDATE_REQUIRED,

        /** Too many requests came from this network or person just now. */
        RATE_LIMITED,

        /** The model is overloaded, or the service has spent what it may spend today. */
        BUSY,

        /** The server rejected the request as invalid. */
        BAD_REQUEST,

        /** The server had a problem of its own. */
        SERVICE_ERROR,

        /** No usable connection to the server. */
        NETWORK,

        /** The server was reached but the answer did not come in time. */
        TIMEOUT,

        /** The model declined to read the bill. */
        REFUSED,

        /** The answer was cut off or was not the structured bill that was asked for. */
        BAD_RESPONSE,
    }
}
