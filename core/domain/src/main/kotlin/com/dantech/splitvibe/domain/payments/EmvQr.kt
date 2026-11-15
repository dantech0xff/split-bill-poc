package com.dantech.splitvibe.domain.payments

/**
 * The merchant-presented QR codes of EMVCo, which PromptPay and VietQR both use: a run of data
 * objects, each a two-digit id, a two-digit length and the value, closed by a CRC that covers
 * everything before it. The format indicator comes first and the CRC last; the objects in between
 * may come in any order, and each payment network has the order its own apps write.
 */
object EmvQr {

    private const val CRC_FIELD = "6304"

    /** One data object. Its value is plain ASCII, one to 99 characters, and may hold data objects of its own. */
    fun field(id: String, value: String): String {
        require(id.length == 2 && id.all(Char::isDigit)) { "an id is two digits" }
        require(value.length in 1..99) { "a value is 1 to 99 characters" }
        require(value.all { it in ' '..'~' }) { "a value is printable ASCII" }
        return id + value.length.toString().padStart(2, '0') + value
    }

    /** [fields] in their order, then the CRC field. */
    fun payload(fields: List<String>): String {
        val body = fields.joinToString("") + CRC_FIELD
        return body + crc16(body).toString(16).uppercase().padStart(4, '0')
    }

    /** CRC-16/CCITT-FALSE of the bytes of [text]: polynomial 0x1021, starting from 0xFFFF, nothing reflected. */
    fun crc16(text: String): Int {
        var crc = 0xFFFF
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }
}
