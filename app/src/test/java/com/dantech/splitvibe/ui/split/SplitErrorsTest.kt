package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.Plan
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.english
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.llm.MAX_INSTRUCTION_CHARS
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import com.dantech.splitvibe.text.UiText
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the first step of a split, and the assistant, tell the person when a call to the AI did not work, and where it leads. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SplitErrorsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)

    @After
    fun tearDown() = storage.close()

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Test
    fun `an error that signing in fixes leads to Settings, in a build that can sign in`() {
        val cases = listOf(
            Case(Kind.SIGN_IN_EXPIRED, signedIn = false, canSignIn = true, opensSettings = true),
            Case(Kind.QUOTA_EXHAUSTED, signedIn = false, canSignIn = true, opensSettings = true),
            // A signed-in person has used this month's reads: signing in again gives no more.
            Case(Kind.QUOTA_EXHAUSTED, signedIn = true, canSignIn = true, opensSettings = false),
            Case(Kind.QUOTA_EXHAUSTED, signedIn = false, canSignIn = false, opensSettings = false),
            Case(Kind.SIGN_IN_EXPIRED, signedIn = false, canSignIn = false, opensSettings = false),
            Case(Kind.NETWORK, signedIn = false, canSignIn = true, opensSettings = false),
        )

        for (case in cases) assertEquals(case.toString(), case.opensSettings to case.opensPro, errorActionsOf(case))
    }

    @Test
    fun `in a build that sells Pro, calls used up lead to the Pro screen, except for a Pro member, and a sign-in to renew to Settings`() {
        val cases = listOf(
            Case(Kind.QUOTA_EXHAUSTED, signedIn = false, canSignIn = true, sellsPro = true, plan = Plan.TRIAL, opensPro = true),
            Case(Kind.QUOTA_EXHAUSTED, signedIn = true, canSignIn = true, sellsPro = true, plan = Plan.FREE, opensPro = true),
            Case(Kind.QUOTA_EXHAUSTED, signedIn = true, canSignIn = true, sellsPro = true, plan = Plan.PRO, opensPro = false),
            Case(Kind.SIGN_IN_EXPIRED, signedIn = false, canSignIn = true, sellsPro = true, opensSettings = true),
            Case(Kind.NETWORK, signedIn = true, canSignIn = true, sellsPro = true),
        )

        for (case in cases) assertEquals(case.toString(), case.opensSettings to case.opensPro, errorActionsOf(case))
    }

    @Test
    fun `the way to Pro is the error's action, on the first step and in the assistant`() {
        var opened = 0
        var inAssistant by mutableStateOf(false)
        compose.setContent {
            SplitVibeTheme {
                if (inAssistant) {
                    AssistantCard(
                        state = AssistantUiState(error = USED_UP, errorOpensPro = true),
                        actions = assistantActions(onOpenPro = { opened++ }),
                    )
                } else {
                    NewSplitContent(
                        state = SplitUiState(error = USED_UP, errorOpensPro = true),
                        aiStatus = AiStatus.AVAILABLE,
                        newCameraTarget = { error("No photo is taken in this test") },
                        scanIntent = { null },
                        onBack = {},
                        onPagesPicked = {},
                        onSamplePicked = {},
                        onManualEntry = {},
                        onInstructionChanged = {},
                        onAnalyze = {},
                        onCancel = {},
                        onOpenSettings = { error("The way is to Pro") },
                        onOpenPro = { opened++ },
                    )
                }
            }
        }
        // The error sits below the photo and the instruction.
        compose.onNode(hasText("Get Pro") and hasClickAction()).performScrollTo().performClick()

        inAssistant = true
        compose.onNode(hasText("Get Pro") and hasClickAction()).performClick()

        assertEquals(2, opened)
    }

    /** Whether the error a bill read ends in for [case] leads to Settings and to the Pro screen. */
    private fun errorActionsOf(case: Case): Pair<Boolean, Boolean> {
        val viewModel = viewModel(case.signedIn, case.canSignIn, failing(case.kind, case.plan), sellsPro = case.sellsPro)
        viewModel.onSamplePicked()
        awaitOnMain { viewModel.state.value.let { it.photo != null && !it.isBusy } }

        viewModel.analyze()
        awaitOnMain { viewModel.state.value.let { it.error != null && !it.isBusy } }

        return viewModel.state.value.let { it.errorOpensSettings to it.errorOpensPro }
    }

    @Test
    fun `the instruction holds no more than the AI proxy takes`() {
        val viewModel = viewModel(signedIn = false, canSignIn = true, analyzer = failing(Kind.NETWORK))

        viewModel.onInstructionChanged("Split it. ".repeat(200))

        assertEquals(MAX_INSTRUCTION_CHARS, viewModel.state.value.instruction.length)
    }

    @Test
    fun `an error that only repeats the AI notice is not shown twice`() {
        val notice = checkNotNull(AiStatus.OFF.notice)
        compose.setContent {
            SplitVibeTheme {
                NewSplitContent(
                    state = SplitUiState(error = notice),
                    aiStatus = AiStatus.OFF,
                    newCameraTarget = { error("No photo is taken in this test") },
                    scanIntent = { null },
                    onBack = {},
                    onPagesPicked = {},
                    onSamplePicked = {},
                    onManualEntry = {},
                    onInstructionChanged = {},
                    onAnalyze = {},
                    onCancel = {},
                    onOpenSettings = {},
                    onOpenPro = {},
                )
            }
        }

        compose.onAllNodesWithText(notice.english()).assertCountEquals(1)
    }

    private companion object {
        val USED_UP = UiText.Raw("You have used this month's 10 calls. They come back on 1 December. Get Pro for 300 calls a month.")
    }

    private data class Case(
        val kind: Kind,
        val signedIn: Boolean,
        val canSignIn: Boolean,
        val sellsPro: Boolean = false,
        /** The plan the proxy counted the calls against, when it said. */
        val plan: Plan? = null,
        val opensSettings: Boolean = false,
        val opensPro: Boolean = false,
    )

    private fun assistantActions(onOpenPro: () -> Unit) = AssistantActions(
        onTextChange = {},
        onSend = {},
        onCancel = {},
        onToggleEdit = {},
        onApply = {},
        onDiscard = {},
        onUndo = {},
        onForgetQuestion = {},
        onDismissError = {},
        onOpenSettings = { error("The way is to Pro") },
        onOpenPro = onOpenPro,
        onMic = {},
        onNetworkVoice = {},
        onMicPermissionAsked = {},
        onMicPermission = {},
    )

    private fun viewModel(signedIn: Boolean, canSignIn: Boolean, analyzer: BillAnalyzer, sellsPro: Boolean = false) = testSplitViewModel(
        context,
        storage,
        account = MutableStateFlow(
            Account(
                installId = "split-errors-test",
                session = if (signedIn) Session("signed-session", expiresAt = Long.MAX_VALUE) else null,
                callsLeft = null,
                ai = AiStatus.AVAILABLE,
            ),
        ),
        canSignIn = canSignIn,
        sellsPro = sellsPro,
        recognizer = recordedMlKitOutput,
        analyzer = analyzer,
    )

    /** Fails each read as [kind], with the proxy's refusal behind it that counted the calls against [plan] when that is given. */
    private fun failing(kind: Kind, plan: Plan? = null) = object : BillAnalyzer {
        override suspend fun analyze(pages: List<OcrResult>, instruction: String, localCurrency: String): BillAnalysis {
            val refusal = plan?.let {
                val calls = CallsLeft(BigDecimal(10), BigDecimal(10), BigDecimal.ZERO, period = "2026-11", resetsAt = 0, plan = it)
                ProxyException(429, "quota_exhausted", retryAfterSeconds = null, callsLeft = calls)
            }
            throw BillAnalyzerException(kind, "The bill could not be read ($kind).", refusal)
        }
    }
}
