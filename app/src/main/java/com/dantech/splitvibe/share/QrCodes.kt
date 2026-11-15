package com.dantech.splitvibe.share

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Draws the QR code of a payment, for the payer's banking app to scan. */
object QrCodes {

    /** [payload] as a QR code [sizePx] pixels across, black on white, with the quiet zone around it that scanners need. */
    fun bitmap(payload: String, sizePx: Int = 720): Bitmap {
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES)
            // Payment codes are plain ASCII, which every scanner reads as it is. Only other text, such
            // as an accented name on a SEPA code, needs UTF-8 and the marker that says so.
            if (payload.any { it.code > 0x7E }) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val pixels = IntArray(width * matrix.height) { i -> if (matrix.get(i % width, i / width)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    private const val QUIET_ZONE_MODULES = 4
}
