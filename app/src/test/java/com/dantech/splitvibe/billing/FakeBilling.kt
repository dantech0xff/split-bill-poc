package com.dantech.splitvibe.billing

import android.app.Activity
import com.dantech.splitvibe.data.account.Account
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow

/**
 * Stands in for RevenueCat over Google Play. It sells [plans], reports [membership], and fails the
 * next purchase or restore with [failure] when that is set. It notes every start that came while
 * [account] had no session or another purchaser, which must never happen.
 */
class FakeBilling(private val account: StateFlow<Account>) : Billing {
    /** Who the store runs as now, or null while it is stopped. */
    var user: String? = null
        private set

    /** Everyone the store was started as, in order. */
    val starts = mutableListOf<String>()

    /** Starts that came while the account had no session, or another purchaser than the one started as. */
    val startsWithoutTheAccount = mutableListOf<String>()

    /** What was done to the store, in order: "start <purchaser>", "stop", and "sync as <purchaser>". */
    val log = mutableListOf<String>()

    var plans: List<ProPlan> = listOf(MONTHLY, YEARLY)
    var membership: ProMembership? = null

    /** The purchases opened, with the switch each one makes. */
    val purchases = mutableListOf<Pair<ProPlan, PlanChange?>>()
    var restores = 0

    /** What the next purchase or restore fails with. */
    var failure: PurchasesException? = null

    /** While set, a purchase or a restore waits for it to complete, as while Google Play's purchase is open. */
    var gate: CompletableDeferred<Unit>? = null

    /** The next purchase goes through, but RevenueCat's server did not take it, so the SDK worked Pro out on the phone. */
    var nextPurchaseOffline = false

    /** A sync never ends by itself, as when RevenueCat does not answer. */
    var syncHangs = false

    /** What a sync fails with, such as no connection. */
    var syncFailure: PurchasesException? = null

    override suspend fun start(purchaser: String) {
        val now = account.value
        if (now.session == null || now.purchaser != purchaser) startsWithoutTheAccount += purchaser
        starts += purchaser
        log += "start $purchaser"
        user = purchaser
    }

    override suspend fun stop() {
        log += "stop"
        user = null
    }

    override suspend fun plans(): List<ProPlan> = plans.also { requireStarted() }

    override suspend fun membership(): ProMembership? = membership.also { requireStarted() }

    override suspend fun purchase(activity: Activity, plan: ProPlan, change: PlanChange?): ProMembership? {
        requireStarted()
        purchases += plan to change
        gate?.await()
        failure?.let {
            failure = null
            throw it
        }
        val basePlan = plan.productId.substringAfter(':')
        val offline = nextPurchaseOffline.also { nextPurchaseOffline = false }
        return active(productId = "pro", basePlanId = basePlan).copy(offline = offline).also { membership = it }
    }

    override suspend fun restore(): ProMembership? {
        requireStarted()
        restores++
        gate?.await()
        failure?.let {
            failure = null
            throw it
        }
        return membership
    }

    override suspend fun syncPurchases() {
        log += "sync as ${requireStarted()}"
        if (syncHangs) awaitCancellation()
        syncFailure?.let { throw it }
    }

    private fun requireStarted(): String = checkNotNull(user) { "The store was used before it was started" }

    companion object {
        val MONTHLY = ProPlan(ProPeriod.MONTHLY, "pro:monthly", "$3.99", BillingPeriod(1, PeriodUnit.MONTH))
        val YEARLY = ProPlan(ProPeriod.YEARLY, "pro:annual", "$29.99", BillingPeriod(1, PeriodUnit.YEAR))

        /** 17 December 2026 at noon, in UTC. */
        const val RENEWAL = 1_797_508_800_000L

        fun active(productId: String = "pro", basePlanId: String? = "monthly", willRenew: Boolean = true, billingIssue: Boolean = false) =
            ProMembership(
                active = true,
                willRenew = willRenew,
                expiresAt = RENEWAL,
                productId = productId,
                basePlanId = basePlanId,
                billingIssue = billingIssue,
                fromGooglePlay = true,
            )

        /** A purchase or a log-in Google Play or RevenueCat refused with [code]. */
        fun refused(code: PurchasesErrorCode): PurchasesException = PurchasesException(PurchasesError(code))

        /** A purchase Google Play ended with [code]; [cancelled] when the person closed it. */
        fun purchaseFailed(code: PurchasesErrorCode, cancelled: Boolean = false): PurchasesTransactionException =
            PurchasesTransactionException(PurchasesError(code), userCancelled = cancelled)
    }
}
