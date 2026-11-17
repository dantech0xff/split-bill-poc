package com.dantech.splitvibe.llm

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.proxy.AnswerTimeoutException
import com.dantech.splitvibe.data.proxy.ModelReply
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
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
    val notSetUp: String,
    /** The proxy said AI is switched off. */
    val switchedOff: String,
    /** This version of the app is too old for the route. */
    val updateRequired: String,
    /** Too many calls came from this network or person just now; a hint on when to try again follows it. */
    val tooMany: String,
    /** The request ran as often as the proxy lets one request run. */
    val failedTries: String,
    /** The model declined. */
    val refused: String,
    /** The model's answer was cut off at the most it may write. */
    val tooLong: String,
    /** The model did not answer in time. */
    val tooSlow: String,
    /** The proxy did not take the request as it was. */
    val notTaken: String,
    /** What one call of this kind takes, such as "Reading a bill takes a whole call"; what is left follows it. */
    val cost: String,
)

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
        if (!api.isSetUp) throw BillAnalyzerException(Kind.AI_OFF, words.notSetUp)
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
            throw BillAnalyzerException(Kind.TIMEOUT, words.tooSlow, e)
        } catch (e: IOException) {
            throw BillAnalyzerException(Kind.NETWORK, NO_CONNECTION, e)
        }
        reply.callsLeft?.let(account::onCallsLeft)
        account.onAiEnabled(true)
        return read(reply.answer).also { unfinished = null }
    }

    private fun failureOf(e: ProxyException): BillAnalyzerException = when (e.code) {
        "quota_exhausted" -> BillAnalyzerException(Kind.QUOTA_EXHAUSTED, quotaMessage(e.callsLeft), e)

        ProxyApi.SESSION_REFUSED -> {
            account.expireSession()
            BillAnalyzerException(Kind.SIGN_IN_EXPIRED, "Your sign-in has run out. Sign in again in Settings to use your monthly calls.", e)
        }

        "ai_off" -> {
            account.onAiEnabled(false)
            BillAnalyzerException(Kind.AI_OFF, words.switchedOff, e)
        }

        "update_required" -> BillAnalyzerException(Kind.UPDATE_REQUIRED, words.updateRequired, e)

        "rate_limited", "spend_limited" -> BillAnalyzerException(Kind.RATE_LIMITED, "${words.tooMany} ${waitHint(e.retryAfterSeconds)}", e)

        // The next try is a new request, which can run at once.
        RETRY_LIMIT -> BillAnalyzerException(Kind.SERVICE_ERROR, words.failedTries, e)

        "busy" -> BillAnalyzerException(Kind.BUSY, "The AI is busy right now. ${waitHint(e.retryAfterSeconds)}", e)

        "refused" -> BillAnalyzerException(Kind.REFUSED, words.refused, e)

        "too_long" -> BillAnalyzerException(Kind.BAD_RESPONSE, words.tooLong, e)

        "bad_response" -> BillAnalyzerException(Kind.BAD_RESPONSE, UNREADABLE_ANSWER, e)

        "timeout" -> BillAnalyzerException(Kind.TIMEOUT, words.tooSlow, e)

        "install" -> BillAnalyzerException(Kind.BAD_REQUEST, words.notTaken, e)

        else -> if (isNotTaken(e)) {
            BillAnalyzerException(Kind.BAD_REQUEST, words.notTaken, e)
        } else {
            BillAnalyzerException(Kind.SERVICE_ERROR, "Split Vibe's server had a problem. Try again in a moment.", e)
        }
    }

    /** What the proxy says about a request it does not take as it is, whatever it names the reason. */
    private fun isNotTaken(e: ProxyException): Boolean = e.code == INPUT || e.status == 400

    /**
     * That the calls are used up, or that too little of one is left for a call of this kind: a bill
     * read takes a whole one, so a part left over from changes is not enough for it. Then where more
     * calls come from, as [moreCalls] says.
     */
    private fun quotaMessage(callsLeft: CallsLeft?): String {
        val more = moreCalls(callsLeft?.plan)
        if (callsLeft == null) return "You have used your free calls.$more"
        val limit = CallsLeft.text(callsLeft.limit)
        val left = callsLeft.remaining.takeIf { it.signum() > 0 }?.let(CallsLeft::text)
        val date = callsLeft.comesBackOn
        return when {
            date != null && left == null -> "You have used this month's $limit calls. They come back on $date.$more"
            date != null -> "${words.cost}, and $left of this month's $limit calls are left. They come back on $date.$more"
            left == null -> "You have used your $limit free calls.$more"
            else -> "${words.cost}, and $left of your $limit free calls are left.$more"
        }
    }

    /**
     * Where more calls come from for someone on [plan], as a sentence after the count, or nothing. A
     * build that sells Pro invites everyone not on Pro to it, and also tells someone who has not
     * signed in what signing in gives; a Pro member only learns when the calls come back. A build
     * that does not sell Pro tells only an install's trial to sign in.
     */
    private fun moreCalls(plan: Plan?): String = when {
        sellsPro -> when (plan) {
            Plan.PRO -> ""
            Plan.FREE -> " Get Pro for ${CallsLeft.PRO_MONTHLY} calls a month."
            Plan.TRIAL, null -> " Sign in with Google for ${CallsLeft.FREE_MONTHLY} calls a month, or get Pro for ${CallsLeft.PRO_MONTHLY}."
        }

        canSignIn && (plan == null || plan == Plan.TRIAL) -> " Sign in with Google in Settings to get more each month."

        else -> ""
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

    companion object {
        /** What to say about an answer that came but could not be used. */
        const val UNREADABLE_ANSWER = "The AI's answer could not be read. Please try again."

        private const val NO_CONNECTION = "Could not reach Split Vibe's server. Check the internet connection and try again."
        private const val REQUEST_ID = "requestId"
        private const val RETRY_LIMIT = "retry_limit"
        private const val INPUT = "input"
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
    sellsPro && kind == Kind.QUOTA_EXHAUSTED && (cause as? ProxyException)?.callsLeft?.plan != Plan.PRO
