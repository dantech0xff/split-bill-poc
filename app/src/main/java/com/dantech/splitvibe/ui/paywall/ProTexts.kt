package com.dantech.splitvibe.ui.paywall

import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.billing.BillingPeriod
import com.dantech.splitvibe.billing.OfferPhase
import com.dantech.splitvibe.billing.PeriodUnit
import com.dantech.splitvibe.billing.ProMembership
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.billing.ProPlan
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a button that opens the Pro screen says to someone who does not have Pro. */
const val GET_PRO = "Get Pro"

/** The name of a plan, such as "Monthly". */
internal fun nameOf(period: ProPeriod): String = when (period) {
    ProPeriod.MONTHLY -> "Monthly"
    ProPeriod.YEARLY -> "Yearly"
}

/**
 * A plan's full price for each of its billing periods, as Google Play states it: "$3.99 a month",
 * or for the yearly plan "$29.99 a year", never a monthly figure.
 */
internal fun priceText(plan: ProPlan): String = "${plan.price} ${everyText(plan.billingPeriod)}"

/**
 * The terms of the offer Google Play applies first, phase by phase, and the full price after it,
 * such as "Free for 7 days, then $3.99 a month." or "$0.99 for the first month, then $3.99 a
 * month."; null when the plan has no offer.
 */
internal fun offerText(plan: ProPlan): String? {
    if (plan.offer.isEmpty()) return null
    val phases = plan.offer.mapIndexed { index, phase -> phaseText(phase, first = index == 0) }
    return (phases + "then ${priceText(plan)}").joinToString(", ", postfix = ".")
}

/**
 * When Pro renews or ends, such as "Renews on 17 December 2026.", in the phone's time zone; Pro
 * given without an end says that it does not end, and a subscription Google Play is still renewing
 * says so. Null for Pro that is not active otherwise, such as one waiting for a payment, which the
 * notice about the payment covers.
 */
internal fun renewalText(membership: ProMembership, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (!membership.active) return RENEWAL_PENDING.takeIf { membership.awaitingRenewal && !membership.billingIssue }
    val end = membership.expiresAt ?: return "It does not end."
    val day = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(Instant.ofEpochMilli(end).atZone(zone))
    return if (membership.willRenew) "Renews on $day." else "Ends on $day, as it is set not to renew."
}

/**
 * Where the person manages their subscription in Google Play: the page of their Pro subscription
 * while they have one there, also one Google Play is still renewing or waits for a payment for,
 * otherwise the address RevenueCat gave, or the page of all their subscriptions.
 */
internal fun manageUrlOf(membership: ProMembership?): String {
    val subscription = membership?.takeIf { it.fromGooglePlay && (it.active || it.awaitingRenewal) }?.subscriptionId
    if (subscription != null) {
        return "$PLAY_SUBSCRIPTIONS?sku=${URLEncoder.encode(subscription, "UTF-8")}&package=${BuildConfig.APPLICATION_ID}"
    }
    return membership?.managementUrl?.takeIf { it.startsWith("https://") } ?: PLAY_SUBSCRIPTIONS
}

/** Google Play's page of the person's subscriptions. */
internal const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

/** Said of a subscription whose period ended while Google Play has not renewed it yet. */
internal const val RENEWAL_PENDING = "Renewal pending: Google Play is renewing your subscription, which can take a few hours."

/** "Free for 7 days", "$0.99 for the first month" or "$1.99 a month for 3 months". */
private fun phaseText(phase: OfferPhase, first: Boolean): String {
    val total = BillingPeriod(phase.period.count * phase.cycles, phase.period.unit)
    val text = when {
        phase.price == null -> "${if (first) "Free" else "free"} for ${lengthText(total)}"
        phase.cycles == 1 -> "${phase.price} for the first ${if (total.count == 1) unitName(total.unit) else lengthText(total)}"
        else -> "${phase.price} ${everyText(phase.period)} for ${lengthText(total)}"
    }
    return if (first) text else "then $text"
}

/** How often a price is paid: "a month", or "every 3 months". */
private fun everyText(period: BillingPeriod): String =
    if (period.count == 1) "a ${unitName(period.unit)}" else "every ${period.count} ${unitName(period.unit)}s"

/** How long something lasts: "a week", or "7 days". */
private fun lengthText(period: BillingPeriod): String =
    if (period.count == 1) "a ${unitName(period.unit)}" else "${period.count} ${unitName(period.unit)}s"

private fun unitName(unit: PeriodUnit): String = when (unit) {
    PeriodUnit.DAY -> "day"
    PeriodUnit.WEEK -> "week"
    PeriodUnit.MONTH -> "month"
    PeriodUnit.YEAR -> "year"
}
