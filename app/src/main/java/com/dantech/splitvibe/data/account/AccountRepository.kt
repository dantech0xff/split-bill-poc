package com.dantech.splitvibe.data.account

import android.content.Context
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.notifications.DeviceRegistrar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Signing in with Google, signing out, deleting the account and asking for the calls left. The
 * proxy checks Google's ID token itself and hands out its own session; the phone keeps only that
 * session, the account's address and the account's id at RevenueCat that comes with it.
 */
class AccountRepository(
    private val api: ProxyApi,
    private val store: AccountStore,
    private val google: GoogleAccounts?,
    /** Groups and requests of the account signed in now; another account must never see them. */
    private val cache: ProxyCache,
    /** Where the account's push notices go; this install stops getting them on signing out. */
    private val devices: DeviceRegistrar,
    /** What only this phone keeps, which the person may delete with the account. */
    private val phone: PhoneData,
) {

    /** False in a build that has no address for the proxy or no web client ID for Google. */
    val canSignIn: Boolean get() = google != null && api.isSetUp

    /** Throws [GoogleSignInException] when no account was picked, and [ProxyException] when the proxy refused it. */
    suspend fun signIn(activity: Context) {
        val accounts = checkNotNull(google) { "This build cannot sign in" }
        val account = accounts.pick(activity)
        val started = api.startSession(account.idToken)
        // Someone else may be signing in after a sign-in ran out.
        cache.clear()
        store.saveSession(started.session, signedInAs = account.email, purchaser = started.purchaser)
        started.callsLeft?.let(store::onCallsLeft)
    }

    /** Also ends a sign-in that has run out: this phone's free reads are used again. */
    suspend fun signOut(context: Context) {
        // Signing out never waits long on the network: without an answer, notices simply stop later.
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) { devices.unregister() }
        store.clearSession()
        cache.clear()
        forgetGoogle(context)
    }

    /**
     * Deletes what the proxy keeps about the account first; the phone forgets the session only once
     * that worked, and then, [withPhoneData], also deletes what only the phone keeps ([PhoneData]).
     * When the proxy did not delete the account, nothing on the phone is deleted.
     *
     * @throws PhoneDataException when the account was deleted but the phone's data could not all be.
     */
    suspend fun deleteAccount(context: Context, withPhoneData: Boolean) {
        try {
            api.deleteAccount()
        } catch (e: ProxyException) {
            // A session the proxy no longer accepts cannot delete anything; signing in again can.
            if (e.code == ProxyApi.SESSION_REFUSED) store.expireSession()
            throw e
        }
        store.clearSession()
        // The account is gone now, so what the person asked to delete with it is deleted even when
        // they leave the screen meanwhile.
        val notDeleted = withContext(NonCancellable) {
            cache.clear()
            if (!withPhoneData) return@withContext null
            try {
                phone.delete()
                null
            } catch (e: Exception) {
                e
            }
        }
        forgetGoogle(context)
        notDeleted?.let { throw PhoneDataException(it) }
    }

    /**
     * Asks the proxy how many calls are left and whether AI is on, and for a signed-in account who
     * its purchaser is. A sign-in that has run out is not an error here: the account then shows that
     * the person has to sign in again.
     */
    suspend fun refresh() {
        if (!api.isSetUp) return
        val session = store.currentSession()
        val usage = try {
            api.usage()
        } catch (e: ProxyException) {
            if (e.code != ProxyApi.SESSION_REFUSED) throw e
            store.expireSession()
            return
        }
        store.onCallsLeft(usage.callsLeft)
        store.onAiEnabled(usage.aiEnabled)
        if (session != null) usage.purchaser?.let { store.onPurchaser(it, session) }
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

    private companion object {
        const val UNREGISTER_TIMEOUT_MS = 5_000L
    }
}
