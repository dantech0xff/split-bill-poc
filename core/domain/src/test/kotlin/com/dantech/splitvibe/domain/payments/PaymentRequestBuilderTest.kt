package com.dantech.splitvibe.domain.payments

import com.dantech.splitvibe.domain.BillCurrency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The codes and links each network reads. Where a network publishes no specification, the expected
 * text comes character for character from the tests of an open-source implementation that bank
 * apps are known to read: promptpay-qr (github.com/dtinth/promptpay-qr) for PromptPay and
 * vietnam-qr-pay (github.com/xuannghia/vietnam-qr-pay) for VietQR, both MIT. SEPA follows the
 * examples of the European Payments Council's guidelines, EPC069-12 version 3.0.
 */
class PaymentRequestBuilderTest {

    private val thb = BillCurrency("THB", 2)
    private val vnd = BillCurrency("VND", 0)
    private val eur = BillCurrency("EUR", 2)
    private val usd = BillCurrency("USD", 2)
    private val inr = BillCurrency("INR", 2)

    @Test
    fun `PromptPay codes match promptpay-qr for every kind of id`() {
        val cases = mapOf(
            "0801234567" to "00020101021129370016A000000677010111011300668012345675802TH530376463046197",
            "080-123-4567" to "00020101021129370016A000000677010111011300668012345675802TH530376463046197",
            "+66-89-123-4567" to "00020101021129370016A000000677010111011300668912345675802TH5303764630429C1",
            "1111111111111" to "00020101021129370016A000000677010111021311111111111115802TH530376463047B5A",
            "0123456789012" to "00020101021129370016A000000677010111021301234567890125802TH530376463040CBD",
            "012345678901234" to "00020101021129390016A00000067701011103150123456789012345802TH530376463049781",
        )
        cases.forEach { (id, expected) ->
            // Asked in another currency, the code carries no amount: the payer types it.
            val request = build(PaymentMethod.PromptPay(id), 1_000, usd)
            assertEquals(id, expected, request.qrPayload)
            assertFalse(id, request.amountFilledIn)
        }
    }

    @Test
    fun `a PromptPay code in baht carries the amount`() {
        val request = build(PaymentMethod.PromptPay("000-000-0000"), 422, thb)

        assertEquals("00020101021229370016A000000677010111011300660000000005802TH530376454044.226304E469", request.qrPayload)
        assertTrue(request.amountFilledIn)
        assertNull(request.link)
    }

    @Test
    fun `VietQR codes match vietnam-qr-pay with and without an amount`() {
        val acb = PaymentMethod.VietQr("970416", "257678859", "NGUYEN VAN A")

        assertEquals(
            "00020101021138530010A0000007270123000697041601092576788590208QRIBFTTA53037045802VN6304AE9F",
            build(acb, 0, vnd).qrPayload,
        )
        assertEquals(
            "00020101021238530010A0000007270123000697041601092576788590208QRIBFTTA53037045405100005802VN62150811Chuyen tien630453E6",
            build(acb, 10_000, vnd, "Chuyen tien").qrPayload,
        )
        assertEquals(
            "00020101021238530010A0000007270123000697041601092576788590208QRIBFTTA5303704540410005802VN62150811Chuyen tien6304BBB8",
            build(acb, 1_000, vnd, "Chuyen tien").qrPayload,
        )
        assertEquals(
            "00020101021138580010A000000727012800069704070114190304136010180208QRIBFTTA53037045802VN63040283",
            build(PaymentMethod.VietQr("970407", "19030413601018", "TRAN THI B"), 0, vnd).qrPayload,
        )
    }

    @Test
    fun `a VietQR purpose loses its accents and what banks do not take`() {
        val method = PaymentMethod.VietQr("970436", "0011004455667", "LE VAN C")

        val request = build(method, 250_000, vnd, "Phở bò & trà đá, quán Bà Ba!")

        assertTrue(request.qrPayload!!, request.qrPayload!!.contains(EmvQr.field("62", EmvQr.field("08", "Pho bo tra da quan Ba Ba"))))
        assertTrue(request.details, request.details.contains("Note: Phở bò & trà đá, quán Bà Ba!"))
        assertTrue(request.details, request.details.startsWith("Bank: Vietcombank (970436)\nAccount: 0011004455667\nName: LE VAN C\n"))
    }

    @Test
    fun `the SEPA code is the guidelines' example, in UTF-8`() {
        val method = PaymentMethod.SepaQr("François D'Alsace S.A.", "FR14 2004 1010 0505 0001 3M02 606")

        val request = build(method, 1_230, eur, "Client:Marie Louise La Lune")

        // The example in EPC069-12 is written in ISO 8859-1, character set 2; the app always writes
        // UTF-8, character set 1. Every other character is the guidelines' own, 103 in all.
        val example = "BCD\n002\n2\nSCT\n\nFrançois D'Alsace S.A.\nFR1420041010050500013M02606\nEUR12.3\n\n\nClient:Marie Louise La Lune"
        assertEquals(103, example.length)
        assertEquals(example.replaceFirst("\n2\n", "\n1\n"), request.qrPayload)
        assertTrue(request.amountFilledIn)
    }

    @Test
    fun `a SEPA code with a BIC, and one with nothing after the IBAN`() {
        val method = PaymentMethod.SepaQr("Red Cross of Belgium", "BE72000000001616", "BPOTBEB1")

        assertEquals(
            "BCD\n002\n1\nSCT\nBPOTBEB1\nRed Cross of Belgium\nBE72000000001616\nEUR1\n\n\nUrgency fund",
            build(method, 100, eur, "Urgency fund").qrPayload,
        )
        // In dollars, the code leaves the amount for the payer, and the empty lines after the IBAN go.
        val inDollars = build(method, 100, usd)
        assertEquals("BCD\n002\n1\nSCT\nBPOTBEB1\nRed Cross of Belgium\nBE72000000001616", inDollars.qrPayload)
        assertFalse(inDollars.amountFilledIn)
    }

    @Test
    fun `a SEPA remittance text keeps the characters the scheme carries`() {
        val method = PaymentMethod.SepaQr("Jürgen Weiß", "DE89370400440532013000")

        val payload = build(method, 4_250, eur, "Pizza & Bier — Müller's \"Abend\" #3").qrPayload!!

        assertEquals("Pizza Bier Muller's Abend 3", payload.lines().last())
        assertEquals("Jürgen Weiß", payload.lines()[5])
    }

    @Test
    fun `a UPI link is a payer's payment, never a collect request`() {
        val method = PaymentMethod.Upi("asha.rao@okaxis", "Asha Rao")

        val inRupees = build(method, 25_000, inr, "Dinner at Saravana")
        assertEquals("upi://pay?pa=asha.rao@okaxis&pn=Asha%20Rao&am=250.00&cu=INR&tn=Dinner%20at%20Saravana", inRupees.link)
        assertEquals(inRupees.link, inRupees.qrPayload)
        assertTrue(inRupees.amountFilledIn)

        val inDollars = build(method, 1_000, usd, "a".repeat(80))
        assertEquals("upi://pay?pa=asha.rao@okaxis&pn=Asha%20Rao&tn=" + "a".repeat(50), inDollars.link)
        assertFalse(inDollars.amountFilledIn)
    }

    @Test
    fun `a PayPal_Me link carries the amount in the currencies PayPal takes`() {
        val method = PaymentMethod.PayPalMe("DiaRusso")

        // PayPal's own example.
        assertEquals("https://paypal.me/DiaRusso/25AUD", build(method, 2_500, BillCurrency("AUD", 2)).link)
        assertEquals("https://paypal.me/DiaRusso/12.50USD", build(method, 1_250, usd).link)
        assertEquals("https://paypal.me/DiaRusso/1500JPY", build(method, 1_500, BillCurrency("JPY", 0)).link)
        // PayPal takes no forints below one, and no dong at all.
        assertEquals("https://paypal.me/DiaRusso", build(method, 150_050, BillCurrency("HUF", 2)).link)
        val inDong = build(method, 100_000, vnd)
        assertEquals("https://paypal.me/DiaRusso", inDong.link)
        assertFalse(inDong.amountFilledIn)
        assertTrue(inDong.details, inDong.details.contains("Amount: "))
    }

    @Test
    fun `a pasted link and plain details give the details to type in`() {
        val link = build(PaymentMethod.PastedLink(" https://revolut.me/asha "), 1_000, eur, "Tickets")
        assertEquals("https://revolut.me/asha", link.link)
        assertEquals("Pay at: https://revolut.me/asha\nAmount: €10.00\nNote: Tickets", link.details)
        assertFalse(link.amountFilledIn)

        val zelle = build(PaymentMethod.PlainDetails("Zelle", "asha@example.com"), 1_000, usd)
        assertEquals(PaymentRequest(null, null, "Zelle: asha@example.com\nAmount: $10.00", false), zelle)
    }

    @Test
    fun `a method that does not check out gives no link and no code`() {
        listOf(
            PaymentMethod.PastedLink("javascript:alert(1)"),
            PaymentMethod.VietQr("000000", "123456", "X"),
            PaymentMethod.SepaQr("Name", "BE72000000001617"),
            PaymentMethod.Upi("not an id", "Name"),
        ).forEach { method ->
            val request = build(method, 1_000, eur)
            val expected = PaymentRequest(null, null, "This payment method cannot be used.\nAmount: €10.00", false)
            assertEquals(method.toString(), expected, request)
        }
    }

    private fun build(method: PaymentMethod, amountMinor: Long, currency: BillCurrency, note: String? = null) =
        PaymentRequestBuilder.build(method, amountMinor, currency, note, Locale.US)
}
