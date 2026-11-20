package com.dantech.splitvibe.llm

import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.proxy.AnswerTimeoutException
import com.dantech.splitvibe.data.proxy.ModelReply
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.sentences
import com.dantech.splitvibe.text.text
import java.io.IOException
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the messages about one kind of call to the model say where the kinds differ: reading a bill,
 * or changing a split by a sentence. Each one is a whole sentence the person reads.
 */
class ModelCallWords(
    /** This build has no address for the AI proxy. */
    val notSetUp: UiText,
    /** The proxy said AI is switched off. */
    val switchedOff: UiText,
    /** This version of the app is too old for the route. */
    val updateRequired: UiText,
    /** Too many calls came from this network or person just now; a hint on when to try again follows it. */
    val tooMany: UiText,
    /** The request ran as often as the proxy lets one request run. */
    val failedTries: UiText,
    /** The model declined. */
    val refused: UiText,
    /** The model's answer was cut off at the most it may write. */
    val tooLong: UiText,
    /** The model did not answer in time. */
    val tooSlow: UiText,
    /** The proxy did not take the request as it was. */
    val notTaken: UiText,
    /**
     * What one call of this kind takes, such as "Reading a bill takes a whole call"; what is left
     * follows it. It is built for each message, so a number in it has the marks of the region the
     * phone is set to then, as the numbers around it do.
     */
    val cost: () -> UiText,
)

/**
 * What a failed call to the model tells the person. Each [BillAnalyzerException] the app throws has
 * it as its cause, as the domain's exception carries only a message for developers; it keeps what
 * failed, such as the proxy's refusal, as its own cause.
 */
class ModelCallFailure(val text: UiText, cause: Throwable? = null) : Exception(cause)

/** A failure of [kind] that tells the person [text]; [cause] is what failed, if something did. */
fun modelCallFailure(kind: Kind, text: UiText, cause: Throwable? = null): BillAnalyzerException =
    BillAnalyzerException(kind, "The call to the model failed: $kind", ModelCallFailure(text, cause))

/** What to tell the person about this failure: the app's words for it, or the message `:core:domain` gave it, which is in English. */
val BillAnalyzerException.text: UiText get() = (cause as? ModelCallFailure)?.text ?: UiText.Raw(message.orEmpty())

/** What failed behind this failure, such as the proxy's refusal. */
private val BillAnalyzerException.failure: Throwable? get() = (cause as? ModelCallFailure)?.cause ?: cause

/**
 * One of the AI proxy's routes that ask the model, as the app calls it: it sends the request with
 * an id, keeps the account's calls left and AI switch as the proxy reports them, and turns every
 * refusal and failure into a [BillAnalyzerException] with a kind and a message the person can act on.
 *
 * A request keeps its id until it ends in an answer the app can use. Sending the same content again
 * after a cancel, a cut-off answer or an answer that could not be used is therefore the same
 * request, which the proxy does not charge twice.
 */
class ProxyModelCall(
    private val api: ProxyApi,
    private val account: AccountStore,
    /** Whether this build can sign in with Google, which is how a person gets more calls. */
    private val canSignIn: Boolean,
    /** Whether this build sells Pro, which gives the most calls. */
    private val sellsPro: Boolean,
    private val words: ModelCallWords,
    /** Sends a body to the route and reads the streamed reply up to its answer, such as [ProxyApi.readBill]. */
    private val send: suspend (JsonObject) -> ModelReply,
) {

    /** The content and request id of the latest request that has not ended in a usable answer. */
    @Volatile
    private var unfinished: Pair<JsonObject, String>? = null

    /**
     * Sends what [content] makes, with a request id, and returns what [read] makes of the model's
     * answer. [content] may throw [BillAnalyzerException] for something the proxy would refuse, and
     * [read] for an answer it cannot use, which keeps the request id for the next try.
     */
    suspend fun <T> ask(content: () -> JsonObject, read: (JsonObject) -> T): T {
        if (!api.isSetUp) throw modelCallFailure(Kind.AI_OFF, words.notSetUp)
        val asked = content()
        val requestId = unfinished?.takeIf { it.first == asked }?.second ?: UUID.randomUUID().toString()
        // Kept from before the request goes out, so that a request cancelled while it waits keeps its id too.
        unfinished = asked to requestId

        val reply = try {
            send(JsonObject(mapOf(REQUEST_ID to JsonPrimitive(requestId)) + asked))
        } catch (e: ProxyException) {
            // Under its old id the request cannot work again: it has run as often as it may, or the
            // proxy no longer takes it, for example after a change to the prompt.
            if (e.code == RETRY_LIMIT || isNotTaken(e)) unfinished = null
            e.callsLeft?.let(account::onCallsLeft)
            // The proxy checks its AI switch first, so any other answer of its own means AI is on. A
            // refused session says nothing about that: the app refuses a session that ran out itself.
            if (e.code != ProxyApi.SESSION_REFUSED && (e.status == 200 || e.status in 400..499)) account.onAiEnabled(true)
            throw failureOf(e)
        } catch (e: AnswerTimeoutException) {
            throw modelCallFailure(Kind.TIMEOUT, words.tooSlow, e)
        } catch (e: IOException) {
            throw modelCallFailure(Kind.NETWORK, text(R.string.server_unreachable), e)
        }
        reply.callsLeft?.let(account::onCallsLeft)
        account.onAiEnabled(true)
        return read(reply.answer).also { unfinished = null }
    }

    private fun failureOf(e: ProxyException): BillAnalyzerException = when (e.code) {
        "quota_exhausted" -> modelCallFailure(Kind.QUOTA_EXHAUSTED, quotaMessage(e.callsLeft), e)

        ProxyApi.SESSION_REFUSED -> {
            account.expireSession()
            modelCallFailure(Kind.SIGN_IN_EXPIRED, text(R.string.model_sign_in_expired), e)
        }

        "ai_off" -> {
            account.onAiEnabled(false)
            modelCallFailure(Kind.AI_OFF, words.switchedOff, e)
        }

        "update_required" -> modelCallFailure(Kind.UPDATE_REQUIRED, words.updateRequired, e)

        "rate_limited", "spend_limited" -> modelCallFailure(Kind.RATE_LIMITED, sentences(words.tooMany, waitHint(e.retryAfterSeconds)), e)

        // The next try is a new request, which can run at once.
        RETRY_LIMIT -> modelCallFailure(Kind.SERVICE_ERROR, words.failedTries, e)

        "busy" -> modelCallFailure(Kind.BUSY, sentences(text(R.string.model_busy), waitHint(e.retryAfterSeconds)), e)

        "refused" -> modelCallFailure(Kind.REFUSED, words.refused, e)

        "too_long" -> modelCallFailure(Kind.BAD_RESPONSE, words.tooLong, e)

        "bad_response" -> modelCallFailure(Kind.BAD_RESPONSE, UNREADABLE_ANSWER, e)

        "timeout" -> modelCallFailure(Kind.TIMEOUT, words.tooSlow, e)

        "install" -> modelCallFailure(Kind.BAD_REQUEST, words.notTaken, e)

        else -> if (isNotTaken(e)) {
            modelCallFailure(Kind.BAD_REQUEST, words.notTaken, e)
        } else {
            modelCallFailure(Kind.SERVICE_ERROR, text(R.string.server_problem), e)
        }
    }

    /** What the proxy says about a request it does not take as it is, whatever it names the reason. */
    private fun isNotTaken(e: ProxyException): Boolean = e.code == INPUT || e.status == 400

    /**
     * That the calls are used up, or that too little of one is left for a call of this kind: a bill
     * read takes a whole one, so a part left over from changes is not enough for it. Then where more
     * calls come from, as [moreCalls] says.
     */
    private fun quotaMessage(callsLeft: CallsLeft?): UiText {
        val more = moreCalls(callsLeft?.plan)
        if (callsLeft == null) return sentences(text(R.string.model_quota_used), more)
        val limit = CallsLeft.text(callsLeft.limit)
        // The words follow the number of calls the allowance holds.
        val count = callsLeft.limit.toInt()
        val left = callsLeft.remaining.takeIf { it.signum() > 0 }?.let(CallsLeft::text)
        val date = callsLeft.comesBackOn
        val said = when {
            left == null && date != null -> plural(R.plurals.model_quota_used_month, count, limit, date)
            left == null -> plural(R.plurals.model_quota_used_free, count, limit)
            date != null -> plural(R.plurals.model_quota_left_month, count, words.cost(), left, limit, date)
            else -> plural(R.plurals.model_quota_left_free, count, words.cost(), left, limit)
        }
        return sentences(said, more)
    }

    /**
     * Where more calls come from for someone on [plan], as a sentence after the count, or nothing. A
     * build that sells Pro invites everyone not on Pro to it, and also tells someone who has not
     * signed in what signing in gives; a Pro member only learns when the calls come back. A build
     * that does not sell Pro tells only an install's trial to sign in.
     */
    private fun moreCalls(plan: Plan?): UiText? = when {
        sellsPro -> when (plan) {
            Plan.PRO -> null

            Plan.FREE -> plural(R.plurals.model_more_pro, CallsLeft.PRO_MONTHLY)

            Plan.TRIAL, null -> {
                plural(R.plurals.model_more_sign_in_or_pro, CallsLeft.FREE_MONTHLY, CallsLeft.FREE_MONTHLY, CallsLeft.PRO_MONTHLY)
            }
        }

        canSignIn && (plan == null || plan == Plan.TRIAL) -> text(R.string.model_more_sign_in)

        else -> null
    }

    companion object {
        /** What to say about an answer that came but could not be used. */
        val UNREADABLE_ANSWER: UiText = text(R.string.model_unreadable_answer)

        private const val REQUEST_ID = "requestId"
        private const val RETRY_LIMIT = "retry_limit"
        private const val INPUT = "input"
    }
}

/**
 * When to try again after the proxy limited a request, from its `Retry-After` in [seconds], such as
 * "Try again in about 40 minutes."; without one, later.
 */
internal fun waitHint(seconds: Long?): UiText {
    if (seconds == null) return text(R.string.wait_later)
    val minutes = ((seconds + 59) / 60).toInt()
    return when {
        minutes <= 1 -> text(R.string.wait_a_minute)
        minutes < 120 -> plural(R.plurals.wait_minutes, minutes)
        else -> plural(R.plurals.wait_hours, (minutes + 59) / 60)
    }
}

/**
 * Whether signing in fixes this failure, in a build that can sign in: a sign-in that has run out,
 * or the free calls of this phone used up before anyone signed in. Signing in again after the
 * month's calls are used up gives no more. In a build that sells Pro, calls used up lead to Pro
 * instead, whose screen also offers signing in ([proHelps]).
 */
fun BillAnalyzerException.signInHelps(canSignIn: Boolean, signedIn: Boolean, sellsPro: Boolean): Boolean =
    canSignIn && (kind == Kind.SIGN_IN_EXPIRED || (kind == Kind.QUOTA_EXHAUSTED && !signedIn && !sellsPro))

/**
 * Whether Pro gives more calls after this failure, in a build that sells it: the calls are used up,
 * and the proxy does not count them as Pro's already.
 */
fun BillAnalyzerException.proHelps(sellsPro: Boolean): Boolean =
    sellsPro && kind == Kind.QUOTA_EXHAUSTED && (failure as? ProxyException)?.callsLeft?.plan != Plan.PRO
