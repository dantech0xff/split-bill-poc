package com.dantech.splitvibe.assistant

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.SplitEdits
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.llm.ModelCallWords
import com.dantech.splitvibe.llm.ProxyModelCall
import com.dantech.splitvibe.llm.SplitRevision
import com.dantech.splitvibe.llm.SplitRevisionParser
import com.dantech.splitvibe.llm.SplitRevisionRequest

/**
 * Asks the AI proxy how to change a split, from a sentence the user typed or said, through
 * `POST /v1/split-revision`. The model only proposes edits. Nothing here applies them, and nothing
 * in the answer is trusted until [SplitEdits.apply] has checked it against the bill.
 *
 * The request goes as the bill and its split are, never as the text read from the bill's photo. It
 * keeps its id until it ends in an answer the app can use, as a bill read does, so asking again
 * after a cancel or a cut-off answer is the same request and is not charged twice. Each request
 * takes [CallsLeft.REVISION] of a call.
 */
class SplitAssistant(
    api: ProxyApi,
    account: AccountStore,
    /** Whether this build can sign in with Google, which is how a person gets more calls. */
    val canSignIn: Boolean,
) {

    private val call = ProxyModelCall(api, account, canSignIn, WORDS, api::reviseSplit)

    /**
     * The edits the AI proposes for [instruction] on [bill] split as [plan], with what it says about
     * them, or the question it asks back. [meName] is the user's own name, which "I" and "me" in the
     * instruction stand for.
     *
     * @throws BillAnalyzerException when the proxy could not be reached, refused, or gave no answer the app can use.
     * @throws IllegalArgumentException when nothing of [instruction] is left to send
     *   ([SplitRevisionRequest.instructionOf]), or it is longer than [SplitRevisionRequest.MAX_INSTRUCTION_CODE_POINTS].
     */
    suspend fun revise(instruction: String, bill: Bill, plan: SplitPlan, meName: String): SplitRevision = call.ask(
        content = { SplitRevisionRequest.body(instruction, bill, plan, meName) },
        read = { answer ->
            val revision = SplitRevisionParser.parse(answer.toString())
            // Edits that are all unreadable, with nothing else said, are no answer at all: the next
            // try is the same request.
            if (revision == null || (revision.edits.isEmpty() && revision.question == null && revision.unreadable > 0)) {
                throw BillAnalyzerException(Kind.BAD_RESPONSE, ProxyModelCall.UNREADABLE_ANSWER)
            }
            revision
        },
    )

    companion object {
        /** What the assistant says when this build cannot change splits with AI at all. */
        const val NOT_SET_UP = "Changing a split with AI is not set up in this build of the app."

        /** What the assistant says while the proxy has AI switched off. */
        const val SWITCHED_OFF = "Changing a split with AI is switched off for now. Please try again later."

        private val WORDS = ModelCallWords(
            notSetUp = NOT_SET_UP,
            switchedOff = SWITCHED_OFF,
            updateRequired = "Update Split Vibe to keep changing splits with AI.",
            tooMany = "Too many changes were asked for just now.",
            failedTries = "This change did not work after several tries. Please try again.",
            refused = "The AI declined to make this change.",
            tooLong = "The AI's answer was too long. Ask for fewer changes at a time.",
            tooSlow = "Working out the change took too long. Please try again.",
            notTaken = "This bill cannot be changed this way. Change it by hand instead.",
            cost = "Changing a split takes ${CallsLeft.text(CallsLeft.REVISION)} of a call",
        )
    }
}
