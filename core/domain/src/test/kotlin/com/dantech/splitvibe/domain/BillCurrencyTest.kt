package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillCurrencyTest {

    private fun amounts(vararg values: String) = values.map(::BigDecimal)

    @Test
    fun `bill is never split finer than its prices are printed`() {
        // ISO 4217 gives rupiah, forint and New Taiwan dollar two decimals that nobody pays with.
        assertEquals(BillCurrency("IDR", 0), BillCurrency.resolve("IDR", printedDecimals = 0, amounts("103400", "70000")))
        assertEquals(BillCurrency("HUF", 0), BillCurrency.resolve("huf", printedDecimals = 0, amounts("4590")))
        assertEquals(BillCurrency("TWD", 0), BillCurrency.resolve("TWD", printedDecimals = 0, amounts("320")))
        assertEquals(BillCurrency("USD", 2), BillCurrency.resolve(" USD ", printedDecimals = 2, amounts("17", "18.95")))
    }

    @Test
    fun `bill is never split finer than the currency allows`() {
        assertEquals(BillCurrency("VND", 0), BillCurrency.resolve("vnd", printedDecimals = 3, amounts("70000")))
        assertEquals(BillCurrency("JPY", 0), BillCurrency.resolve("JPY", printedDecimals = 2, amounts("1200")))
        assertEquals(BillCurrency("KWD", 3), BillCurrency.resolve("KWD", printedDecimals = 3, amounts("1.250")))
    }

    @Test
    fun `decimals that are really on the bill are kept even if the reported precision is lower`() {
        assertEquals(BillCurrency("USD", 2), BillCurrency.resolve("USD", printedDecimals = 0, amounts("12.5", "3.25")))
        assertEquals(BillCurrency("IDR", 1), BillCurrency.resolve("IDR", printedDecimals = 0, amounts("33333.5")))
    }

    @Test
    fun `without a reported precision the currency's own decimals are used`() {
        assertEquals(BillCurrency("USD", 2), BillCurrency.resolve("USD", printedDecimals = null, amounts("35")))
        assertEquals(BillCurrency("VND", 0), BillCurrency.resolve("VND", printedDecimals = null, emptyList()))
    }

    @Test
    fun `unknown currency goes by what the bill shows`() {
        assertEquals(BillCurrency("", 2), BillCurrency.resolve("XXX", printedDecimals = null, amounts("12.5", "3")))
        assertEquals(BillCurrency("", 0), BillCurrency.resolve("", printedDecimals = null, amounts("70000", "25000.00")))
        assertEquals(BillCurrency("ABC", 2), BillCurrency.resolve("abc", printedDecimals = 2, amounts("9")))
    }

    @Test
    fun `currency codes are recognised`() {
        assertTrue(BillCurrency.isKnownCode("vnd"))
        assertTrue(BillCurrency.isKnownCode(" EUR "))
        assertFalse(BillCurrency.isKnownCode("XXX"))
        assertFalse(BillCurrency.isKnownCode("DONG"))
        assertFalse(BillCurrency.isKnownCode(""))
    }

    @Test
    fun `amounts convert to minor units and back`() {
        val usd = BillCurrency("USD", 2)
        val vnd = BillCurrency("VND", 0)

        assertEquals(1_250L, usd.toMinor(BigDecimal("12.5")))
        assertEquals(1_235L, usd.toMinor(BigDecimal("12.345")))
        assertEquals(70_000L, vnd.toMinor(BigDecimal("70000")))
        assertEquals(BigDecimal("12.50"), usd.toMajor(1_250))
    }

    @Test
    fun `formatting follows the currency and the locale`() {
        val formattedUsd = BillCurrency("USD", 2).format(1_250, Locale.US)
        val formattedVnd = BillCurrency("VND", 0).format(70_000, Locale.forLanguageTag("vi-VN"))
        val formattedIdr = BillCurrency("IDR", 0).format(33_334, Locale.forLanguageTag("id-ID"))

        assertEquals("$12.50", formattedUsd)
        assertTrue(formattedVnd, formattedVnd.contains("70.000") && formattedVnd.contains("₫"))
        assertTrue(formattedIdr, formattedIdr.contains("33.334") && !formattedIdr.contains(","))
        assertEquals("12.50 ABC", BillCurrency("ABC", 2).format(1_250, Locale.US))
        assertEquals("1,250", BillCurrency("", 0).format(1_250, Locale.US))
    }

    @Test
    fun `typed amounts are read whichever separator people use`() {
        val usd = BillCurrency("USD", 2)
        val vnd = BillCurrency("VND", 0)

        assertEquals(1_250L, usd.parse("12.50"))
        assertEquals(1_250L, usd.parse("12,5"))
        assertEquals(123_456L, usd.parse("1,234.56"))
        assertEquals(123_456L, usd.parse("1.234,56"))
        assertEquals(100_000L, usd.parse("1,000"))
        assertEquals(-300L, usd.parse("-3"))
        assertEquals(65_000L, vnd.parse("65.000"))
        assertEquals(1_250_000L, vnd.parse("1,250,000"))
        assertEquals(null, usd.parse(""))
        assertEquals(null, usd.parse("12a"))
        assertEquals(null, usd.parse("-"))
    }

    @Test
    fun `an amount written for editing reads back the same`() {
        val usd = BillCurrency("USD", 2)

        assertEquals("12.50", usd.inputText(1_250))
        assertEquals(1_250L, usd.parse(usd.inputText(1_250)))
        assertEquals("65000", BillCurrency("VND", 0).inputText(65_000))
    }
}
