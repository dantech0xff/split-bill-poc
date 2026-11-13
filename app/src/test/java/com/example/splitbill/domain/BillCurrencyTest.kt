package com.example.splitbill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class BillCurrencyTest {

    @Test
    fun `known currencies use their ISO decimals`() {
        assertEquals(BillCurrency("VND", 0), BillCurrency.resolve("vnd", emptyList()))
        assertEquals(BillCurrency("USD", 2), BillCurrency.resolve(" USD ", emptyList()))
        assertEquals(BillCurrency("JPY", 0), BillCurrency.resolve("JPY", emptyList()))
        assertEquals(BillCurrency("KWD", 3), BillCurrency.resolve("KWD", emptyList()))
    }

    @Test
    fun `unknown currency infers decimals from the amounts on the bill`() {
        assertEquals(BillCurrency("", 2), BillCurrency.resolve("XXX", listOf(BigDecimal("12.5"), BigDecimal("3"))))
        assertEquals(BillCurrency("", 0), BillCurrency.resolve("", listOf(BigDecimal("70000"), BigDecimal("25000.00"))))
        assertEquals(BillCurrency("ABC", 2), BillCurrency.resolve("abc", listOf(BigDecimal("9.99"))))
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

        assertEquals("$12.50", formattedUsd)
        assertTrue(formattedVnd, formattedVnd.contains("70.000") && formattedVnd.contains("₫"))
        assertEquals("12.50 ABC", BillCurrency("ABC", 2).format(1_250, Locale.US))
        assertEquals("1,250", BillCurrency("", 0).format(1_250, Locale.US))
    }
}
