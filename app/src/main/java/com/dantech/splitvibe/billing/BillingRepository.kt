package com.dantech.splitvibe.billing

import android.app.Activity
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.models.StoreReplacementMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Buying, switching or restoring Pro needs a Google sign-in, and the purchaser the proxy gave for it. */
class SignInNeededException : Exception("Pro is bought only while signed in")

/**
 * What a purchase, a switch of plans or a restore left: Pro as RevenueCat reports it, and the plan
 * the proxy counts the calls against once it asked RevenueCat itself, or null when it could not be
 * asked just then. Only the proxy's plan decides the calls.
 */
data class ProChange(val membership: ProMembership?, val plan: Plan?)

/**
 * Pro for the Pro screen: the plans Google Play sells, the person's Pro, buying it, switching
 * between monthly and yearly, and restoring it. Only a signed-in account buys, as its purchaser,
 * so a purchase always belongs to a Split Vibe account. After each of them the proxy asks
 * RevenueCat for the account's Pro at once, rather than waiting for RevenueCat's notice.
 */
class BillingRepository(
    /** The store, or null in a build without RevenueCat's key, which sells no Pro. */
    private val billing: Billing?,
    private val api: ProxyApi,
    private val store: AccountStore,
    /** Whether this build can sign in with Google, as [AccountRepository.canSignIn] says. */
    canSignIn: Boolean,
) {
    /** Whether this build sells Pro: it has RevenueCat's key and can sign in, as only a signed-in account buys. */
    val sellsPro: Boolean = billing != null && canSignIn

    /**
     * One change of the store's user at a time, so that it always ends as the latest account. A
     * purchase, a restore and reading the offer hold it too, so the store neither stops nor changes
     * user in the middle of one: a sign-out waits until Google Play is done.
     */
    private val identity = Mutex()

    /** Who the store runs as, as it was last started here; null while it is stopped. Changed only under [identity]. */
    private var runningAs: String? = null

    /**
     * The purchaser one of whose purchases in this run of the app may not have reached RevenueCat:
     * its sending failed, its payment is pending, or the purchase ended unseen. Changed only under
     * [identity], and only while the store runs as that purchaser.
     */
    private var unsentBy: String? = null

    /**
     * Keeps the store's user the purchaser of the account signed in now, and stops using the store
     * while nobody is signed in or the proxy has not said who the purchaser is. RevenueCat's SDK
     * sends a purchase it has not got to RevenueCat yet as whoever it runs as when it next tries,
     * not as the account that made it. So when this run saw a purchase left unsent, the store first
     * gets a few seconds to send it as its buyer. Runs until cancelled, and nothing it meets stops
     * it: the next purchase starts the store again anyway.
     */
    suspend fun follow() {
        if (!sellsPro) return
        store.account.map { if (it.session != null) it.purchaser else null }.distinctUntilChanged().collect { purchaser ->
            try {
                identity.withLock { useAs(purchaser) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Such as no connection for the log-in, or a phone that is locked; the store is not
                // used until the next start works.
            }
        }
    }

    /**
     * `POST /v1/plan`: the proxy asks RevenueCat for the signed-in account's Pro and keeps it, and
     * the calls left, the AI switch and the purchaser it answers are kept. Returns the calls left.
     * Throws [ProxyException], also when nobody is signed in, and [IOException] without a connection.
     */
    suspend fun refreshPlan(): CallsLeft {
        val session = store.currentSession() ?: throw ProxyException(401, ProxyApi.SESSION_REFUSED, null, null)
        val usage = try {
            api.refreshPlan()
        } catch (e: ProxyException) {
            if (e.code == ProxyApi.SESSION_REFUSED) store.expireSession()
            throw e
        }
        // An answer that arrives after signing out, or after someone else signed in, is nobody's now.
        if (store.account.value.session == session) {
            store.onCallsLeft(usage.callsLeft)
            store.onAiEnabled(usage.aiEnabled)
            usage.purchaser?.let { store.onPurchaser(it, session) }
        }
        return usage.callsLeft
    }

    /**
     * The plans on sale and the person's Pro, as RevenueCat reports them to the signed-in account.
     * Throws [SignInNeededException] before the account has a purchaser, and [PurchasesException].
     */
    suspend fun offer(): ProOffer = asPurchaser { billing, _ -> ProOffer(plans = billing.plans(), membership = billing.membership()) }

    /** Buys [plan] over [activity]. Throws [PurchasesTransactionException] when Google Play or RevenueCat did not complete it. */
    suspend fun buy(activity: Activity, plan: ProPlan): ProChange = completed(purchase(activity, plan, change = null))

    /**
     * Switches the Pro the person has, [from], to [plan] at once. Between the two base plans of one
     * subscription Google Play takes only a switch without proration: the new plan starts now, and
     * its price is charged from the next renewal, in both directions.
     */
    suspend fun switchTo(activity: Activity, plan: ProPlan, from: ProMembership): ProChange {
        val change = PlanChange(oldProductId = from.subscriptionId, replacementMode = StoreReplacementMode.WITHOUT_PRORATION)
        return completed(purchase(activity, plan, change))
    }

    /**
     * Restores the purchases of the phone's Google Play account for the signed-in account. A
     * subscription bought by another Split Vibe account stays with it: RevenueCat then refuses, with
     * [PurchasesException], and nothing moves to this account.
     */
    suspend fun restore(): ProChange = completed(asPurchaser { billing, _ -> billing.restore() })

    /**
     * Google Play's purchase of [plan], or with [change] a switch, noting when it may have left the
     * purchase unsent: RevenueCat's server failed and its SDK worked Pro out on the phone, Google
     * Play still waits for the payment, a failure came after Google Play may have taken it, or the
     * purchase ended unseen.
     */
    private suspend fun purchase(activity: Activity, plan: ProPlan, change: PlanChange?): ProMembership? =
        asPurchaser { billing, purchaser ->
            try {
                billing.purchase(activity, plan, change).also { if (it?.offline == true) unsentBy = purchaser }
            } catch (e: PurchasesException) {
                if (e.code !in NOTHING_LEFT_TO_SEND) unsentBy = purchaser
                throw e
            } catch (e: CancellationException) {
                unsentBy = purchaser
                throw e
            }
        }

    /** Runs [block] on the store as the signed-in account's purchaser, under [identity]. */
    private suspend fun <T> asPurchaser(block: suspend (Billing, String) -> T): T = identity.withLock {
        val billing = checkNotNull(billing) { "This build sells no Pro" }
        val purchaser = store.currentSession()?.let { store.account.value.purchaser } ?: throw SignInNeededException()
        useAs(purchaser)
        block(billing, purchaser)
    }

    /** Uses the store as [purchaser], or stops it for null. Called only under [identity]. */
    private suspend fun useAs(purchaser: String?) {
        val billing = billing ?: return
        val leaving = runningAs?.takeIf { it != purchaser }
        if (leaving != null) {
            if (unsentBy == leaving) sendUnsent(billing)
            unsentBy = null
        }
        runningAs = null
        if (purchaser == null) {
            billing.stop()
        } else {
            billing.start(purchaser)
            runningAs = purchaser
        }
    }

    /**
     * Gives the store a few seconds to send what the phone's Google Play account bought, as whoever
     * it runs as now. When that fails or takes longer, the store stops or changes user all the same.
     */
    private suspend fun sendUnsent(billing: Billing) {
        try {
            withTimeoutOrNull(SEND_UNSENT_MILLIS) { billing.syncPurchases() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Such as no connection, or RevenueCat keeping a purchase with the account that bought it.
        }
    }

    /** After a purchase, a switch or a restore: the proxy's count follows at once, when the proxy can be asked now. */
    private suspend fun completed(membership: ProMembership?): ProChange {
        val plan = try {
            refreshPlan().plan
        } catch (e: ProxyException) {
            null
        } catch (e: IOException) {
            null
        }
        return ProChange(membership, plan)
    }

    private companion object {
        /** How long stopping or changing the store's user waits for a purchase left unsent to be sent. */
        const val SEND_UNSENT_MILLIS = 5_000L

        /** How a purchase can end with nothing left to send: nothing was bought, or RevenueCat already decided on it. */
        val NOTHING_LEFT_TO_SEND = setOf(
            PurchasesErrorCode.PurchaseCancelledError,
            PurchasesErrorCode.ProductAlreadyPurchasedError,
            PurchasesErrorCode.ReceiptAlreadyInUseError,
            PurchasesErrorCode.PurchaseNotAllowedError,
            PurchasesErrorCode.ProductNotAvailableForPurchaseError,
            PurchasesErrorCode.ConfigurationError,
        )
    }
}
