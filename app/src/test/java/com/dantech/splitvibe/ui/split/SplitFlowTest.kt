package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.llm.BillPrompt
import com.dantech.splitvibe.llm.ClaudeBillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Walks through the whole feature on the real screens, without a device (Robolectric).
 *
 * The view model, the screens, the split logic and the Anthropic SDK client are the real ones. Two
 * things are replayed from recordings of the real pipeline for the bundled sample bill, because
 * they cannot run on a development machine: the text ML Kit read on a device
 * (`fixtures/sample_bill_ocr.json`) and the answer Claude Opus gave for it
 * (`fixtures/sample_bill_answer.json`, served by a local HTTP server).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SplitFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val vnd = BillCurrency("VND", 0)

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `sample bill is read, split between people and requested`() {
        server.enqueue(recordedClaudeAnswer())
        val analyzer = ClaudeBillAnalyzer("test-key", baseUrl = server.url("/").toString())
        val viewModel = SplitBillViewModel(
            contactRepository = SeededContactRepository(),
            settings = MutableStateFlow(AppSettings(apiKey = "test-key", model = ClaudeBillAnalyzer.DEFAULT_MODEL, localCurrency = "VND")),
            imageLoader = BillImageLoader(context),
            recognizer = recordedMlKitOutput,
            analyzerFor = { analyzer },
        )
        compose.setContent {
            SplitVibeTheme {
                SplitScreen(viewModel = viewModel, hasApiKey = true, newCameraTarget = { Uri.EMPTY }, onExit = {}, onOpenSettings = {})
            }
        }

        // Step 1: photo and instruction.
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        compose.onNodeWithText(BillPrompt.DEFAULT_INSTRUCTION).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)

        // The model was sent the recognized text plus the instruction, never the photo.
        val sent = server.takeRequest().body.readUtf8()
        assertTrue(sent, sent.contains("902.664"))
        assertTrue(sent, sent.contains(INSTRUCTION))
        assertFalse("no image may be uploaded", sent.contains("\"type\":\"image\""))

        // Step 2: Minh and Lan were found in the contacts and the beers went to Minh.
        val result = checkNotNull(viewModel.state.value.result)
        assertEquals(listOf(ME_ID, "c01", "c02"), result.plan.participantIds)
        assertEquals(mapOf(5 to setOf("c01")), result.plan.sharersByItem())
        compose.onNodeWithText(vnd.format(902_664)).assertIsDisplayed()

        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Adds up to the bill total"))
        compose.onNodeWithText(vnd.format(250_993)).assertIsDisplayed()
        compose.onNodeWithText(vnd.format(400_680)).assertIsDisplayed()
        compose.onNodeWithText(vnd.format(250_991)).assertIsDisplayed()

        // The user changes who shares an item: Lan did not have any of the water.
        list.performScrollToNode(hasText("Nước suối"))
        compose.onNodeWithText("Nước suối").performClick()
        compose.onNode(hasText("Lê Thị Lan") and isToggleable()).performClick()
        compose.onNodeWithText("Done").performClick()
        awaitState { viewModel.state.value.result?.plan?.assignments?.get(6) == setOf(ME_ID, "c01") }
        val shares = checkNotNull(viewModel.state.value.result).split
        assertEquals("shares must still add up to the bill", 902_664L, shares.totalMinor)

        // Step 3: money is requested from everyone but the payer.
        val requested = shares.shares.filter { it.personId != ME_ID }.sumOf { it.totalMinor }
        compose.onNodeWithText("Request ${vnd.format(requested)} from 2 people").performClick()
        compose.onNodeWithText("Requests sent").assertIsDisplayed()
        assertEquals(listOf("c01", "c02"), viewModel.state.value.sentRequests?.map { it.contact.id })
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

    private companion object {
        const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
        const val TIMEOUT_MS = 30_000L
    }
}
