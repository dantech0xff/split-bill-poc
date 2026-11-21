package com.dantech.splitvibe.domain.payments

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMethodTest {

    @Test
    fun `only secure links to known payment services are taken`() {
        listOf(
            "https://revolut.me/asha",
            "https://wise.com/pay/me/ashar",
            "https://account.venmo.com/u/asha",
            "https://cash.app/\$asha",
            "HTTPS://PayPal.Me/asha",
            "https://paypal.me:443/asha",
        ).forEach { assertNull(it, PaymentMethod.PastedLink(it).problem()) }

        listOf(
            "javascript:alert(1)",
            "intent://pay#Intent;scheme=upi;end",
            "http://revolut.me/asha",
            "https://revolut.me.evil.example/asha",
            "https://evilrevolut.me/asha",
            "https://revolut.me@evil.example/asha",
            "https://revolut.me:8443/asha",
            "https://rеvolut.me/asha",
            "https://example.com/pay",
            "https://revolut.me/asha\nhttps://evil.example",
            "revolut.me/asha",
            "",
        ).forEach { assertNotNull(it, PaymentMethod.PastedLink(it).problem()) }
    }

    @Test
    fun `each method checks what it was given`() {
        assertNull(PaymentMethod.PayPalMe("DiaRusso").problem())
        assertNotNull(PaymentMethod.PayPalMe("dia.russo").problem())
        assertNotNull(PaymentMethod.PayPalMe("a".repeat(21)).problem())

        assertNull(PaymentMethod.Upi("asha.rao@okaxis", "Asha Rao").problem())
        assertNotNull(PaymentMethod.Upi("asha.rao", "Asha Rao").problem())
        assertNotNull(PaymentMethod.Upi("asha.rao@okaxis", " ").problem())

        assertNull(PaymentMethod.PromptPay("081-234-5678").problem())
        assertNull(PaymentMethod.PromptPay("+66 81 234 5678").problem())
        assertNull(PaymentMethod.PromptPay("1-1111-11111-11-1").problem())
        assertNotNull(PaymentMethod.PromptPay("81-234-5678").problem())
        assertNotNull(PaymentMethod.PromptPay("+0812345678").problem())
        assertNotNull(PaymentMethod.PromptPay("0812345678x").problem())

        assertNull(PaymentMethod.VietQr("970416", "000000000", "NGUYEN VAN A").problem())
        assertNotNull(PaymentMethod.VietQr("970416", "257 678", "NGUYEN VAN A").problem())
        assertNotNull(PaymentMethod.VietQr("123456", "000000000", "NGUYEN VAN A").problem())

        assertNull(PaymentMethod.SepaQr("Red Cross of Belgium", "be72 0000 0000 1616", "bpotbeb1").problem())
        assertNotNull(PaymentMethod.SepaQr("Red Cross of Belgium", "BE72000000001617").problem())
        assertNotNull(PaymentMethod.SepaQr("Red Cross of Belgium", "BE72000000001616", "BPOT").problem())
        assertNotNull(PaymentMethod.SepaQr("", "BE72000000001616").problem())

        assertNull(PaymentMethod.PlainDetails("Zelle", "asha@example.com").problem())
        assertNotNull(PaymentMethod.PlainDetails("Zelle", " ").problem())
    }

    @Test
    fun `an IBAN is read the way banks print it`() {
        val method = PaymentMethod.SepaQr("Jürgen Weiß", "de89 3704 0044 0532 0130 00")

        assertEquals("DE89370400440532013000", method.compactIban)
        assertEquals("DE89 3704 0044 0532 0130 00", method.summary)
    }

    @Test
    fun `a method is saved and sent with its kind`() {
        val method: PaymentMethod = PaymentMethod.VietQr("970416", "000000000", "NGUYEN VAN A")

        val json = Json.encodeToString(method)

        assertEquals("""{"type":"vietqr","bankBin":"970416","account":"000000000","accountName":"NGUYEN VAN A"}""", json)
        assertEquals(method, Json.decodeFromString<PaymentMethod>(json))
        assertEquals("ACB · 000000000", method.summary)
    }
}
