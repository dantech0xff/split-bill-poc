package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.core.content.edit
import com.dantech.splitvibe.data.settings.KeystoreCipher
import com.dantech.splitvibe.data.settings.TextCipher
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
 * The AI calls left, as the proxy last counted them. [period] is "trial" for the calls an install
 * gets before signing in, which never come back, or the calendar month in UTC such as "2026-11".
 */
data class CallsLeft(val limit: Int, val used: Int, val remaining: Int, val period: String, val resetsAt: Long?) {
    val isTrial: Boolean get() = resetsAt == null

    /** The day the monthly calls come back, such as "1 December", or null for a trial. */
    val comesBackOn: String?
        get() = resetsAt?.let {
            DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH).format(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC))
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
    /** The Google account's address, shown in Settings while signed in. */
    val signedInAs: String? = null,
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
    private val state = MutableStateFlow(
        storedSession().let { session ->
            Account(
                installId = installId(),
                session = session,
                callsLeft = null,
                ai = aiStatus(enabled = true),
                signedInAs = if (session == null) null else preferences.getString(KEY_SIGNED_IN_AS, null)?.let(cipher::decrypt),
            )
        },
    )

    val account: StateFlow<Account> = state.asStateFlow()

    /** The session to send, or null when there is none or it has run out. */
    fun currentSession(): Session? {
        val session = state.value.session ?: return null
        if (session.expiresAt > clock()) return session
        clearSession()
        return null
    }

    fun saveSession(session: Session, signedInAs: String? = null) {
        preferences.edit {
            putString(KEY_SESSION, cipher.encrypt(session.token))
            putLong(KEY_SESSION_EXPIRES_AT, session.expiresAt)
            if (signedInAs == null) remove(KEY_SIGNED_IN_AS) else putString(KEY_SIGNED_IN_AS, cipher.encrypt(signedInAs))
        }
        // A new person may have signed in, so the old count no longer applies.
        state.update { it.copy(session = session, signedInAs = signedInAs, callsLeft = null) }
    }

    /** Forgets the session: after signing out, deleting the account, or when the proxy refused it. */
    fun clearSession() {
        preferences.edit {
            remove(KEY_SESSION)
            remove(KEY_SESSION_EXPIRES_AT)
            remove(KEY_SIGNED_IN_AS)
        }
        state.update { it.copy(session = null, signedInAs = null, callsLeft = null) }
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

    private fun storedSession(): Session? {
        val token = preferences.getString(KEY_SESSION, null)?.let(cipher::decrypt) ?: return null
        val expiresAt = preferences.getLong(KEY_SESSION_EXPIRES_AT, 0L)
        return Session(token, expiresAt).takeIf { expiresAt > clock() }
    }

    private companion object {
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_SESSION = "session_encrypted"
        const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        const val KEY_SIGNED_IN_AS = "signed_in_as_encrypted"
    }
}
