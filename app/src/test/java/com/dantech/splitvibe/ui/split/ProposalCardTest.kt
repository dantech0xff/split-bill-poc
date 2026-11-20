package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where the changes the AI proposes show on the result screen: right below the bill's total and
 * above what each person pays, as the approved design has them, and in view as soon as they come in,
 * wherever in the list the person asked for them; at once when animations are off.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE_WITH_KEYBOARD)
class ProposalCardTest {

    /**
     * The stand-in for the proxy and the phone's storage, closed only once the screens that read them
     * are gone: the compose rule inside this one removes the screens first.
     */
    @get:Rule(order = 0)
    val outsideTheScreens = object : ExternalResource() {
        override fun after() {
            server.shutdown()
            storage.close()
        }
    }

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private lateinit var assistant: SplitAssistantViewModel

    private val recordedText = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() = server.start()

    @Test
    @Config(qualifiers = TALL)
    fun `the proposed changes stand right below the total, above what each person pays`() {
        showResult(reducedMotion = false)
        askForATip()

        // The top of the page, which shows all three.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val total = compose.onNodeWithText("Bill total").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("Proposed changes").fetchSemanticsNode().boundsInRoot
        val apply = compose.onNode(hasText("Apply") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val shares = compose.onNode(hasText("Each person pays") and isHeading()).fetchSemanticsNode().boundsInRoot
        assertTrue("The proposed changes are not below the total", title.top >= total.bottom)
        assertTrue("The proposed changes do not end above what each person pays", apply.bottom <= shares.top)
    }

    @Test
    fun `the proposed changes come into view as they come in, though the person asked for them further down`() {
        showResult(reducedMotion = false)
        askForATip(farBelowTheTotal = true)

        compose.onNodeWithText("Proposed changes").assertIsDisplayed()
    }

    @Test
    fun `with animations off the proposed changes come into view at once`() {
        showResult(reducedMotion = true)
        typeATip(farBelowTheTotal = true)

        // From here on time moves only when the test says so, frame by frame.
        compose.mainClock.autoAdvance = false
        server.enqueue(tipAnswer())
        compose.onNodeWithContentDescription("Ask the AI").performClick()
        awaitOnMain { assistant.state.value.let { it.proposal != null || it.error != null } }
        assertNull(assistant.state.value.error)
        repeat(FEW_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            // What a frame started, such as the scroll that brings the changes into view, runs on the main looper.
            shadowOf(Looper.getMainLooper()).idle()
        }

        val title = compose.onNodeWithText("Proposed changes").assertIsDisplayed()
        val shown = title.fetchSemanticsNode().positionInRoot
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertEquals("The list was still on its way to the proposed changes", shown, title.fetchSemanticsNode().positionInRoot)
    }

    /** Reads the sample bill as the AI did when it was recorded, on the result screen with its assistant. */
    private fun showResult(reducedMotion: Boolean) {
        server.enqueue(Fixtures.proxyAnswer())
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(
            context,
            storage,
            account = proxy.account.account,
            recognizer = recordedText,
            analyzer = proxy.analyzer,
            localCurrency = "VND",
        )
        assistant = SplitAssistantViewModel(
            host = viewModel,
            assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true, sellsPro = false),
            voice = VoiceInput(context, SettingsStore(context)),
            settings = MutableStateFlow(AppSettings(localCurrency = "VND", aiAllowed = true)),
            allowAi = {},
            account = proxy.account.account,
        )
        compose.setContent {
            SplitVibeTheme {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    SplitScreen(viewModel = viewModel, aiStatus = AiStatus.AVAILABLE, newCameraTarget = {
                        Uri.EMPTY
                    }, scanIntent = { null }, onExit = {}, onOpenSettings = {}, onOpenPro = {}, assistant = assistant)
                }
            }
        }
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)
    }

    /**
     * Types a change into the field below what each person pays, scrolled to it as the person would.
     * With [farBelowTheTotal] the total, where the proposed changes come, must be out of view by then.
     */
    private fun typeATip(farBelowTheTotal: Boolean = false) {
        val field = hasText("Change the split") and hasSetTextAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(field)
        compose.onNode(field).performTextInput("Add a 5% tip")
        if (farBelowTheTotal) {
            val totalInView = runCatching { compose.onNodeWithText("Bill total").assertIsDisplayed() }.isSuccess
            assertFalse("The total is still in view, so this proves nothing", totalInView)
        }
    }

    /** Asks for a tip in the field, and waits until the proposed changes came in. */
    private fun askForATip(farBelowTheTotal: Boolean = false) {
        typeATip(farBelowTheTotal)
        server.enqueue(tipAnswer())
        compose.onNodeWithContentDescription("Ask the AI").performClick()
        awaitState { assistant.state.value.let { it.proposal != null || it.error != null } }
        assertNull(assistant.state.value.error)
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

/** What the proxy answers to the request for a tip on the sample bill: one change, a tip of 5%. */
private fun tipAnswer(): MockResponse {
    val edits = """[{"op":"add_charge","kind":"tip","label":"Tip","percentBasisPoints":500}]"""
    val answer = """{"edits":$edits,"explanation":"A 5% tip is added.","question":null}"""
    val usage = """{"limit":3,"used":1.2,"remaining":1.8,"period":"trial","resetsAt":null}"""
    return MockResponse()
        .setHeader("content-type", "application/x-ndjson; charset=utf-8")
        .setBody("{\"type\":\"wait\"}\n{\"type\":\"result\",\"answer\":$answer,\"usage\":$usage}\n")
}

/** A phone as tall as what a list shows while the keyboard is open, so the field lies far below the total. */
private const val PHONE_WITH_KEYBOARD = "w412dp-h600dp-xhdpi"

/** Tall enough for the total, the proposed changes and what each person pays at once. */
private const val TALL = "w412dp-h2080dp-xhdpi"

/** Enough frames for the list to settle once, as it does at once with animations off. */
private const val FEW_FRAMES = 4

/** Long enough for any scroll of the list to have ended. */
private const val SETTLE_MS = 2_000L

private const val TIMEOUT_MS = 30_000L
