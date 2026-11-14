package com.dantech.splitvibe.ocr

data class OcrPoint(val x: Float, val y: Float)

/**
 * One line of text found on the bill.
 *
 * [corners] are the four corners of the text in image pixels, clockwise starting at the text's own
 * top-left. Keeping corners (not just an upright box) lets the layout step straighten a bill that
 * was photographed at an angle.
 */
data class OcrLine(val text: String, val corners: List<OcrPoint>) {
    init {
        require(corners.size == 4) { "A line needs exactly 4 corners, got ${corners.size}" }
    }

    companion object {
        /** A line whose text runs horizontally inside an upright box. */
        fun upright(text: String, left: Float, top: Float, right: Float, bottom: Float) = OcrLine(
            text,
            listOf(OcrPoint(left, top), OcrPoint(right, top), OcrPoint(right, bottom), OcrPoint(left, bottom)),
        )
    }
}

/** Everything the on-device recognizer found in one bill photo. */
data class OcrResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>,
) {
    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }
}
