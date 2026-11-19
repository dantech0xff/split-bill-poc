package com.dantech.splitvibe.ui.paywall

import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.R
import com.dantech.splitvibe.billing.BillingPeriod
import com.dantech.splitvibe.billing.OfferPhase
import com.dantech.splitvibe.billing.PeriodUnit
import com.dantech.splitvibe.billing.ProMembership
import com.dantech.splitvibe.billing.ProPeriod
import com.dantech.splitvibe.billing.ProPlan
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.text
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The name of a plan, such as "Monthly". */
internal fun nameOf(period: ProPeriod): UiText = when (period) {
    ProPeriod.MONTHLY -> text(R.string.pro_monthly)
    ProPeriod.YEARLY -> text(R.string.pro_yearly)
}

/** The name of a plan inside a sentence, such as "monthly" in "Switch to the monthly plan". */
internal fun nameInSentence(period: ProPeriod): UiText = when (period) {
    ProPeriod.MONTHLY -> text(R.string.pro_monthly_in_sentence)
    ProPeriod.YEARLY -> text(R.string.pro_yearly_in_sentence)
}

/**
 * A plan's full price for each of its billing periods, as Google Play states it: "$3.99 a month",
 * or for the yearly plan "$29.99 a year", never a monthly figure.
 */
internal fun priceText(plan: ProPlan): UiText = everyText(plan.price, plan.billingPeriod)

/**
 * The terms of the offer Google Play applies first, phase by phase, and the full price after it,
 * such as "Free for 7 days, then $3.99 a month." or "$0.99 for the first month, then $3.99 a
 * month."; null when the plan has no offer.
 */
internal fun offerText(plan: ProPlan): UiText? {
    if (plan.offer.isEmpty()) return null
    val phases = plan.offer.mapIndexed { index, phase -> phaseText(phase, first = index == 0) }
    val list = (phases + text(R.string.pro_offer_then, priceText(plan))).reduce { list, next -> text(R.string.pro_offer_list, list, next) }
    return text(R.string.pro_offer_end, list)
}

/**
 * When Pro renews or ends, such as "Renews on 17 December 2026.", in the phone's time zone; Pro
 * given without an end says that it does not end, and a subscription Google Play is still renewing
 * says so. Null for Pro that is not active otherwise, such as one waiting for a payment, which the
 * notice about the payment covers.
 */
internal fun renewalText(membership: ProMembership, zone: ZoneId = ZoneId.systemDefault()): UiText? {
    if (!membership.active) return RENEWAL_PENDING.takeIf { membership.awaitingRenewal && !membership.billingIssue }
    val end = membership.expiresAt ?: return text(R.string.pro_does_not_end)
    val day = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(Instant.ofEpochMilli(end).atZone(zone))
    return text(if (membership.willRenew) R.string.pro_renews_on else R.string.pro_ends_on, day)
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

/** Said when no plan can be bought now: Google Play sells none here, or Split Vibe's server could not count it. */
internal val NOTHING_ON_SALE: UiText = text(R.string.pro_nothing_on_sale)

/** Said of a subscription whose period ended while Google Play has not renewed it yet. */
internal val RENEWAL_PENDING: UiText = text(R.string.pro_renewal_pending)

/** "Free for 7 days", "$0.99 for the first month" or "$1.99 a month for 3 months". */
private fun phaseText(phase: OfferPhase, first: Boolean): UiText {
    val total = BillingPeriod(phase.period.count * phase.cycles, phase.period.unit)
    val price = phase.price
    val said = when {
        price == null -> text(if (first) R.string.pro_offer_free_first else R.string.pro_offer_free, lengthText(total))
        phase.cycles == 1 -> plural(firstPeriod(total.unit), total.count, price, total.count)
        else -> text(R.string.pro_offer_for, everyText(price, phase.period), lengthText(total))
    }
    return if (first) said else text(R.string.pro_offer_then, said)
}

/** [price] paid each [period]: "$3.99 a month", or "$9.99 every 3 months". */
private fun everyText(price: String, period: BillingPeriod): UiText {
    val id = when (period.unit) {
        PeriodUnit.DAY -> R.plurals.pro_price_day
        PeriodUnit.WEEK -> R.plurals.pro_price_week
        PeriodUnit.MONTH -> R.plurals.pro_price_month
        PeriodUnit.YEAR -> R.plurals.pro_price_year
    }
    return plural(id, period.count, price, period.count)
}

/** How long something lasts: "a week", or "7 days". */
private fun lengthText(period: BillingPeriod): UiText {
    val id = when (period.unit) {
        PeriodUnit.DAY -> R.plurals.pro_length_day
        PeriodUnit.WEEK -> R.plurals.pro_length_week
        PeriodUnit.MONTH -> R.plurals.pro_length_month
        PeriodUnit.YEAR -> R.plurals.pro_length_year
    }
    return plural(id, period.count)
}

/** "$0.99 for the first month", or "$5.99 for the first 3 months", in the plurals of [unit]. */
private fun firstPeriod(unit: PeriodUnit): Int = when (unit) {
    PeriodUnit.DAY -> R.plurals.pro_first_day
    PeriodUnit.WEEK -> R.plurals.pro_first_week
    PeriodUnit.MONTH -> R.plurals.pro_first_month
    PeriodUnit.YEAR -> R.plurals.pro_first_year
}
