package com.dantech.splitvibe

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.di.appModule
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalysisParser
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Splits the sample bill on a device, from the photo to the sent requests, through the consent to
 * the AI, and checks every screen on the way for accessibility. The text is read by the real recognizer on the device; the model's
 * answer is the one recorded for this bill, so the test needs neither the AI proxy nor the network.
 */
@RunWith(AndroidJUnit4::class)
class SplitFlowOnDeviceTest {

    /** Replaces the view model before the activity starts, and puts the app's own back afterwards. */
    @get:Rule(order = 0)
    val recordedAnswer = object : ExternalResource() {
        override fun before() = loadKoinModules(
            module {
                viewModel {
                    // Not agreed to the AI yet, so the consent screen is checked on the way too.
                    val settings = MutableStateFlow(AppSettings(localCurrency = "VND"))
                    SplitBillViewModel(
                        settings = settings,
                        allowAi = { allowed -> settings.update { it.copy(aiAllowed = allowed) } },
                        account = MutableStateFlow(
                            Account(installId = "on-device-test", session = null, callsLeft = null, ai = AiStatus.AVAILABLE),
                        ),
                        canSignIn = true,
                        sellsPro = false,
                        imageLoader = get(),
                        recognizer = get(),
                        analyzer = RecordedAnswer,
                        bills = get(),
                        peopleRepository = get(),
                        pageStore = get(),
                        fx = get(),
                        contactNames = get(),
                        savedState = get(),
                    )
                }
            },
        )

        // All device tests run in one process, so the tests that follow would otherwise get this view model too.
        override fun after() = loadKoinModules(appModule)
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** From here on every tap also checks the screen it lands on. */
    @Before
    fun checkAccessibilityOnEveryScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) compose.enableAccessibilityChecks()
    }

    @Test
    fun theSampleBillIsSplitAndTheRequestsAreSent() {
        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitNode(hasText("Read and split the bill") and isEnabled())
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitNode(hasText("Agree") and hasClickAction())
        compose.onNode(hasText("Agree") and hasClickAction()).performClick()

        val request = hasText("Request", substring = true) and hasClickAction()
        awaitNode(request)
        compose.onNode(request).performClick()
        compose.onNodeWithText("Ask for the money").assertIsDisplayed()

        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
    }

    /**
     * Types a person's part into their row of the split as a finger does, with the keyboard open: the
     * field being typed into stays in view above the bar at the bottom, and the bar above the keyboard.
     * The last row is the one nearest the bar. On a phone that shows no keyboard, as some emulators do
     * not, the test is skipped rather than passed, as it would prove nothing there.
     */
    @Test
    fun aPartTypedIntoTheLastRowStaysAboveTheBarAndTheKeyboard() {
        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitNode(hasText("Read and split the bill") and isEnabled())
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitNode(hasText("Agree") and hasClickAction())
        compose.onNode(hasText("Agree") and hasClickAction()).performClick()
        awaitNode(hasText("Bill total"))

        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("By percent"))
        // Through its action: the chip may lie partly beyond the edge of its row, which scrolls sideways.
        compose.onNodeWithText("By percent").performSemanticsAction(SemanticsActions.OnClick)
        val part = hasSetTextAction() and hasText("Percent for Lan")
        list.performScrollToNode(part)
        // A tap, as a finger gives it, puts the cursor in the field and opens the keyboard.
        compose.onNode(part).performClick()
        assumeTrue("The phone shows no keyboard", awaitKeyboard())
        compose.onNode(part).performTextReplacement("50")
        compose.waitForIdle()

        // Where the field and the bar are as a whole: bounds cut to the list would never reach below it.
        val field = compose.onNode(part).assertIsDisplayed().unclippedBounds()
        // Lan's 50% leaves the split over 100%, so its main action cannot be used yet.
        val bar = compose.onNodeWithText("Finish the split").onParent().unclippedBounds()
        assertTrue("The field ends at ${field.bottom}, under the top of the bar at ${bar.top}", field.bottom <= bar.top)
        val keyboard = keyboardTop()
        assertTrue("The bar ends at ${bar.bottom}, under the top of the keyboard at $keyboard", bar.bottom <= keyboard)
    }

    /** Waits for work that runs off the main thread: loading the photo, reading its text. */
    private fun awaitNode(matcher: SemanticsMatcher) = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    /** Waits for the keyboard to show; false when it does not, as on an emulator without one or a phone with keys of its own. */
    private fun awaitKeyboard(): Boolean = try {
        compose.waitUntil(KEYBOARD_TIMEOUT_MS) { keyboardShown() }
        true
    } catch (e: ComposeTimeoutException) {
        false
    }

    private fun keyboardShown(): Boolean {
        var shown = false
        compose.activityRule.scenario.onActivity { activity ->
            shown = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }

    /** Where this node lies on the screen as large as it is, not cut to what clips it, such as the list it scrolls in. */
    private fun SemanticsNodeInteraction.unclippedBounds(): DpRect {
        val node = fetchSemanticsNode()
        return with(compose.density) {
            DpRect(
                left = node.positionInRoot.x.toDp(),
                top = node.positionInRoot.y.toDp(),
                right = (node.positionInRoot.x + node.size.width).toDp(),
                bottom = (node.positionInRoot.y + node.size.height).toDp(),
            )
        }
    }

    /** Where the keyboard starts on the screen, or the bottom of the screen while it shows none, as an emulator may not. */
    private fun keyboardTop(): Dp {
        var keyboard = 0
        compose.activityRule.scenario.onActivity { activity ->
            keyboard = ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        val screen = compose.onRoot().getBoundsInRoot()
        return screen.bottom - with(compose.density) { keyboard.toDp() }
    }
}

/** Answers with what the model said about the sample bill when it was recorded. */
private object RecordedAnswer : BillAnalyzer {
    private val answer = checkNotNull(javaClass.classLoader?.getResource("fixtures/sample_bill_answer.json")) {
        "Missing the recorded answer in src/sharedTest/resources/fixtures"
    }.readText()

    override suspend fun analyze(pages: List<OcrResult>, instruction: String, localCurrency: String): BillAnalysis =
        BillAnalysisParser.parse(answer, localCurrency)
}

private const val TIMEOUT_MS = 60_000L

/** Long enough for a keyboard to slide in, short enough not to hold up a run on a phone without one. */
private const val KEYBOARD_TIMEOUT_MS = 5_000L
