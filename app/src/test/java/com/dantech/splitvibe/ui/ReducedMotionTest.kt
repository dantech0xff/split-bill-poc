package com.dantech.splitvibe.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.ClaudeBillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Some people switch animations off in the system settings because movement makes them unwell.
 * Then nothing may keep moving on its own, and the app must work as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ReducedMotionTest {

    /** The speed of Compose's own animations in the test. The test runs Compose on a clock of its own, which does not read the system setting. */
    private var animationScale = 1f

    @get:Rule
    val compose = createComposeRule(
        effectContext = object : MotionDurationScale {
            override val scaleFactor: Float get() = animationScale
        },
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val settings = AppSettings(apiKey = "test-key", model = ClaudeBillAnalyzer.DEFAULT_MODEL, localCurrency = "VND")

    private val recordedText = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }
    private val neverFinishes = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = awaitCancellation()
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `the working state stands still when animations are off`() {
        switchAnimationsOff()
        showWorkingState()

        assertTrue("The working state changed on its own", frame().sameAs(frameAfter(STEP_MS)))
    }

    /** The counterpart of the test above: it shows that a picture taken later can tell movement at all. */
    @Test
    fun `the working state moves when animations are on`() {
        showWorkingState()

        assertFalse("Nothing moved, so the other test proves nothing", frame().sameAs(frameAfter(STEP_MS)))
    }

    @Test
    fun `a bill can be split from start to finish when animations are off`() {
        switchAnimationsOff()
        server.enqueue(Fixtures.claudeAnswer())
        val analyzer = ClaudeBillAnalyzer("test-key", baseUrl = server.url("/").toString())
        val viewModel = start(recordedText, analyzerFor = { analyzer })

        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)

        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        compose.onNodeWithText("Requests sent").assertExists()
    }

    private fun showWorkingState() {
        val viewModel = start(neverFinishes, analyzerFor = { error("This test never gets as far as asking the model") })
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { it.photo != null && !it.isBusy } }
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.progress == Progress.READING_TEXT }
        // From here on time only moves when the test says so. The first step lets the bar settle.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(STEP_MS)
    }

    private fun start(recognizer: BillTextRecognizer, analyzerFor: (AppSettings) -> BillAnalyzer): SplitBillViewModel {
        val viewModel = SplitBillViewModel(
            contactRepository = SeededContactRepository(),
            settings = MutableStateFlow(settings),
            imageLoader = BillImageLoader(context),
            recognizer = recognizer,
            analyzerFor = analyzerFor,
        )
        compose.setContent {
            // No touch ripples: the platform draws them on real time, which would look like movement.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                SplitVibeTheme {
                    SplitScreen(viewModel = viewModel, hasApiKey = true, newCameraTarget = { Uri.EMPTY }, onExit = {}, onOpenSettings = {})
                }
            }
        }
        return viewModel
    }

    /** Switches animations off the way the system setting does: for the app's own effects and for Compose's animations. */
    private fun switchAnimationsOff() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        animationScale = 0f
    }

    private fun frame(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun frameAfter(millis: Long): Bitmap {
        compose.mainClock.advanceTimeBy(millis)
        return frame()
    }

    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

private const val STEP_MS = 600L
private const val TIMEOUT_MS = 60_000L
