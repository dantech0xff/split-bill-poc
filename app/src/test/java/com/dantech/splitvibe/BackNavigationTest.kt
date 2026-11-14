package com.dantech.splitvibe

import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the back button and the back swipe do in the real activity. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class BackNavigationTest {

    private lateinit var splitViewModel: SplitBillViewModel

    private val neverFinishes = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = awaitCancellation()
    }

    /**
     * Replaces the view model before the activity starts. With a recognizer that never finishes,
     * the test can press back while the bill is being read.
     */
    @get:Rule(order = 0)
    val stalledBillReading = object : ExternalResource() {
        override fun before() = loadKoinModules(
            module {
                viewModel {
                    SplitBillViewModel(
                        contactRepository = get(),
                        settings = MutableStateFlow(AppSettings(localCurrency = "VND")),
                        account = get<AccountStore>().account,
                        canSignIn = true,
                        imageLoader = get(),
                        recognizer = neverFinishes,
                        analyzer = NeverAsked,
                    ).also { splitViewModel = it }
                }
            },
        )
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `a back swipe previews the previous screen and can be cancelled`() {
        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        swipeBackHalfway()
        // While the finger is down both screens are drawn: the one being left and the one behind it.
        compose.onNodeWithText("New split").assertExists()
        compose.onNodeWithText("Contacts").assertExists()
        captureScreenRoboImage(File("src/test/screenshots/14-back-swipe-preview.png"))

        onBackDispatcher { dispatchOnBackCancelled() }
        compose.onNodeWithText("New split").assertIsDisplayed()
        compose.onNodeWithText("Contacts").assertDoesNotExist()

        swipeBackHalfway()
        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Contacts").assertIsDisplayed()
        compose.onNodeWithText("New split").assertDoesNotExist()
    }

    @Test
    fun `back stops running work before it leaves the split screen`() {
        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { splitViewModel.state.value.let { it.photo != null && !it.isBusy } }
        compose.runOnUiThread { splitViewModel.analyze() }
        awaitState { splitViewModel.state.value.progress == Progress.READING_TEXT }

        onBackDispatcher { onBackPressed() }
        assertFalse(splitViewModel.state.value.isBusy)
        compose.onNodeWithText("New split").assertIsDisplayed()

        onBackDispatcher { onBackPressed() }
        compose.onNodeWithText("Contacts").assertIsDisplayed()
    }

    private fun swipeBackHalfway() = onBackDispatcher {
        dispatchOnBackStarted(BackEventCompat(touchX = 0f, touchY = 900f, progress = 0f, swipeEdge = BackEventCompat.EDGE_LEFT))
        dispatchOnBackProgressed(BackEventCompat(touchX = 300f, touchY = 900f, progress = 0.5f, swipeEdge = BackEventCompat.EDGE_LEFT))
    }

    private fun onBackDispatcher(action: OnBackPressedDispatcher.() -> Unit) {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.action() }
        compose.waitForIdle()
    }

    /** Waits for work that finishes on a background thread and reports back on the main looper. */
    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

private const val TIMEOUT_MS = 60_000L
