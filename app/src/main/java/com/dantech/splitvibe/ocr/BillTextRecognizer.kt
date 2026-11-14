package com.dantech.splitvibe.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Finds the text on a bill photo, with positions. */
interface BillTextRecognizer {
    /** @param image an upright photo (EXIF rotation already applied). */
    suspend fun recognize(image: Bitmap): OcrResult
}

/**
 * On-device recognition with ML Kit Text Recognition v2.
 *
 * Uses the bundled Latin-script model, so it works offline and the photo never leaves the phone.
 * Latin script covers English, Vietnamese and most European languages. Other scripts (Chinese,
 * Japanese, Korean, Devanagari) need the matching ML Kit artifact and options.
 */
class MlKitBillTextRecognizer : BillTextRecognizer {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(image: Bitmap): OcrResult {
        val text = recognizer.process(InputImage.fromBitmap(image, 0)).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { it.toOcrLine() }
        return OcrResult(image.width, image.height, lines)
    }

    private fun Text.Line.toOcrLine(): OcrLine? {
        val points = cornerPoints
        if (points != null && points.size == 4) {
            return OcrLine(text, points.map { OcrPoint(it.x.toFloat(), it.y.toFloat()) })
        }
        val box = boundingBox ?: return null
        return OcrLine.upright(text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
