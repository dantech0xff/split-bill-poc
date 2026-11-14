package com.dantech.splitvibe.data.account

import android.content.Context
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import kotlinx.coroutines.CancellationException

/**
 * Signing in with Google, signing out, deleting the account and asking for the calls left. The
 * proxy checks Google's ID token itself and hands out its own session; the phone keeps only that
 * session and the account's address.
 */
class AccountRepository(private val api: ProxyApi, private val store: AccountStore, private val google: GoogleAccounts?) {

    /** False in a build that has no address for the proxy or no web client ID for Google. */
    val canSignIn: Boolean get() = google != null && api.isSetUp

    /** Throws [GoogleSignInException] when no account was picked, and [ProxyException] when the proxy refused it. */
    suspend fun signIn(activity: Context) {
        val accounts = checkNotNull(google) { "This build cannot sign in" }
        val account = accounts.pick(activity)
        val (session, callsLeft) = api.startSession(account.idToken)
        store.saveSession(session, signedInAs = account.email)
        callsLeft?.let(store::onCallsLeft)
    }

    suspend fun signOut(context: Context) {
        store.clearSession()
        forgetGoogle(context)
    }

    /** Deletes what the proxy keeps about the account first; the phone forgets the session only once that worked. */
    suspend fun deleteAccount(context: Context) {
        try {
            api.deleteAccount()
        } catch (e: ProxyException) {
            // A session the proxy no longer accepts cannot delete anything; signing in again can.
            if (e.code == SESSION_REFUSED) store.clearSession()
            throw e
        }
        store.clearSession()
        forgetGoogle(context)
    }

    /** Asks the proxy how many calls are left and whether AI is on. */
    suspend fun refresh() {
        if (!api.isSetUp) return
        val usage = try {
            api.usage()
        } catch (e: ProxyException) {
            if (e.code == SESSION_REFUSED) store.clearSession()
            throw e
        }
        store.onCallsLeft(usage.callsLeft)
        store.onAiEnabled(usage.aiEnabled)
    }

    private suspend fun forgetGoogle(context: Context) {
        try {
            google?.forget(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The session is gone either way; the picker may just preselect the same account next time.
        }
    }

    companion object {
        const val SESSION_REFUSED = "session"
    }
}
