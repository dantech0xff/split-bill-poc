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
import org.junit.BeforeClass
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
 * The home picture shows whatever bills the journeys that ran before it kept, so it is the first
 * home of its reference image only when no journey kept a bill.
 *
 * The pictures go to the folder that the Android Gradle plugin gives a run of the device tests for
 * the files they write, and copies back to the computer once the run ends, under
 * `app/build/outputs/connected_android_test_additional_output/`. Only the api-levels job keeps that
 * folder, so the test runs only when a run asks for the pictures with the runner argument
 * [ASKED_FOR_ARGUMENT], as that job does, and is skipped before the app starts in every other run,
 * such as the one a release waits for. Asked for, a run that gives no folder fails the test, so that
 * a lost picture shows in the job's conclusion.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsOnDeviceTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theHomeAndTheSplitOfTheSampleBillAreSavedAsThisPhoneDrawsThem() {
        val folder = checkNotNull(InstrumentationRegistry.getArguments().getString(OUTPUT_FOLDER_ARGUMENT)) {
            "The run asks for the pictures but gives no folder to keep them in: the runner argument $OUTPUT_FOLDER_ARGUMENT, " +
                "the folder that the Android Gradle plugin copies back after the run, is missing"
        }
        val pictures = File(folder)

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

    companion object {
        /** The runner argument, set to true, with which a run asks for the pictures; the api-levels job sets it. */
        const val ASKED_FOR_ARGUMENT = "savePictures"

        /** The argument in which the Android Gradle plugin names the folder it copies back after the run. */
        const val OUTPUT_FOLDER_ARGUMENT = "additionalTestOutputDir"

        /** Skips the test before its rules start anything, unless the run asks for the pictures. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenThePicturesAreAskedFor() {
            val asked = InstrumentationRegistry.getArguments().getString(ASKED_FOR_ARGUMENT) == "true"
            assumeTrue("The run does not ask for the pictures with $ASKED_FOR_ARGUMENT=true", asked)
        }

        /** PNG keeps every pixel and ignores the quality, which compressing still asks for. */
        const val PNG_QUALITY = 100
    }
}
