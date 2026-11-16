package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.core.content.edit
import com.dantech.splitvibe.data.settings.KeystoreCipher
import com.dantech.splitvibe.data.settings.TextCipher
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

/**
 * The AI calls left, as the proxy last counted them. A call can take part of one: reading a bill
 * takes [READ] and changing a split by a sentence [REVISION], so the counts have decimals, such as
 * 2.8. [period] is "trial" for the calls an install gets before signing in, which never come back,
 * or the calendar month in UTC such as "2026-11".
 */
data class CallsLeft(val limit: BigDecimal, val used: BigDecimal, val remaining: BigDecimal, val period: String, val resetsAt: Long?) {
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

        /** [calls] as the app writes a number of calls, without zeros that say nothing: "2.8", "3", "0.2". */
        fun text(calls: BigDecimal): String = calls.stripTrailingZeros().toPlainString()
    }
}

/** Whether bills can be read with AI, and what to tell the person when they cannot. */
enum class AiStatus(val notice: String?) {
    AVAILABLE(null),

    /** This build has no address for the AI proxy, so bills cannot be read with AI at all. */
    NOT_SET_UP("Reading bills with AI is not set up in this build of the app."),

    /** The proxy said AI reading is switched off. The next request asks again. */
    OFF("Reading bills with AI is switched off for now. Please try again later."),
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
)

/**
 * Keeps the install id and the signed-in session, encrypted with a key in the Android Keystore,
 * and the latest calls left and AI switch the proxy reported, which only live as long as the app.
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

    fun saveSession(session: Session, signedInAs: String? = null) {
        preferences.edit {
            putString(KEY_SESSION, cipher.encrypt(session.token))
            putLong(KEY_SESSION_EXPIRES_AT, session.expiresAt)
            if (signedInAs == null) remove(KEY_SIGNED_IN_AS) else putString(KEY_SIGNED_IN_AS, cipher.encrypt(signedInAs))
            remove(KEY_SIGN_IN_EXPIRED)
        }
        // A new person may have signed in, so the old count no longer applies.
        state.update { it.copy(session = session, signedInAs = signedInAs, signInExpired = false, callsLeft = null) }
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
            putBoolean(KEY_SIGN_IN_EXPIRED, true)
        }
        // The count was the account's, and no read is possible until the person signs in again.
        state.update { it.copy(session = null, signInExpired = true, callsLeft = null) }
    }

    /** Forgets the sign-in, after signing out or deleting the account; the install is the caller again. */
    fun clearSession() {
        preferences.edit {
            remove(KEY_SESSION)
            remove(KEY_SESSION_EXPIRES_AT)
            remove(KEY_SIGNED_IN_AS)
            remove(KEY_SIGN_IN_EXPIRED)
        }
        state.update { it.copy(session = null, signedInAs = null, signInExpired = false, callsLeft = null) }
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
        )
    }

    private companion object {
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_SESSION = "session_encrypted"
        const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        const val KEY_SIGNED_IN_AS = "signed_in_as_encrypted"
        const val KEY_SIGN_IN_EXPIRED = "sign_in_expired"
    }
}
