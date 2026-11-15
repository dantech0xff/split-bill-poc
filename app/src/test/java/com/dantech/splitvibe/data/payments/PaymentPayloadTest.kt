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
    fun `a payment code is kept with the amount it carries`() {
        val payload = PaymentPayload.of(
            PaymentRequest(link = null, qrPayload = "00020101021238", details = "Bank: ACB", amountFilledIn = true),
        )

        assertEquals("00020101021238", payload.qrPayload)
        assertTrue(payload.amountFilledIn)
    }
}
