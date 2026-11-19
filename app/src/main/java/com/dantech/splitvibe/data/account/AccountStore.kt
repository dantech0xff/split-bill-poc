package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.core.content.edit
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.settings.KeystoreCipher
import com.dantech.splitvibe.data.settings.TextCipher
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.text
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A session the AI proxy signed after a Google sign-in. [expiresAt] is in epoch milliseconds. */
data class Session(val token: String, val expiresAt: Long)

/** Which allowance the proxy counts someone's calls against. */
enum class Plan(private val wireName: String) {
    /** The calls an install gets before anyone signs in on it, which never come back. */
    TRIAL("trial"),

    /** The calls a Google account gets each month without Pro. */
    FREE("free"),

    /** The calls of a Pro subscription each month. */
    PRO("pro"),
    ;

    companion object {
        /** The plan the proxy names [name], or null for one this version of the app does not know. */
        fun ofWire(name: String): Plan? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * The AI calls left, as the proxy last counted them. A call can take part of one: reading a bill
 * takes [READ] and changing a split by a sentence [REVISION], so the counts have decimals, such as
 * 2.8. [period] is "trial" for the calls an install gets before signing in, which never come back,
 * or the calendar month in UTC such as "2026-11". [plan] is the allowance they are counted against;
 * a proxy from before Pro does not say, and then a trial is the install's and anything else a Google
 * account's free calls.
 */
data class CallsLeft(
    val limit: BigDecimal,
    val used: BigDecimal,
    val remaining: BigDecimal,
    val period: String,
    val resetsAt: Long?,
    val plan: Plan = if (period == TRIAL_PERIOD) Plan.TRIAL else Plan.FREE,
) {
    /** The day the monthly calls come back, such as "1 December", or null for a trial. */
    val comesBackOn: String?
        get() = resetsAt?.let {
            DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH).format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC))
        }

    companion object {
        /** What reading a bill takes of the calls. The proxy decides it; the app only says it. */
        val READ: BigDecimal = BigDecimal.ONE

        /** What changing a split by a sentence takes, as `revisionWeight` in the proxy's `policy.ts` sets it. */
        val REVISION: BigDecimal = BigDecimal("0.2")

        /** The calls a Google account gets each month without Pro, as the proxy's `policy.ts` sets them. */
        const val FREE_MONTHLY = 10

        /** The calls Pro gives each month, as `proCalls` in the proxy's `policy.ts` sets them. */
        const val PRO_MONTHLY = 300

        /** The [period] of the calls an install gets before anyone signs in on it. */
        const val TRIAL_PERIOD = "trial"

        /** [calls] as the app writes a number of calls, without zeros that say nothing: "2.8", "3", "0.2". */
        fun text(calls: BigDecimal): String = calls.stripTrailingZeros().toPlainString()
    }
}

/** Whether bills can be read with AI, and what to tell the person when they cannot. */
enum class AiStatus(val notice: UiText?) {
    AVAILABLE(null),

    /** This build has no address for the AI proxy, so bills cannot be read with AI at all. */
    NOT_SET_UP(text(R.string.ai_not_set_up)),

    /** The proxy said AI reading is switched off. The next request asks again. */
    OFF(text(R.string.ai_off)),
}

/** What the app knows about its standing with the AI proxy. */
data class Account(
    /** A random id this install made for itself. It is not tied to the phone or to a person. */
    val installId: String,
    val session: Session?,
    /** Null until the proxy has reported a count in this run of the app. */
    val callsLeft: CallsLeft?,
    val ai: AiStatus,
    /** The Google account's address, shown in Settings while signed in and once the sign-in has run out. */
    val signedInAs: String? = null,
    /**
     * Someone signed in, and their session has since run out or the proxy refused it. Until they sign
     * in again or sign out, no request goes out, neither as them nor as the install in their place.
     */
    val signInExpired: Boolean = false,
    /**
     * The signed-in account's id at RevenueCat, which the proxy hands out with the session and the
     * app never works out itself. It goes with the session: null while nobody is signed in, and
     * until the proxy has said it.
     */
    val purchaser: String? = null,
    /**
     * The proxy took [session] in this run of the app: it gave it at a sign-in, or answered a check of
     * the calls left or of the plan made with it. A session kept from an earlier run may belong to an
     * account deleted since, on the web, which the proxy then refuses; until it took the session,
     * nothing is done for the account elsewhere, such as starting RevenueCat as its purchaser.
     */
    val sessionAccepted: Boolean = false,
)

/**
 * Keeps the install id and the signed-in session with its purchaser, encrypted with a key in the
 * Android Keystore, and the latest calls left and AI switch the proxy reported, which only live as
 * long as the app.
 */
class AccountStore(
    context: Context,
    /** Whether this build knows where the AI proxy is. */
    private val proxySetUp: Boolean,
    private val cipher: TextCipher = KeystoreCipher(alias = "split_vibe_session"),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(storedAccount())

    val account: StateFlow<Account> = state.asStateFlow()

    /**
     * The session to send, or null when there is none. A session that has run out is not sent: it
     * turns into a sign-in that has run out, see [Account.signInExpired].
     */
    fun currentSession(): Session? {
        val session = state.value.session ?: return null
        if (session.expiresAt > clock()) return session
        expireSession()
        return null
    }

    /** Keeps the session of a new sign-in with the account's address and its [purchaser], in place of any before it. */
    fun saveSession(session: Session, signedInAs: String? = null, purchaser: String? = null) {
        preferences.edit {
            putString(KEY_SESSION, cipher.encrypt(session.token))
            putLong(KEY_SESSION_EXPIRES_AT, session.expiresAt)
            if (signedInAs == null) remove(KEY_SIGNED_IN_AS) else putString(KEY_SIGNED_IN_AS, cipher.encrypt(signedInAs))
            if (purchaser == null) remove(KEY_PURCHASER) else putString(KEY_PURCHASER, cipher.encrypt(purchaser))
            remove(KEY_SIGN_IN_EXPIRED)
        }
        // A new person may have signed in, so the old count no longer applies. The proxy has just given the session.
        state.update {
            it.copy(
                session = session,
                signedInAs = signedInAs,
                signInExpired = false,
                callsLeft = null,
                purchaser = purchaser,
                sessionAccepted = true,
            )
        }
    }

    /** The proxy answered a call made with [session], so it takes it, while it is still the one signed in. */
    fun onSessionAccepted(session: Session) = state.update { if (it.session == session) it.copy(sessionAccepted = true) else it }

    /**
     * The purchaser the proxy gave in an answer to [session], such as a sign-in from before the app
     * kept one. It is kept only while [session] is still the one signed in: an answer that arrives
     * after signing out, or after someone else signed in, is nobody's now.
     */
    fun onPurchaser(purchaser: String, session: Session) {
        val current = state.value
        if (current.session != session || current.purchaser == purchaser) return
        preferences.edit { putString(KEY_PURCHASER, cipher.encrypt(purchaser)) }
        state.update { it.copy(purchaser = purchaser) }
    }

    /**
     * The session ran out or the proxy refused it. The person keeps their place as someone who
     * signed in, and has to sign in again, or sign out to go back to this phone's free reads.
     */
    fun expireSession() {
        // A refusal that arrives after signing out belongs to nobody any more.
        if (state.value.session == null && !state.value.signInExpired) return
        preferences.edit {
            remove(KEY_SESSION)
            remove(KEY_SESSION_EXPIRES_AT)
            // The next sign-in brings the purchaser again, whoever signs in.
            remove(KEY_PURCHASER)
            putBoolean(KEY_SIGN_IN_EXPIRED, true)
        }
        // The count was the account's, and no read is possible until the person signs in again.
        state.update { it.copy(session = null, signInExpired = true, callsLeft = null, purchaser = null, sessionAccepted = false) }
    }

    /**
     * Forgets the sign-in, after signing out or deleting the account; the install is the caller again.
     * It is on disk when this returns, so a deleted account's session never comes back after the app ends.
     */
    fun clearSession() {
        preferences.edit(commit = true) {
            remove(KEY_SESSION)
            remove(KEY_SESSION_EXPIRES_AT)
            remove(KEY_SIGNED_IN_AS)
            remove(KEY_PURCHASER)
            remove(KEY_SIGN_IN_EXPIRED)
        }
        state.update {
            it.copy(session = null, signedInAs = null, signInExpired = false, callsLeft = null, purchaser = null, sessionAccepted = false)
        }
    }

    fun onCallsLeft(callsLeft: CallsLeft) = state.update { it.copy(callsLeft = callsLeft) }

    fun onAiEnabled(enabled: Boolean) = state.update { it.copy(ai = aiStatus(enabled)) }

    private fun aiStatus(enabled: Boolean): AiStatus = when {
        !proxySetUp -> AiStatus.NOT_SET_UP
        !enabled -> AiStatus.OFF
        else -> AiStatus.AVAILABLE
    }

    private fun installId(): String {
        preferences.getString(KEY_INSTALL_ID, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        preferences.edit { putString(KEY_INSTALL_ID, created) }
        return created
    }

    private fun storedAccount(): Account {
        val encrypted = preferences.getString(KEY_SESSION, null)
        val token = encrypted?.let(cipher::decrypt)
        val expiresAt = preferences.getLong(KEY_SESSION_EXPIRES_AT, 0L)
        val session = if (token != null && expiresAt > clock()) Session(token, expiresAt) else null
        // A session that ran out, or that this phone can no longer decrypt, still means someone signed in.
        val expired = session == null && (encrypted != null || preferences.getBoolean(KEY_SIGN_IN_EXPIRED, false))
        return Account(
            installId = installId(),
            session = session,
            callsLeft = null,
            ai = aiStatus(enabled = true),
            signedInAs = if (session != null || expired) preferences.getString(KEY_SIGNED_IN_AS, null)?.let(cipher::decrypt) else null,
            signInExpired = expired,
            // Only with a session that still holds: the purchaser goes with it.
            purchaser = if (session != null) preferences.getString(KEY_PURCHASER, null)?.let(cipher::decrypt) else null,
        )
    }

    private companion object {
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_SESSION = "session_encrypted"
        const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        const val KEY_SIGNED_IN_AS = "signed_in_as_encrypted"
        const val KEY_PURCHASER = "purchaser_encrypted"
        const val KEY_SIGN_IN_EXPIRED = "sign_in_expired"
    }
}
