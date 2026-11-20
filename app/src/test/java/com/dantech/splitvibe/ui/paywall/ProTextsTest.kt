package com.dantech.splitvibe.ui.paywall

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.billing.BillingPeriod
import com.dantech.splitvibe.billing.FakeBilling
import com.dantech.splitvibe.billing.OfferPhase
import com.dantech.splitvibe.billing.PeriodUnit
import com.dantech.splitvibe.english
import com.dantech.splitvibe.inEnglish
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** What the Pro screen says about prices, offers, renewals and where to manage a subscription. */
@RunWith(AndroidJUnit4::class)
class ProTextsTest {

    private val month = BillingPeriod(1, PeriodUnit.MONTH)

    @Test
    fun `each plan says its own price for its own period, the yearly one a year's`() {
        assertEquals("$3.99 a month", priceText(FakeBilling.MONTHLY).english())
        assertEquals("$29.99 a year", priceText(FakeBilling.YEARLY).english())
        val quarterly = FakeBilling.MONTHLY.copy(price = "€9.99", billingPeriod = BillingPeriod(3, PeriodUnit.MONTH))
        assertEquals("€9.99 every 3 months", priceText(quarterly).english())
    }

    @Test
    fun `a plan without an offer says nothing more`() {
        assertNull(offerText(FakeBilling.MONTHLY).inEnglish())
    }

    @Test
    fun `a free trial says how long it lasts and what is charged after it`() {
        val week = FakeBilling.MONTHLY.copy(offer = listOf(OfferPhase(price = null, BillingPeriod(7, PeriodUnit.DAY), cycles = 1)))
        val twoWeeks = FakeBilling.YEARLY.copy(offer = listOf(OfferPhase(price = null, BillingPeriod(1, PeriodUnit.WEEK), cycles = 2)))

        assertEquals("Free for 7 days, then $3.99 a month.", offerText(week).inEnglish())
        assertEquals("Free for 2 weeks, then $29.99 a year.", offerText(twoWeeks).inEnglish())
    }

    @Test
    fun `an intro price says what it costs and for how long`() {
        val firstMonth = FakeBilling.MONTHLY.copy(offer = listOf(OfferPhase("$0.99", month, cycles = 1)))
        val threeMonths = FakeBilling.MONTHLY.copy(offer = listOf(OfferPhase("$1.99", month, cycles = 3)))
        val paidUpFront = FakeBilling.YEARLY.copy(offer = listOf(OfferPhase("$5.99", BillingPeriod(3, PeriodUnit.MONTH), cycles = 1)))

        assertEquals("$0.99 for the first month, then $3.99 a month.", offerText(firstMonth).inEnglish())
        assertEquals("$1.99 a month for 3 months, then $3.99 a month.", offerText(threeMonths).inEnglish())
        assertEquals("$5.99 for the first 3 months, then $29.99 a year.", offerText(paidUpFront).inEnglish())
    }

    @Test
    fun `a free trial followed by an intro price says both, in order`() {
        val plan = FakeBilling.MONTHLY.copy(
            offer = listOf(OfferPhase(null, BillingPeriod(1, PeriodUnit.WEEK), cycles = 1), OfferPhase("$1.99", month, cycles = 2)),
        )

        assertEquals("Free for a week, then $1.99 a month for 2 months, then $3.99 a month.", offerText(plan).inEnglish())
    }

    @Test
    fun `Pro says when it renews, or when it ends when it will not renew`() {
        assertEquals("Renews on December 17, 2026.", renewalText(FakeBilling.active(), ZoneOffset.UTC).inEnglish())
        val ending = FakeBilling.active(willRenew = false)
        assertEquals("Ends on December 17, 2026, as it is set not to renew.", renewalText(ending, ZoneOffset.UTC).inEnglish())
        assertEquals("It does not end.", renewalText(FakeBilling.active().copy(expiresAt = null), ZoneOffset.UTC).inEnglish())
    }

    @Test
    fun `a subscription Google Play is still renewing says so, and one waiting for a payment leaves it to the payment's notice`() {
        val renewing = FakeBilling.active(willRenew = true).copy(active = false)
        val onHold = FakeBilling.active(willRenew = false, billingIssue = true).copy(active = false)
        val ended = FakeBilling.active(willRenew = false).copy(active = false)

        assertEquals(
            "Renewal pending: Google Play is renewing your subscription, which can take a few hours.",
            renewalText(renewing).inEnglish(),
        )
        assertNull(renewalText(onHold).inEnglish())
        assertNull(renewalText(ended).inEnglish())
    }

    @Test
    fun `Google Play's page of the subscription while there is one, else RevenueCat's address or all subscriptions`() {
        val subscription = "https://play.google.com/store/account/subscriptions?sku=pro&package=${BuildConfig.APPLICATION_ID}"
        assertEquals(subscription, manageUrlOf(FakeBilling.active(productId = "pro:monthly")))
        assertEquals("one Google Play is still renewing", subscription, manageUrlOf(FakeBilling.active().copy(active = false)))
        val onHold = FakeBilling.active(willRenew = false, billingIssue = true).copy(active = false)
        assertEquals("one waiting for a payment", subscription, manageUrlOf(onHold))
        val ended = FakeBilling.active(willRenew = false)
            .copy(active = false, managementUrl = "https://play.google.com/store/account/subscriptions?x=1")
        assertEquals("https://play.google.com/store/account/subscriptions?x=1", manageUrlOf(ended))
        val given = FakeBilling.active(productId = "rc_promo_pro_lifetime").copy(fromGooglePlay = false)
        assertEquals(PLAY_SUBSCRIPTIONS, manageUrlOf(given))
        assertEquals(PLAY_SUBSCRIPTIONS, manageUrlOf(null))
        assertEquals("only an https address is opened", PLAY_SUBSCRIPTIONS, manageUrlOf(ended.copy(managementUrl = "intent://x")))
    }
}
