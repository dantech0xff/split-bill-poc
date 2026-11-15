package com.dantech.splitvibe.data.payments

import com.dantech.splitvibe.domain.payments.PaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentPayloadTest {

    @Test
    fun `only secure web links and UPI payments go to the page`() {
        assertTrue(PaymentPayload.opensOnTheWeb("https://paypal.me/dana/150000VND"))
        assertTrue(PaymentPayload.opensOnTheWeb("upi://pay?pa=dana@upi&pn=Dana"))
        listOf(
            "http://paypal.me/dana",
            "javascript:alert(1)",
            "intent://pay#Intent;end",
            "data:text/html,hi",
            "https:///nohost",
            "upi://mandate?pa=x",
        )
            .forEach { assertFalse(it, PaymentPayload.opensOnTheWeb(it)) }
    }

    @Test
    fun `a link the page may not open is left out, and then the amount is no longer filled in`() {
        val payload = PaymentPayload.of(
            PaymentRequest(link = "http://pay.example/dana", qrPayload = null, details = "Pay Dana", amountFilledIn = true),
        )

        assertNull(payload.link)
        assertFalse(payload.amountFilledIn)
        assertEquals("Pay Dana", payload.details)
    }

    @Test
    fun `a code longer than the proxy keeps is left out, and the details are cut to what it keeps`() {
        val payload = PaymentPayload.of(
            PaymentRequest(link = null, qrPayload = "0".repeat(513), details = "d".repeat(1_200), amountFilledIn = true),
        )

        assertNull(payload.qrPayload)
        assertEquals(1_000, payload.details.length)
        assertFalse(payload.amountFilledIn)
    }

    @Test
    fun `control characters other than line breaks leave the details, as the proxy refuses them`() {
        val payload = PaymentPayload.of(
            PaymentRequest(
                link = null,
                qrPayload = null,
                details = "Amount: ₫71,500\nNote: Pho\t24\u0007\u0085\r\nThanks",
                amountFilledIn = false,
            ),
        )

        assertEquals("Amount: ₫71,500\nNote: Pho 24  \r\nThanks", payload.details)
    }

    @Test
    fun `a code with a control character other than a line break is left out`() {
        assertNull(
            PaymentPayload.of(PaymentRequest(link = null, qrPayload = "0002\t01", details = "Bank", amountFilledIn = true)).qrPayload,
        )
        // The SEPA code breaks its lines, which the proxy takes.
        assertEquals(
            "BCD\n002",
            PaymentPayload.of(PaymentRequest(link = null, qrPayload = "BCD\n002", details = "Bank", amountFilledIn = true)).qrPayload,
        )
    }

    @Test
    fun `a link with whitespace in it, or longer than the proxy keeps, is left out`() {
        assertFalse(PaymentPayload.opensOnTheWeb("upi://pay?pa=dana@upi&pn=Dana Nguyen"))
        assertFalse(PaymentPayload.opensOnTheWeb("https://paypal.me/dana\u00a0x"))
        assertFalse(PaymentPayload.opensOnTheWeb("https://paypal.me/" + "d".repeat(2_031)))
        assertTrue(PaymentPayload.opensOnTheWeb("https://paypal.me/" + "d".repeat(2_030)))
        assertFalse("Upper case is not the scheme the page opens", PaymentPayload.opensOnTheWeb("HTTPS://paypal.me/dana"))
    }

    @Test
    fun `details are cut by code points, never in the middle of one`() {
        val payload = PaymentPayload.of(PaymentRequest(link = null, qrPayload = null, details = "😀".repeat(1_200), amountFilledIn = false))

        assertEquals(1_000, payload.details.codePointCount(0, payload.details.length))
    }

    @Test
    fun `a payment code is kept with the amount it carries`() {
        val payload = PaymentPayload.of(
            PaymentRequest(link = null, qrPayload = "00020101021238", details = "Bank: ACB", amountFilledIn = true),
        )

        assertEquals("00020101021238", payload.qrPayload)
        assertTrue(payload.amountFilledIn)
    }
}
