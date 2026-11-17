package com.dantech.splitvibe.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.Store
import com.revenuecat.purchases.VerificationResult
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.awaitSyncPurchases
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.RecurrenceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RevenueCat's SDK over Google Play Billing, set up with the build's public SDK key [apiKey]. It is
 * set up only once [start] names a purchaser, never anonymously: the app never logs out of it, as
 * that would make an anonymous id, and it closes the SDK instead when nobody is signed in.
 */
class RevenueCatBilling(context: Context, private val apiKey: String) : Billing {

    private val context: Context = context.applicationContext

    // The SDK keeps one instance for the process; it is set up and closed on the main thread, where
    // it watches the app come to the foreground.
    override suspend fun start(purchaser: String) {
        withContext(Dispatchers.Main) {
            if (!Purchases.isConfigured) {
                Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).appUserID(purchaser).build())
            } else {
                val purchases = Purchases.sharedInstance
                if (purchases.appUserID != purchaser) logIn(purchases, purchaser)
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            if (Purchases.isConfigured) Purchases.sharedInstance.close()
        }
    }

    override suspend fun plans(): List<ProPlan> {
        val offering = purchases().awaitOfferings().current ?: return emptyList()
        return ProPeriod.entries.mapNotNull { period -> packageOf(offering, period)?.let { planOf(it, period) } }
    }

    override suspend fun membership(policy: CacheFetchPolicy): ProMembership? = membershipOf(purchases().awaitCustomerInfo(policy))

    override suspend fun purchase(activity: Activity, plan: ProPlan, change: PlanChange?): ProMembership? {
        val purchases = purchases()
        // Bought from the offering as it is now, and only when it still sells what the person saw.
        val offering = purchases.awaitOfferings().current
        val sold = offering?.let { packageOf(it, plan.period) }?.takeIf { it.product.id == plan.productId }
            ?: throw PurchasesException(PurchasesError(PurchasesErrorCode.ProductNotAvailableForPurchaseError))
        val params = PurchaseParams.Builder(activity, sold)
        change?.let { params.oldProductId(it.oldProductId).replacementMode(it.replacementMode) }
        return membershipOf(purchases.awaitPurchase(params.build()).customerInfo)
    }

    override suspend fun restore(): ProMembership? = membershipOf(purchases().awaitRestore())

    override suspend fun syncPurchases() {
        purchases().awaitSyncPurchases()
    }

    private suspend fun logIn(purchases: Purchases, purchaser: String) {
        try {
            purchases.awaitLogIn(purchaser)
        } catch (e: PurchasesException) {
            // Still the person before. Closed, the SDK sends nothing more as them, and the next start
            // sets it up afresh as the purchaser.
            purchases.close()
            throw e
        }
    }

    private fun purchases(): Purchases {
        check(Purchases.isConfigured) { "RevenueCat is used only once a purchaser is signed in" }
        return Purchases.sharedInstance
    }

    private fun packageOf(offering: Offering, period: ProPeriod): Package? = when (period) {
        ProPeriod.MONTHLY -> offering.monthly
        ProPeriod.YEARLY -> offering.annual
    }

    /**
     * The plan [sold] is, with the phases of the offer that buying it applies, which is the
     * product's default option. A plan whose terms cannot be read is left out, as they could not
     * be shown before it is bought.
     */
    private fun planOf(sold: Package, period: ProPeriod): ProPlan? {
        val product = sold.product
        val billingPeriod = product.period?.let(::periodOf) ?: return null
        val offer = offerOf(product.defaultOption?.pricingPhases.orEmpty()) ?: return null
        return ProPlan(period, productId = product.id, price = product.price.formatted, billingPeriod = billingPeriod, offer = offer)
    }

    private fun membershipOf(info: CustomerInfo): ProMembership? {
        val pro = info.entitlements[ENTITLEMENT] ?: return null
        return ProMembership(
            active = pro.isActive,
            willRenew = pro.willRenew,
            expiresAt = pro.expirationDate?.time,
            productId = pro.productIdentifier,
            basePlanId = pro.productPlanIdentifier,
            billingIssue = pro.billingIssueDetectedAt != null,
            fromGooglePlay = pro.store == Store.PLAY_STORE,
            managementUrl = info.managementURL?.toString(),
            // How the SDK marks what it worked out itself after RevenueCat's server failed to take a purchase.
            offline = info.entitlements.verification == VerificationResult.VERIFIED_ON_DEVICE,
        )
    }

    private companion object {
        /** The entitlement Pro is in RevenueCat. */
        const val ENTITLEMENT = "pro"
    }
}

/**
 * The phases of an offer that come before the full price, from all the [phases] of what is bought,
 * the full price last, as Google Play sends them through RevenueCat. Null when the terms cannot be
 * stated: a phase that never ends would come before the full price, or a period cannot be read.
 *
 * Google Play counts a single payment, such as $5.99 for the first 3 months, as 0 cycles of its
 * period, and RevenueCat passes the 0 on; it is paid once and covers its period once. A count of 0
 * or none for any other phase is read the same way.
 */
internal fun offerOf(phases: List<PricingPhase>): List<OfferPhase>? {
    return phases.dropLast(1).map { phase ->
        if (phase.recurrenceMode == RecurrenceMode.INFINITE_RECURRING) return null
        val cycles = if (phase.recurrenceMode == RecurrenceMode.NON_RECURRING) 1 else phase.billingCycleCount?.takeIf { it > 0 } ?: 1
        OfferPhase(
            price = phase.price.takeIf { it.amountMicros > 0 }?.formatted,
            period = periodOf(phase.billingPeriod) ?: return null,
            cycles = cycles,
        )
    }
}

/** [period] as the app counts it, or null for one Google Play states in a unit this version does not know. */
internal fun periodOf(period: Period): BillingPeriod? {
    val unit = when (period.unit) {
        Period.Unit.DAY -> PeriodUnit.DAY
        Period.Unit.WEEK -> PeriodUnit.WEEK
        Period.Unit.MONTH -> PeriodUnit.MONTH
        Period.Unit.YEAR -> PeriodUnit.YEAR
        Period.Unit.UNKNOWN -> return null
    }
    return BillingPeriod(period.value, unit).takeIf { it.count > 0 }
}
