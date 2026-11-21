package com.dantech.splitvibe.e2e

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.splitvibe.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saves pictures of the home and of the split of the sample bill as this phone draws them, for a
 * person to look at the two fonts on each version of Android the journeys run on: the large titles
 * and the amounts in Bricolage Grotesque, the rest in Be Vietnam Pro, with the marks of Vietnamese
 * and the dong sign. A font that fails to load gives way to the system's without any error, so only
 * an eye on the pictures finds it. Each picture is named after the reference image of the same
 * screen, with the version of Android after it.
 *
 * The pictures go to the folder that the Android Gradle plugin gives a run of the device tests for
 * the files they write, and copies back to the computer once the run ends, under
 * `app/build/outputs/connected_android_test_additional_output/`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsOnDeviceTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theHomeAndTheSplitOfTheSampleBillAreSavedAsThisPhoneDrawsThem() {
        val folder = InstrumentationRegistry.getArguments().getString(OUTPUT_FOLDER_ARGUMENT)
        // Without the folder nothing would keep the pictures, so the test is skipped rather than passed.
        assumeTrue("The run gives no folder for the files tests write", folder != null)
        val pictures = File(checkNotNull(folder))

        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        save(pictures, "01-home")

        compose.readTheSampleBill()
        compose.onNodeWithText("Bill total").assertIsDisplayed()
        save(pictures, "05-result-top")
    }

    /** Saves what the screen shows in [folder], as [name] followed by this phone's version of Android. */
    private fun save(folder: File, name: String) {
        compose.waitForIdle()
        val picture = compose.onRoot().captureToImage().asAndroidBitmap()
        check(folder.isDirectory || folder.mkdirs()) { "The folder $folder cannot be made" }
        val file = File(folder, "$name-api-${Build.VERSION.SDK_INT}.png")
        FileOutputStream(file).use { out ->
            check(picture.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "The picture could not be written to $file" }
        }
    }

    private companion object {
        /** The argument in which the Android Gradle plugin names the folder it copies back after the run. */
        const val OUTPUT_FOLDER_ARGUMENT = "additionalTestOutputDir"

        /** PNG keeps every pixel and ignores the quality, which compressing still asks for. */
        const val PNG_QUALITY = 100
    }
}
