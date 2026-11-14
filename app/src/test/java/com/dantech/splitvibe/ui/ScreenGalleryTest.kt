package com.dantech.splitvibe.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.llm.BillPrompt
import com.dantech.splitvibe.llm.ClaudeBillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws every screen of the flow and compares it with the reference image in `src/test/screenshots`.
 *
 * The screens are the real ones, rendered by the Android framework on the build machine, so a
 * visual change shows up as a failing test instead of going unnoticed. The data is the same as in
 * [com.dantech.splitvibe.ui.split.SplitFlowTest]: the recorded ML Kit output and Claude answer for
 * the bundled sample bill.
 *
 * After an intended visual change, record new reference images and review them like code:
 *
 *     ./gradlew :app:testProdDebugUnitTest --tests '*ScreenGalleryTest*' -Pscreenshots=record
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class ScreenGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val settings = AppSettings(apiKey = "test-key", model = ClaudeBillAnalyzer.DEFAULT_MODEL, localCurrency = "VND")

    private enum class Screen { HOME, SPLIT, SPLIT_WITHOUT_KEY, SETTINGS }

    private var screen by mutableStateOf(Screen.HOME)

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `every screen of the split flow`() {
        // The answer is held back so the "working" state stays on screen long enough to be drawn.
        val viewModel = start(answerDelaySeconds = 4)

        shot("01-home")

        show(Screen.SETTINGS)
        shot("11-settings")

        show(Screen.SPLIT_WITHOUT_KEY)
        shot("12-new-split-without-key")

        show(Screen.SPLIT)
        shot("02-new-split")

        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        compose.onNodeWithText(BillPrompt.DEFAULT_INSTRUCTION).performTextReplacement(INSTRUCTION)
        shot("03-photo-and-instruction")

        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.progress == Progress.ASKING_CLAUDE }
        shot("04-working")

        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("05-result-top")

        // The lazy list only: the text shown under "what was sent" scrolls sideways and would match a plain scroll matcher.
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Adds up to the bill total"))
        shot("06-result-shares")

        list.performScrollToNode(hasText("Add people"))
        compose.onNodeWithText("Add people").performClick()
        shot("07-pick-people")
        compose.onNodeWithText("Done").performClick()

        // The list runs underneath the bottom bar, so bring the beers well clear of it before tapping the row.
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, HALF_PAGE_PX) }
        compose.onNodeWithText(BEERS).performClick()
        shot("08-item-sharers")
        compose.onNodeWithText("Done").performClick()

        list.performScrollToNode(hasText("Show what was sent to Claude"))
        compose.onNodeWithText("Show what was sent to Claude").performClick()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, PAGE_PX) }
        shot("09-sent-to-claude")

        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        shot("10-requests-sent")
    }

    @Test
    @Config(sdk = [34], qualifiers = TALL)
    fun `whole result page`() {
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText(BillPrompt.DEFAULT_INSTRUCTION).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("13-result-whole-page")
    }

    private fun start(answerDelaySeconds: Long): SplitBillViewModel {
        server.enqueue(recordedClaudeAnswer().setHeadersDelay(answerDelaySeconds, TimeUnit.SECONDS))
        val analyzer = ClaudeBillAnalyzer("test-key", baseUrl = server.url("/").toString())
        val viewModel = SplitBillViewModel(
            contactRepository = SeededContactRepository(),
            settings = MutableStateFlow(settings),
            imageLoader = BillImageLoader(context),
            recognizer = recordedMlKitOutput,
            analyzerFor = { analyzer },
        )
        compose.setContent {
            SplitVibeTheme {
                when (screen) {
                    Screen.HOME -> HomeScreen(contacts = viewModel.contacts, onSplitBill = {}, onOpenSettings = {})

                    Screen.SPLIT -> SplitScreen(viewModel = viewModel, hasApiKey = true, newCameraTarget = {
                        Uri.EMPTY
                    }, onExit = {}, onOpenSettings = {})

                    // What a fresh install shows until a key is saved in Settings.
                    Screen.SPLIT_WITHOUT_KEY -> SplitScreen(viewModel = viewModel, hasApiKey = false, newCameraTarget = {
                        Uri.EMPTY
                    }, onExit = {}, onOpenSettings = {})

                    // First use: no key saved yet, so the field shows its placeholder.
                    Screen.SETTINGS -> SettingsScreen(settings = settings.copy(apiKey = ""), onSave = { _, _, _ -> }, onBack = {})
                }
            }
        }
        return viewModel
    }

    private fun show(target: Screen) {
        screen = target
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File(SCREENSHOTS, "$name.png"))
    }

    /**
     * Waits for work that finishes on a background thread. Under Robolectric the test itself runs on
     * the main thread, so results posted back to it are only delivered when the main looper is run.
     */
    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    /** The Messages API response that carries the recorded answer as its text block. */
    private fun recordedClaudeAnswer(): MockResponse {
        val body = buildJsonObject {
            put("id", "msg_recorded")
            put("type", "message")
            put("role", "assistant")
            put("model", ClaudeBillAnalyzer.DEFAULT_MODEL)
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", Fixtures.text("sample_bill_answer.json"))
                        },
                    )
                },
            )
            put("stop_reason", "end_turn")
            putJsonObject("usage") {
                put("input_tokens", 2300)
                put("output_tokens", 800)
            }
        }
        return MockResponse().setHeader("content-type", "application/json").setBody(body.toString())
    }
}

/** Reference images, relative to the module directory the tests run in. */
private const val SCREENSHOTS = "src/test/screenshots"
private const val PHONE = "w412dp-h915dp-xhdpi"

/** Tall enough to show the whole result list at once, with a little room above the bottom bar. */
private const val TALL = "w412dp-h1472dp-xhdpi"
private const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
private const val BEERS = "Bia Saigon Special"

// Scroll distances at xhdpi (2 px per dp).
private const val PAGE_PX = 1150f
private const val HALF_PAGE_PX = 540f
private const val TIMEOUT_MS = 60_000L
