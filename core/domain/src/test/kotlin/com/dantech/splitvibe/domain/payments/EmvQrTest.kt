package com.dantech.splitvibe.domain.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmvQrTest {

    @Test
    fun `the CRC is CRC-16 CCITT-FALSE`() {
        // The check value every CRC catalogue gives for this variant.
        assertEquals(0x29B1, EmvQr.crc16("123456789"))
        assertEquals(0xFFFF, EmvQr.crc16(""))
    }

    @Test
    fun `a field is its id, its length in two digits and its value`() {
        assertEquals("5802TH", EmvQr.field("58", "TH"))
        assertEquals("0011Chuyen tien", EmvQr.field("00", "Chuyen tien"))
        assertThrows(IllegalArgumentException::class.java) { EmvQr.field("5", "TH") }
        assertThrows(IllegalArgumentException::class.java) { EmvQr.field("58", "") }
        assertThrows(IllegalArgumentException::class.java) { EmvQr.field("58", "x".repeat(100)) }
        assertThrows(IllegalArgumentException::class.java) { EmvQr.field("08", "Chuyển tiền") }
    }

    @Test
    fun `a CRC below 0x1000 keeps its leading zero`() {
        // From the vietnam-qr-pay tests: a Techcombank account whose code ends in 0283.
        val fields = listOf(
            EmvQr.field("00", "01"),
            EmvQr.field("01", "11"),
            EmvQr.field("38", "0010A000000727012800069704070114190304136010180208QRIBFTTA"),
            EmvQr.field("53", "704"),
            EmvQr.field("58", "VN"),
        )

        assertEquals(
            "00020101021138580010A000000727012800069704070114190304136010180208QRIBFTTA53037045802VN63040283",
            EmvQr.payload(fields),
        )
    }
}
