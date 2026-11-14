package com.dantech.splitvibe.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
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
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.DEFAULT_INSTRUCTION
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
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
@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class ScreenGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val settings = AppSettings(localCurrency = "VND")

    private enum class Screen { HOME, SPLIT, SPLIT_WITHOUT_AI, SETTINGS, SETTINGS_SIGNED_IN }

    private var screen by mutableStateOf(Screen.HOME)

    /** Added to every file name of a walk, so the dark and large-text images sit beside the light ones. */
    private var suffix = ""

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `every screen of the split flow`() = everyScreen()

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `every screen of the split flow in the dark theme`() {
        suffix = "-dark"
        everyScreen()
    }

    @Test
    @Config(sdk = [34], qualifiers = TALL)
    fun `whole result page`() = wholeResultPage()

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `whole result page in the dark theme`() {
        suffix = "-dark"
        wholeResultPage()
    }

    /** Text at twice its size must still fit: nothing may be cut off on the four main screens. */
    @Test
    fun `the four main screens with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        suffix = "-large-text"
        val viewModel = start(answerDelaySeconds = 0)
        shot("01-home")

        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        shot("03-photo-and-instruction")

        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("05-result-top")

        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        shot("10-requests-sent")
    }

    private fun everyScreen() {
        // The answer is held back so the "working" state stays on screen long enough to be drawn.
        val viewModel = start(answerDelaySeconds = 4)

        shot("01-home")

        show(Screen.SETTINGS)
        shot("11-settings")

        show(Screen.SETTINGS_SIGNED_IN)
        shot("16-settings-signed-in")

        show(Screen.SPLIT_WITHOUT_AI)
        shot("12-new-split-without-ai")

        show(Screen.SPLIT)
        shot("02-new-split")

        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        compose.onNodeWithText(DEFAULT_INSTRUCTION).performTextReplacement(INSTRUCTION)
        shot("03-photo-and-instruction")

        // The working state shows an endless animation. Its clock is stopped and then moved on by a
        // fixed amount, so the picture catches the same moment of the animation on every run.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.progress == Progress.ASKING_CLAUDE }
        compose.mainClock.advanceTimeBy(WORKING_ANIMATION_MS)
        shot("04-working")
        compose.mainClock.autoAdvance = true

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

        // The request button turns into the mark that confirms the requests. The picture catches
        // that change halfway, with the clock stopped as for the working state.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(HALFWAY_TO_SENT_MS)
        shot("15-sending")
        compose.mainClock.autoAdvance = true
        shot("10-requests-sent")
    }

    private fun wholeResultPage() {
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText(DEFAULT_INSTRUCTION).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("13-result-whole-page")
    }

    private fun start(answerDelaySeconds: Long): SplitBillViewModel {
        server.enqueue(Fixtures.proxyAnswer().setHeadersDelay(answerDelaySeconds, TimeUnit.SECONDS))
        val proxy = TestProxy(server, context)
        val viewModel = SplitBillViewModel(
            contactRepository = SeededContactRepository(),
            settings = MutableStateFlow(settings),
            account = proxy.account.account,
            imageLoader = BillImageLoader(context),
            recognizer = recordedMlKitOutput,
            analyzer = proxy.analyzer,
        )
        compose.setContent {
            // No touch ripples: they are drawn by the platform on real time, so a picture taken
            // shortly after a tap would differ by a few pixels from run to run.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                SplitVibeTheme {
                    when (screen) {
                        Screen.HOME -> HomeScreen(contacts = viewModel.contacts, onSplitBill = {}, onOpenSettings = {})

                        Screen.SPLIT -> SplitScreen(viewModel = viewModel, aiStatus = AiStatus.AVAILABLE, newCameraTarget = {
                            Uri.EMPTY
                        }, onExit = {}, onOpenSettings = {})

                        // What a build without the AI proxy's address shows.
                        Screen.SPLIT_WITHOUT_AI -> SplitScreen(viewModel = viewModel, aiStatus = AiStatus.NOT_SET_UP, newCameraTarget = {
                            Uri.EMPTY
                        }, onExit = {}, onOpenSettings = {})

                        // Before signing in, with the count the proxy reported for this install.
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_OUT,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = {},
                            onBack = {},
                        )

                        Screen.SETTINGS_SIGNED_IN -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_IN,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = {},
                            onBack = {},
                        )
                    }
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
        captureScreenRoboImage(File(SCREENSHOTS, "$name$suffix.png"))
    }

    /**
     * Waits for work that finishes on a background thread. Under Robolectric the test itself runs on
     * the main thread, so results posted back to it are only delivered when the main looper is run.
     */
    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

/** Reference images, relative to the module directory the tests run in. */
private const val SCREENSHOTS = "src/test/screenshots"
private const val PHONE = "w412dp-h915dp-xhdpi"
private const val PHONE_AT_NIGHT = "w412dp-h915dp-night-xhdpi"

/** Tall enough to show the whole result list at once, with a little room above the bottom bar. */
private const val TALL = "w412dp-h1700dp-xhdpi"
private const val TALL_AT_NIGHT = "w412dp-h1700dp-night-xhdpi"
private const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
private const val BEERS = "Bia Saigon Special"

// Scroll distances at xhdpi (2 px per dp).
private const val PAGE_PX = 1150f
private const val HALF_PAGE_PX = 540f
private const val TIMEOUT_MS = 60_000L
private const val WORKING_ANIMATION_MS = 500L
private const val HALFWAY_TO_SENT_MS = 200L

private val SIGNED_OUT = Account(
    installId = "gallery-install",
    session = null,
    callsLeft = CallsLeft(limit = 3, used = 1, remaining = 2, period = "trial", resetsAt = null),
    ai = AiStatus.AVAILABLE,
)

private val SIGNED_IN = SIGNED_OUT.copy(
    session = Session("gallery-session", expiresAt = Long.MAX_VALUE),
    signedInAs = "minh.tran@example.com",
    // 1 December 2026, in UTC.
    callsLeft = CallsLeft(limit = 10, used = 1, remaining = 9, period = "2026-11", resetsAt = 1_796_083_200_000),
)

private val NO_ACTIONS = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {})
