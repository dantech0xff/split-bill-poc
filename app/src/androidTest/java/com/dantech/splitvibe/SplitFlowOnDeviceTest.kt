package com.dantech.splitvibe

import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.di.appModule
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalysisParser
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.ClaudeBillAnalyzer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Splits the sample bill on a device, from the photo to the sent requests, and checks every screen
 * on the way for accessibility. The text is read by the real recognizer on the device; the model's
 * answer is the one recorded for this bill, so the test needs neither a key nor the network.
 */
@RunWith(AndroidJUnit4::class)
class SplitFlowOnDeviceTest {

    /** Replaces the view model before the activity starts, and puts the app's own back afterwards. */
    @get:Rule(order = 0)
    val recordedAnswer = object : ExternalResource() {
        override fun before() = loadKoinModules(
            module {
                viewModel {
                    SplitBillViewModel(
                        contactRepository = get(),
                        settings = MutableStateFlow(AppSettings("test-key", ClaudeBillAnalyzer.DEFAULT_MODEL, "VND")),
                        imageLoader = get(),
                        recognizer = get(),
                        analyzerFor = { RecordedAnswer },
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
        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitNode(hasText("Read and split the bill") and isEnabled())
        compose.onNodeWithText("Read and split the bill").performClick()

        val request = hasText("Request", substring = true) and hasClickAction()
        awaitNode(request)
        compose.onNode(request).performClick()
        compose.onNodeWithText("Requests sent").assertIsDisplayed()

        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Contacts").assertIsDisplayed()
    }

    /** Waits for work that runs off the main thread: loading the photo, reading its text. */
    private fun awaitNode(matcher: SemanticsMatcher) = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Answers with what the model said about the sample bill when it was recorded. */
private object RecordedAnswer : BillAnalyzer {
    private val answer = checkNotNull(javaClass.classLoader?.getResource("fixtures/sample_bill_answer.json")) {
        "Missing the recorded answer in src/sharedTest/resources/fixtures"
    }.readText()

    override suspend fun analyze(ocr: OcrResult, instruction: String, localCurrency: String): BillAnalysis =
        BillAnalysisParser.parse(answer, localCurrency)
}

private const val TIMEOUT_MS = 60_000L
