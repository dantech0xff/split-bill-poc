package com.dantech.splitvibe.billing

import android.app.Activity
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.models.StoreReplacementMode

/**
 * The store that sells Pro, as the app uses it: RevenueCat over Google Play in the app, a stand-in
 * in the tests. It is only ever used as a signed-in account's purchaser, the id the proxy gave for
 * it, and never under an id of its own making. What it reports is shown, not trusted: the proxy
 * decides what Pro gives.
 *
 * Its calls throw RevenueCat's [PurchasesException]; a purchase throws its subclass
 * [PurchasesTransactionException], which says whether the person cancelled.
 */
interface Billing {
    /**
     * Uses the store as [purchaser] from now on: sets it up as them the first time, and logs in as
     * them after someone else. When the log-in fails the store is no longer used at all, so nothing
     * goes on as the person before.
     */
    suspend fun start(purchaser: String)

    /** Stops using the store, so that it no longer calls RevenueCat as whoever used it last. */
    suspend fun stop()

    /** The plans of the current offering that Google Play sells here: its `$rc_monthly` and `$rc_annual` packages. */
    suspend fun plans(): List<ProPlan>

    /**
     * The `pro` entitlement as RevenueCat reports it, or null when the account never had it. With
     * [CacheFetchPolicy.FETCH_CURRENT] RevenueCat's server is asked now, after the store sends what
     * it has not sent yet, rather than the store answering from its own copy.
     */
    suspend fun membership(policy: CacheFetchPolicy): ProMembership?

    /**
     * Opens Google Play's purchase of [plan] over [activity], or with [change] a switch from the plan
     * the person has, and returns Pro as RevenueCat reports it once Google Play is done.
     */
    suspend fun purchase(activity: Activity, plan: ProPlan, change: PlanChange?): ProMembership?

    /** Sends the purchases of the phone's Google Play account to RevenueCat again, as the purchaser, and returns Pro after it. */
    suspend fun restore(): ProMembership?

    /**
     * Sends the purchases of the phone's Google Play account to RevenueCat as whoever the store runs
     * as now, such as one whose sending failed after Google Play took the payment. A payment Google
     * Play has not completed yet cannot be sent.
     */
    suspend fun syncPurchases()
}

/** How often a plan of Pro is paid for. */
enum class ProPeriod { MONTHLY, YEARLY }

/** A length of time as Google Play counts it, such as one month or seven days. */
data class BillingPeriod(val count: Int, val unit: PeriodUnit)

enum class PeriodUnit { DAY, WEEK, MONTH, YEAR }

/** One phase of an offer before the full price: [price] for each [period], [cycles] times; a null [price] is free. */
data class OfferPhase(val price: String?, val period: BillingPeriod, val cycles: Int)

/** A plan of Pro as Google Play sells it to this person, with Play's own prices in their currency. */
data class ProPlan(
    val period: ProPeriod,
    /** Google Play's id of what is bought: the subscription and its base plan, such as "pro:monthly". */
    val productId: String,
    /** The full price of each billing period, formatted by Google Play, such as "$3.99". */
    val price: String,
    /** How long a billing period at the full price lasts. */
    val billingPeriod: BillingPeriod,
    /** The phases of the offer Google Play applies first, such as a free week; empty when there is none. */
    val offer: List<OfferPhase> = emptyList(),
)

/** The `pro` entitlement as RevenueCat reports it. */
data class ProMembership(
    val active: Boolean,
    val willRenew: Boolean,
    /** When the period paid for ends, in epoch milliseconds: Pro renews then, or ends; null for Pro that never ends. */
    val expiresAt: Long?,
    /** The product that gives Pro: Google Play's subscription, such as "pro", or for Pro given in another way its own id. */
    val productId: String,
    /** The base plan at Google Play, such as "monthly", when Pro comes from a subscription there. */
    val basePlanId: String?,
    /** RevenueCat saw that Google Play could not take a payment, and it has not been resolved yet. */
    val billingIssue: Boolean,
    /** Pro comes from a Google Play subscription, which Google Play manages and can switch between plans. */
    val fromGooglePlay: Boolean,
    /** Where RevenueCat says the person manages their subscription, when it says. */
    val managementUrl: String? = null,
    /**
     * RevenueCat's server did not take the purchase this was reported after, so its SDK worked Pro
     * out on the phone from Google Play's purchase. The server does not have that purchase yet, and
     * the SDK sends it later, as whoever it runs as then.
     */
    val offline: Boolean = false,
) {
    /** Google Play's id of the subscription, without its base plan, which a switch of plans names. */
    val subscriptionId: String get() = productId.substringBefore(':')

    /**
     * Pro from a Google Play subscription that RevenueCat no longer reports active, but that is not
     * over: Google Play is still renewing it, which can take hours after a period ends, or waits for
     * a payment it could not take.
     */
    val awaitingRenewal: Boolean get() = !active && fromGooglePlay && (willRenew || billingIssue)

    /** Whether this Pro comes from [plan]. */
    fun isOn(plan: ProPlan): Boolean =
        plan.productId == productId || (basePlanId != null && plan.productId == "$subscriptionId:$basePlanId")
}

/** Switching from the plan the person has: Google Play's id of their subscription, and how Google Play charges the switch. */
data class PlanChange(val oldProductId: String, val replacementMode: StoreReplacementMode)

/** The plans on sale and the person's Pro, together. */
data class ProOffer(val plans: List<ProPlan>, val membership: ProMembership?)
