package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRateTest {

    private val usd = BillCurrency("USD", 2)
    private val vnd = BillCurrency("VND", 0)

    /** What one euro buys, as the proxy hands it out. */
    private val perEuro = mapOf(
        "EUR" to (BigDecimal.ONE to "2026-11-14"),
        "USD" to (BigDecimal("1.0800") to "2026-11-14"),
        "VND" to (BigDecimal("27378") to "2026-11-13"),
    )

    @Test
    fun `the rate between two currencies is worked out through the base, with the older day`() {
        val rate = ExchangeRate.between("USD", "VND", perEuro, "Frankfurter")!!

        assertEquals(0, BigDecimal("25350").compareTo(rate.rate))
        assertEquals("2026-11-13", rate.date)
        assertEquals("Frankfurter", rate.source)
        assertFalse(rate.isByHand)
    }

    @Test
    fun `a currency without a usable rate gives no rate`() {
        assertNull(ExchangeRate.between("USD", "THB", perEuro, "Frankfurter"))
        assertNull(ExchangeRate.between("USD", "VND", perEuro + ("USD" to (BigDecimal.ZERO to "2026-11-14")), "Frankfurter"))
    }

    @Test
    fun `an amount is converted to the nearest minor unit of the other currency`() {
        val rate = ExchangeRate("USD", "VND", BigDecimal("25350"))

        assertEquals(2_535_000L, rate.convert(10_000, usd, vnd))
        // 0.01 USD is 253.5 dong, which rounds half up.
        assertEquals(254L, rate.convert(1, usd, vnd))
        assertEquals(-1_267_500L, rate.convert(-5_000, usd, vnd))
    }

    @Test
    fun `a rate so large the result does not fit converts to nothing`() {
        val rate = ExchangeRate("USD", "VND", BigDecimal("1E+30"))

        assertNull(rate.convert(10_000, usd, vnd))
    }

    @Test
    fun `a rate is shown with six significant digits, grouped`() {
        assertEquals("1 USD = 25,350 VND", ExchangeRate("USD", "VND", BigDecimal("25350.0000")).describe(Locale.US))
        assertEquals("1 VND = 0.0000394477 USD", ExchangeRate("VND", "USD", BigDecimal("0.00003944773175542406")).describe(Locale.US))
        assertEquals("1.08", ExchangeRate.rateText(BigDecimal("1.0800"), Locale.US))
    }

    @Test
    fun `a rate converts a bill only from its currency into the local one`() {
        val bill = Bill.empty(usd)
        val rate = ExchangeRate("USD", "VND", BigDecimal("25350"))

        assertTrue(rate.converts(bill, "VND"))
        assertFalse(rate.converts(bill, "EUR"))
        assertFalse(rate.converts(Bill.empty(vnd), "VND"))
    }
}
