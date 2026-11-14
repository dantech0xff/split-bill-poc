package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.BillAnalyzerException.Kind
import com.dantech.splitvibe.llm.MAX_INSTRUCTION_CHARS
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the first step of a split tells the person when a bill cannot be read. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SplitErrorsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

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

        for (case in cases) {
            val viewModel = viewModel(case.signedIn, case.canSignIn, failing(case.kind))
            viewModel.onSamplePicked()
            awaitOnMain { viewModel.state.value.let { it.photo != null && !it.isBusy } }

            viewModel.analyze()
            awaitOnMain { viewModel.state.value.let { it.error != null && !it.isBusy } }

            assertEquals(case.toString(), case.opensSettings, viewModel.state.value.errorOpensSettings)
        }
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
                    onBack = {},
                    onPhotoPicked = {},
                    onSamplePicked = {},
                    onInstructionChanged = {},
                    onAnalyze = {},
                    onCancel = {},
                    onOpenSettings = {},
                )
            }
        }

        compose.onAllNodesWithText(notice).assertCountEquals(1)
    }

    private data class Case(val kind: Kind, val signedIn: Boolean, val canSignIn: Boolean, val opensSettings: Boolean)

    private fun viewModel(signedIn: Boolean, canSignIn: Boolean, analyzer: BillAnalyzer) = SplitBillViewModel(
        contactRepository = SeededContactRepository(),
        settings = MutableStateFlow(AppSettings(localCurrency = "VND")),
        account = MutableStateFlow(
            Account(
                installId = "split-errors-test",
                session = if (signedIn) Session("signed-session", expiresAt = Long.MAX_VALUE) else null,
                callsLeft = null,
                ai = AiStatus.AVAILABLE,
            ),
        ),
        canSignIn = canSignIn,
        imageLoader = BillImageLoader(context),
        recognizer = recordedMlKitOutput,
        analyzer = analyzer,
    )

    private fun failing(kind: Kind) = object : BillAnalyzer {
        override suspend fun analyze(ocr: OcrResult, instruction: String, localCurrency: String): BillAnalysis =
            throw BillAnalyzerException(kind, "The bill could not be read ($kind).")
    }
}
