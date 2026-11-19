package com.dantech.splitvibe.assistant

import com.dantech.splitvibe.R
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
import com.dantech.splitvibe.llm.modelCallFailure
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.text

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
    /** Whether this build sells Pro, which gives the most calls. */
    val sellsPro: Boolean,
) {

    private val call = ProxyModelCall(api, account, canSignIn, sellsPro, WORDS, api::reviseSplit)

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
                throw modelCallFailure(Kind.BAD_RESPONSE, ProxyModelCall.UNREADABLE_ANSWER)
            }
            revision
        },
    )

    companion object {
        /** What the assistant says when this build cannot change splits with AI at all. */
        val NOT_SET_UP: UiText = text(R.string.assistant_not_set_up)

        /** What the assistant says while the proxy has AI switched off. */
        val SWITCHED_OFF: UiText = text(R.string.assistant_switched_off)

        private val WORDS = ModelCallWords(
            notSetUp = NOT_SET_UP,
            switchedOff = SWITCHED_OFF,
            updateRequired = text(R.string.assistant_update_required),
            tooMany = text(R.string.assistant_too_many),
            failedTries = text(R.string.assistant_failed_tries),
            refused = text(R.string.assistant_refused),
            tooLong = text(R.string.assistant_too_long),
            tooSlow = text(R.string.assistant_too_slow),
            notTaken = text(R.string.assistant_not_taken),
            cost = text(R.string.assistant_cost, CallsLeft.text(CallsLeft.REVISION)),
        )
    }
}
