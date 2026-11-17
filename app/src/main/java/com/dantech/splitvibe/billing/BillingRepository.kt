package com.dantech.splitvibe.billing

import android.app.Activity
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.models.StoreReplacementMode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** One change of the store's user at a time, so that it always ends as the latest account. */
    private val identity = Mutex()

    /**
     * Keeps the store's user the purchaser of the account signed in now, and stops using the store
     * while nobody is signed in or the proxy has not said who the purchaser is. Purchases that the
     * store has not got to RevenueCat yet then go as the account that made them. Runs until
     * cancelled, and nothing it meets stops it: the next purchase starts the store again anyway.
     */
    suspend fun follow() {
        if (!sellsPro) return
        store.account.map { if (it.session != null) it.purchaser else null }.distinctUntilChanged().collect { purchaser ->
            try {
                useAs(purchaser)
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
    suspend fun offer(): ProOffer {
        val billing = ready()
        return ProOffer(plans = billing.plans(), membership = billing.membership())
    }

    /** Buys [plan] over [activity]. Throws [PurchasesTransactionException] when Google Play or RevenueCat did not complete it. */
    suspend fun buy(activity: Activity, plan: ProPlan): ProChange = completed(ready().purchase(activity, plan, change = null))

    /**
     * Switches the Pro the person has, [from], to [plan] at once. Between the two base plans of one
     * subscription Google Play takes only a switch without proration: the new plan starts now, and
     * its price is charged from the next renewal, in both directions.
     */
    suspend fun switchTo(activity: Activity, plan: ProPlan, from: ProMembership): ProChange {
        val change = PlanChange(oldProductId = from.subscriptionId, replacementMode = StoreReplacementMode.WITHOUT_PRORATION)
        return completed(ready().purchase(activity, plan, change))
    }

    /**
     * Restores the purchases of the phone's Google Play account for the signed-in account. A
     * subscription bought by another Split Vibe account stays with it: RevenueCat then refuses, with
     * [PurchasesException], and nothing moves to this account.
     */
    suspend fun restore(): ProChange = completed(ready().restore())

    /** The store as the signed-in account's purchaser. */
    private suspend fun ready(): Billing {
        val billing = checkNotNull(billing) { "This build sells no Pro" }
        val purchaser = store.currentSession()?.let { store.account.value.purchaser } ?: throw SignInNeededException()
        useAs(purchaser)
        return billing
    }

    private suspend fun useAs(purchaser: String?) = identity.withLock {
        val billing = billing ?: return@withLock
        if (purchaser == null) billing.stop() else billing.start(purchaser)
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
}
