package com.dantech.splitvibe.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.R
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.TestVoice
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.home.HomeScreen
import com.dantech.splitvibe.ui.settings.AccountActions
import com.dantech.splitvibe.ui.settings.AccountUiState
import com.dantech.splitvibe.ui.settings.SettingsScreen
import com.dantech.splitvibe.ui.split.Progress
import com.dantech.splitvibe.ui.split.SplitAssistantViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitScreen
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private val settings = AppSettings(localCurrency = "VND")

    private enum class Screen {
        HOME,
        SPLIT,
        SPLIT_WITHOUT_AI,
        SETTINGS,
        SETTINGS_SIGNED_IN,
        SETTINGS_SIGN_IN_EXPIRED,
        SETTINGS_SPEECH_SERVICE,
        HISTORY,
    }

    private var screen by mutableStateOf(Screen.HOME)

    /** Added to every file name of a walk, so the dark and large-text images sit beside the light ones. */
    private var suffix = ""

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    /** The assistant of the result screen, asking the same stand-in for the proxy as the bill read. */
    private lateinit var assistant: SplitAssistantViewModel

    @Before
    fun setUp() {
        server.start()
        // A phone that turns speech into text by itself, as most new phones do, so the result screen offers the mic.
        TestVoice.recognizeOnDevice()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

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

    /** Changing the split by a sentence: the field on the result screen, then what the AI proposes, before and after. */
    @Test
    fun `the assistant on the result screen`() = assistantScreens()

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `the assistant on the result screen in the dark theme`() {
        suffix = "-dark"
        assistantScreens()
    }

    /** Settings once the user let the phone's speech service hear them, which they can take back there. */
    @Test
    fun `settings once the phone's speech service was allowed`() = speechServiceSettings()

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `settings once the phone's speech service was allowed, in the dark theme`() {
        suffix = "-dark"
        speechServiceSettings()
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

        show(Screen.SETTINGS_SIGN_IN_EXPIRED)
        shot("17-settings-sign-in-expired")

        show(Screen.SPLIT_WITHOUT_AI)
        shot("12-new-split-without-ai")

        show(Screen.SPLIT)
        shot("02-new-split")

        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        compose.onNodeWithText(context.getString(R.string.split_default_instruction)).performTextReplacement(INSTRUCTION)
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
        // The shares and their total clear of the bottom bar.
        val total = compose.onNodeWithText("Adds up to the bill total").fetchSemanticsNode().boundsInRoot.bottom
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, total - SHARES_TOTAL_BOTTOM_PX) }
        shot("06-result-shares")

        // Someone split with before is suggested as their name is typed.
        list.performScrollToNode(hasText("Add someone"))
        compose.onNodeWithText("Add someone").performClick()
        compose.onNode(hasText("Name") and hasSetTextAction()).performTextInput("Bao")
        shot("07-add-person")
        compose.onNode(hasText("Add") and hasClickAction() and hasAnyAncestor(hasText("Bao Tran"))).performClick()
        awaitState { viewModel.state.value.result?.plan?.participants?.size == 4 }

        // Bring the beers into view with a few rows above them, as the picture shows them, then tap the row.
        list.performScrollToNode(hasText(BEERS))
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -HALF_PAGE_PX / 2) }
        compose.onNodeWithText(BEERS).performClick()
        shot("08-item-split")
        compose.onNodeWithText("Done").performClick()

        list.performScrollToNode(hasText("Show what was sent to Claude"))
        compose.onNodeWithText("Show what was sent to Claude").performClick()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, PAGE_PX) }
        shot("09-sent-to-claude")
        compose.onNodeWithText("Hide what was sent to Claude").performClick()

        // Divided by percent, each person's part can be typed in.
        list.performScrollToNode(hasText("By percent"))
        compose.onNodeWithText("By percent").performClick()
        list.performScrollToNode(hasText("100% of 100%"))
        shot("18-split-by-percent")

        // Opened through its click action, which does not depend on where the row lies on the screen.
        list.performScrollToNode(hasText("You paid", substring = true))
        compose.onNode(hasText("You paid", substring = true) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Several people").performClick()
        shot("19-who-paid")
        // Back to me paying it all, so the requests can go out.
        compose.onNodeWithText("One person").performClick()
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithContentDescription("Edit bill").performClick()
        shot("20-edit-bill")
        compose.onNode(hasText("Done") and hasClickAction()).performClick()
        // Back on the split before the clock is stopped below.
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).assertExists()

        // The request button turns into the mark that confirms the requests. The picture catches
        // that change halfway, with the clock stopped as for the working state.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasText("Request", substring = true) and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(HALFWAY_TO_SENT_MS)
        shot("15-sending")
        compose.mainClock.autoAdvance = true
        shot("10-requests-sent")
        // The list of the requests ends above the bottom bar, so none of its rows passes under the bar.
        // Padding at the end of the list alone would only lift its last row above the bar.
        val bar = compose.onNodeWithText("Done").onParent().getBoundsInRoot()
        val requests = compose.onNode(hasScrollToIndexAction()).getBoundsInRoot()
        assertTrue("The requests run on to ${requests.bottom}, under the top of the bottom bar at ${bar.top}", requests.bottom <= bar.top)

        // The finished split is the first bill in the list.
        awaitState { runBlocking { storage.bills.get(checkNotNull(viewModel.state.value.result).saved.id) }?.status == BillStatus.FINAL }
        show(Screen.HISTORY)
        shot("21-history")
    }

    private fun assistantScreens() {
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText(context.getString(R.string.split_default_instruction)).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)

        compose.onNode(hasText("Change the split") and hasSetTextAction()).performTextInput(ASSISTANT_INSTRUCTION)
        shot("30-assistant")

        server.enqueue(revisionAnswer())
        compose.onNodeWithContentDescription("Ask Claude").performClick()
        awaitState { assistant.state.value.let { it.proposal != null || it.error != null } }
        assertNull(assistant.state.value.error)
        // The user keeps two of the three changes.
        val paid = hasContentDescription("Minh paid the whole bill") and isToggleable()
        compose.onNode(paid).performSemanticsAction(SemanticsActions.OnClick)
        // The proposed changes up under the top bar, so they show whole above the bottom bar.
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Proposed changes"))
        val title = compose.onNodeWithText("Proposed changes").fetchSemanticsNode().boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, title - PROPOSAL_TOP_PX) }
        shot("31-assistant-proposal")
    }

    private fun speechServiceSettings() {
        start(answerDelaySeconds = 0)
        show(Screen.SETTINGS_SPEECH_SERVICE)
        shot("32-settings-speech-service")
    }

    private fun wholeResultPage() {
        val viewModel = start(answerDelaySeconds = 0)
        show(Screen.SPLIT)
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText(context.getString(R.string.split_default_instruction)).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
        shot("13-result-whole-page")
    }

    private fun start(answerDelaySeconds: Long): SplitBillViewModel {
        // People split with before, whose names are suggested again.
        storage.rememberTestPeople()
        server.enqueue(Fixtures.proxyAnswer().setHeadersDelay(answerDelaySeconds, TimeUnit.SECONDS))
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(
            context,
            storage,
            account = proxy.account.account,
            recognizer = recordedMlKitOutput,
            analyzer = proxy.analyzer,
            localCurrency = settings.localCurrency,
        )
        assistant = SplitAssistantViewModel(
            host = viewModel,
            assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true, sellsPro = false),
            voice = VoiceInput(context, SettingsStore(context)),
            // The person agreed to the AI before; the consent screen has its own gallery.
            settings = MutableStateFlow(settings.copy(aiAllowed = true)),
            allowAi = {},
            account = proxy.account.account,
        )
        compose.setContent {
            val bills by storage.bills.listings().collectAsState(emptyList())
            // No touch ripples: they are drawn by the platform on real time, so a picture taken
            // shortly after a tap would differ by a few pixels from run to run.
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                SplitVibeTheme {
                    when (screen) {
                        Screen.HOME -> HomeScreen(bills = bills, onSplitBill = {}, onOpenBill = {}, onSeeAllBills = {}, onOpenSettings = {})

                        Screen.HISTORY -> HistoryScreen(listings = bills, query = "", onQueryChanged = {
                        }, onOpen = {}, onDelete = {}, onBack = {})

                        Screen.SPLIT -> SplitScreen(viewModel = viewModel, aiStatus = AiStatus.AVAILABLE, newCameraTarget = {
                            Uri.EMPTY
                        }, scanIntent = { null }, onExit = {}, onOpenSettings = {}, onOpenPro = {}, assistant = assistant)

                        // What a build without the AI proxy's address shows.
                        Screen.SPLIT_WITHOUT_AI -> SplitScreen(viewModel = viewModel, aiStatus = AiStatus.NOT_SET_UP, newCameraTarget = {
                            Uri.EMPTY
                        }, scanIntent = { null }, onExit = {}, onOpenSettings = {}, onOpenPro = {}, assistant = assistant)

                        // Before signing in, with the count the proxy reported for this install.
                        Screen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_OUT,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                        )

                        Screen.SETTINGS_SIGNED_IN -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_IN,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                        )

                        // The user let the phone's speech service hear them when they first said a change.
                        Screen.SETTINGS_SPEECH_SERVICE -> SettingsScreen(
                            settings = settings.copy(speechServiceAllowed = true),
                            account = SIGNED_OUT,
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
                            onBack = {},
                            onAllowSpeechService = {},
                        )

                        // The session ran out: sign in again, or sign out to use this phone's free calls.
                        Screen.SETTINGS_SIGN_IN_EXPIRED -> SettingsScreen(
                            settings = settings,
                            account = SIGNED_IN.copy(session = null, callsLeft = null, signInExpired = true),
                            accountUi = AccountUiState(),
                            canSignIn = true,
                            actions = NO_ACTIONS,
                            onSave = { _, _ -> },
                            onOpenPaymentMethods = {},
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
private const val TALL = "w412dp-h1900dp-xhdpi"
private const val TALL_AT_NIGHT = "w412dp-h1900dp-night-xhdpi"
private const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
private const val ASSISTANT_INSTRUCTION = "Lan didn't have any water. Add a 5% tip, and Minh paid"
private const val BEERS = "Bia Saigon Special"

// Scroll distances at xhdpi (2 px per dp).
private const val PAGE_PX = 1150f
private const val HALF_PAGE_PX = 540f

/** Where the title of the proposed changes ends up: below the top bar, with the top of its card in view. */
private const val PROPOSAL_TOP_PX = 220f

/** Where the line under the shares ends up: well above the bottom bar. */
private const val SHARES_TOTAL_BOTTOM_PX = 1500f
private const val TIMEOUT_MS = 60_000L
private const val WORKING_ANIMATION_MS = 500L
private const val HALFWAY_TO_SENT_MS = 200L

private val SIGNED_OUT = Account(
    installId = "gallery-install",
    session = null,
    callsLeft = CallsLeft(BigDecimal(3), used = BigDecimal("1.2"), remaining = BigDecimal("1.8"), period = "trial", resetsAt = null),
    ai = AiStatus.AVAILABLE,
)

private val SIGNED_IN = SIGNED_OUT.copy(
    session = Session("gallery-session", expiresAt = Long.MAX_VALUE),
    signedInAs = "minh.tran@example.com",
    // 1 December 2026, in UTC.
    callsLeft = CallsLeft(BigDecimal(10), BigDecimal(1), BigDecimal(9), period = "2026-11", resetsAt = 1_796_083_200_000),
)

private val NO_ACTIONS = AccountActions(onSignIn = {}, onSignOut = {}, onDeleteAccount = {}, onOpenPro = {})

/** What the proxy answers to [ASSISTANT_INSTRUCTION] on the sample bill: the water, a tip and who paid, as three edits. */
private fun revisionAnswer(): MockResponse {
    val edits = """[{"op":"item_sharers","itemId":"i7","participantIds":["me","p1"]},""" +
        """{"op":"add_charge","kind":"tip","label":"Tip","percentBasisPoints":500},{"op":"set_payer","participantId":"p1"}]"""
    val answer = """{"edits":$edits,"explanation":"Lan no longer shares the water, a 5% tip is added, and Minh paid.","question":null}"""
    val usage = """{"limit":3,"used":1.2,"remaining":1.8,"period":"trial","resetsAt":null}"""
    return MockResponse()
        .setHeader("content-type", "application/x-ndjson; charset=utf-8")
        .setBody("{\"type\":\"wait\"}\n{\"type\":\"result\",\"answer\":$answer,\"usage\":$usage}\n")
}
