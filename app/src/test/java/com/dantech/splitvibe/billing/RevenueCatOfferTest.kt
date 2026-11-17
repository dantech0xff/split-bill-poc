package com.dantech.splitvibe.billing

import com.dantech.splitvibe.ui.paywall.offerText
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.Price
import com.revenuecat.purchases.models.PricingPhase
import com.revenuecat.purchases.models.RecurrenceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The terms of an offer, from the phases Google Play sends through RevenueCat for each of its three
 * kinds of offer to the words the Pro screen states before anything is bought. Google Play sends
 * the full price last, recurring without end, with 0 cycles.
 */
class RevenueCatOfferTest {

    @Test
    fun `a free trial is one free period, then the full price`() {
        val trial = PricingPhase(Period(7, Period.Unit.DAY, "P7D"), RecurrenceMode.FINITE_RECURRING, 1, Price("Free", 0, "USD"))

        val offer = checkNotNull(offerOf(listOf(trial, MONTHLY_FULL_PRICE)))

        assertEquals(listOf(OfferPhase(price = null, BillingPeriod(7, PeriodUnit.DAY), cycles = 1)), offer)
        assertEquals("Free for 7 days, then $3.99 a month.", offerText(FakeBilling.MONTHLY.copy(offer = offer)))
    }

    @Test
    fun `a discounted recurring price is paid for each of its cycles, then the full price`() {
        val discounted = PricingPhase(MONTH, RecurrenceMode.FINITE_RECURRING, 3, Price("$1.99", 1_990_000, "USD"))

        val offer = checkNotNull(offerOf(listOf(discounted, MONTHLY_FULL_PRICE)))

        assertEquals(listOf(OfferPhase("$1.99", BillingPeriod(1, PeriodUnit.MONTH), cycles = 3)), offer)
        assertEquals("$1.99 a month for 3 months, then $3.99 a month.", offerText(FakeBilling.MONTHLY.copy(offer = offer)))
    }

    @Test
    fun `a single payment, which Google Play counts as 0 cycles, covers its period once`() {
        val upFront = PricingPhase(Period(3, Period.Unit.MONTH, "P3M"), RecurrenceMode.NON_RECURRING, 0, Price("$5.99", 5_990_000, "USD"))

        val offer = checkNotNull(offerOf(listOf(upFront, YEARLY_FULL_PRICE)))

        assertEquals(listOf(OfferPhase("$5.99", BillingPeriod(3, PeriodUnit.MONTH), cycles = 1)), offer)
        assertEquals("$5.99 for the first 3 months, then $29.99 a year.", offerText(FakeBilling.YEARLY.copy(offer = offer)))
    }

    @Test
    fun `a phase that counts 0 cycles or none is paid once`() {
        for (count in listOf(0, null)) {
            val phase = PricingPhase(MONTH, RecurrenceMode.FINITE_RECURRING, count, Price("$0.99", 990_000, "USD"))

            val offer = checkNotNull(offerOf(listOf(phase, MONTHLY_FULL_PRICE)))

            assertEquals("$count", "$0.99 for the first month, then $3.99 a month.", offerText(FakeBilling.MONTHLY.copy(offer = offer)))
        }
    }

    @Test
    fun `a plan whose terms cannot be stated is left out`() {
        val endless = PricingPhase(MONTH, RecurrenceMode.INFINITE_RECURRING, 0, Price("$1.99", 1_990_000, "USD"))
        val unknownPeriod = PricingPhase(Period(1, Period.Unit.UNKNOWN, "P1X"), RecurrenceMode.FINITE_RECURRING, 1, Price("Free", 0, "USD"))

        assertNull("a phase without end before the full price", offerOf(listOf(endless, MONTHLY_FULL_PRICE)))
        assertNull("a period this version cannot read", offerOf(listOf(unknownPeriod, MONTHLY_FULL_PRICE)))
    }

    @Test
    fun `the full price alone is no offer`() {
        assertEquals(emptyList<OfferPhase>(), offerOf(listOf(MONTHLY_FULL_PRICE)))
        assertEquals(emptyList<OfferPhase>(), offerOf(emptyList()))
    }

    private companion object {
        val MONTH = Period(1, Period.Unit.MONTH, "P1M")
        val MONTHLY_FULL_PRICE = PricingPhase(MONTH, RecurrenceMode.INFINITE_RECURRING, 0, Price("$3.99", 3_990_000, "USD"))
        val YEARLY_FULL_PRICE =
            PricingPhase(Period(1, Period.Unit.YEAR, "P1Y"), RecurrenceMode.INFINITE_RECURRING, 0, Price("$29.99", 29_990_000, "USD"))
    }
}
