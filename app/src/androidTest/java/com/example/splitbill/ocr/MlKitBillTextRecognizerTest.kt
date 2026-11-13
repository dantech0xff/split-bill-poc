package com.example.splitbill.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real ML Kit recognizer on the bundled sample bill. */
@RunWith(AndroidJUnit4::class)
class MlKitBillTextRecognizerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sampleBillIsReadWithItsAmountsOnTheRightRows() = runBlocking {
        val photo = BillImageLoader(context).loadSample()

        val ocr = MlKitBillTextRecognizer().recognize(photo)
        val rows = OcrLayoutEncoder.rows(ocr)
        val document = OcrLayoutEncoder.encode(ocr)

        assertEquals(photo.width, ocr.imageWidth)
        for (expected in listOf("902.664", "796.000", "39.800", "66.864", "165.000", "132.000", "Bia Saigon Special")) {
            assertTrue("\"$expected\" was not recognized in:\n$document", document.contains(expected))
        }

        // The sample is photographed at a slight angle: the label on the left and the amount on the
        // right must still end up on the same row.
        val beerRow = rows.single { row -> row.fragments.any { it.text.contains("Bia Saigon") } }
        assertTrue("beer row lost its amount:\n$document", beerRow.fragments.any { it.text.contains("132.000") })
        val totalRow = rows.single { row -> row.fragments.any { it.text.contains("902.664") } }
        assertTrue("total row lost its label:\n$document", totalRow.fragments.size >= 2)
    }
}
