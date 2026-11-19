package com.dantech.splitvibe.data.account

import android.content.Context
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.notifications.DeviceRegistrar
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where an account deletion is: running, or how it ended, until Settings has said it. */
sealed interface Deletion {
    /** It is running; leaving Settings does not stop it. */
    data object Running : Deletion

    /** It ended and changed something, as [outcome] says. */
    data class Ended(val outcome: DeletionOutcome) : Deletion

    /** The proxy did not delete the account, or could not be reached, so nothing was deleted; [error] says why. */
    data class Refused(val error: Exception) : Deletion
}

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
    /** What the phone remembers of a deletion, so that one the app is ended in the middle of is finished. */
    private val deletions: DeletionStore,
    /** The application, whose Google account the phone's account picker forgets once the account is deleted. */
    private val app: Context,
    /** Where a deletion runs: for as long as the app does, whatever becomes of the screen that started it. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val deletionState = MutableStateFlow<Deletion?>(deletions.outcome?.let(Deletion::Ended))

    /** The deletion running now, or how the last one ended until Settings said it ([deletionShown]); null when there is nothing to say. */
    val deletion: StateFlow<Deletion?> = deletionState.asStateFlow()

    /** The deletion that runs now, if one does. Changed only while this object is locked. */
    private var running: Job? = null

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
     * Deletes the account: first what the proxy keeps about it, and only once the proxy answered that
     * it did, the session and the groups and requests kept from it on the phone, and [withPhoneData]
     * also what only the phone keeps ([PhoneData]). When the proxy does not delete the account,
     * nothing on the phone is deleted.
     *
     * It runs in this object's own scope, so leaving Settings, or Settings going away, stops neither
     * the request nor what follows it; [deletion] says how it goes. The person's choice is on disk
     * before the request goes out, so a deletion the app is ended in the middle of is finished when
     * it starts again ([finishInterruptedDeletion]). Returns the deletion, or the one that runs already.
     */
    fun deleteAccount(withPhoneData: Boolean): Job = synchronized(this) {
        running?.takeIf { it.isActive } ?: run {
            deletions.begin(withPhoneData)
            start(PendingDeletion(withPhoneData, proxyDeleted = false), resumed = false)
        }
    }

    /**
     * Finishes the deletion the app was ended in the middle of, if there is one: asks the proxy again
     * when its answer had not come, and deletes what is left on the phone once the proxy has deleted
     * the account. Returns it, or null when there is nothing to finish.
     */
    fun finishInterruptedDeletion(): Job? = synchronized(this) {
        running?.takeIf { it.isActive } ?: deletions.pending?.let { pending ->
            // The proxy deleted the account already, so its session goes before anything else can send it.
            if (pending.proxyDeleted) store.clearSession()
            start(pending, resumed = true)
        }
    }

    /** Settings has said how the last deletion ended. */
    fun deletionShown() {
        deletionState.update { if (it == Deletion.Running) it else null }
        deletions.outcomeShown()
    }

    private fun start(pending: PendingDeletion, resumed: Boolean): Job {
        deletionState.value = Deletion.Running
        val job = scope.launch { deletionState.value = delete(pending, resumed) }
        // Only a test ever stops the scope; the deletion stays written down for the next start.
        job.invokeOnCompletion { cause -> if (cause != null) deletionState.compareAndSet(Deletion.Running, null) }
        running = job
        return job
    }

    /**
     * Runs [pending] to its end, or to where it waits for the next start, and returns what Settings
     * is to say about it, or null for nothing. [resumed] is a deletion the app was ended during.
     */
    private suspend fun delete(pending: PendingDeletion, resumed: Boolean): Deletion? {
        if (!pending.proxyDeleted) {
            // Someone who signed out since asked for nothing; there is no session to delete with either.
            if (resumed && store.account.value.let { it.session == null && !it.signInExpired }) {
                deletions.end(null)
                return null
            }
            try {
                api.deleteAccount()
            } catch (e: ProxyException) {
                // A session the proxy no longer accepts cannot delete anything; signing in again can.
                if (e.code == ProxyApi.SESSION_REFUSED) {
                    store.expireSession()
                    // It is also how the proxy answers once a request from before the app ended deleted the account.
                    if (resumed) return ended(DeletionOutcome.NOT_CONFIRMED)
                }
                deletions.end(null)
                return Deletion.Refused(e)
            } catch (e: IOException) {
                // The request from before the app ended may have reached the proxy: the next start asks again.
                if (resumed) return null
                deletions.end(null)
                return Deletion.Refused(e)
            }
            deletions.proxyDeleted()
        }
        store.clearSession()
        return ended(deleteFromPhone(pending.withPhoneData))
    }

    /**
     * What is left once the proxy deleted the account: the groups and requests kept from it, and
     * [withPhoneData] what only the phone keeps. Each part is tried whatever became of the one before,
     * and the phone's account picker forgets the account in any case.
     */
    private suspend fun deleteFromPhone(withPhoneData: Boolean): DeletionOutcome {
        val cacheCleared = succeeds { cache.clear() }
        val phoneDataDeleted = withPhoneData && succeeds { phone.delete() }
        forgetGoogle(app)
        return when {
            withPhoneData && cacheCleared && phoneDataDeleted -> DeletionOutcome.DELETED_WITH_PHONE_DATA
            withPhoneData -> DeletionOutcome.PHONE_DATA_NOT_DELETED
            cacheCleared -> DeletionOutcome.DELETED
            else -> DeletionOutcome.ACCOUNT_DATA_NOT_DELETED
        }
    }

    private fun ended(outcome: DeletionOutcome): Deletion {
        deletions.end(outcome)
        return Deletion.Ended(outcome)
    }

    /**
     * Asks the proxy how many calls are left and whether AI is on, and for a signed-in account who
     * its purchaser is. A sign-in that has run out is not an error here: the account then shows that
     * the person has to sign in again. Nothing is asked while the account is being deleted.
     */
    suspend fun refresh() {
        if (!api.isSetUp || deletion.value == Deletion.Running) return
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
        if (session != null) {
            store.onSessionAccepted(session)
            usage.purchaser?.let { store.onPurchaser(it, session) }
        }
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

    /** Whether [block] ran without failing; a failure is said through the outcome it leads to. */
    private suspend fun succeeds(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val UNREGISTER_TIMEOUT_MS = 5_000L
    }
}
