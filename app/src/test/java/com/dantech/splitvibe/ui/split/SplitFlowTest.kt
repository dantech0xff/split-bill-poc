package com.dantech.splitvibe.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import kotlinx.coroutines.runBlocking
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
 * The view model, the screens, the split logic and the AI proxy client are the real ones. Two
 * things are replayed from recordings of the real pipeline for the bundled sample bill, because
 * they cannot run on a development machine: the text ML Kit read on a device
 * (`fixtures/sample_bill_ocr.json`) and the answer Claude Opus gave for it
 * (`fixtures/sample_bill_answer.json`, served the way the proxy streams it by a local HTTP server).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SplitFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private val vnd = BillCurrency("VND", 0)

    private val recordedMlKitOutput = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `sample bill is read, split between people and requested`() {
        val viewModel = showSplitScreen()

        // Step 1: photo and instruction.
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        assertNull(viewModel.state.value.error)
        compose.onNodeWithText(DEFAULT_INSTRUCTION).performTextReplacement(INSTRUCTION)
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)

        // The model was sent the recognized text plus the instruction, never the photo.
        val sent = server.takeRequest().body.readUtf8()
        assertTrue(sent, sent.contains("902.664"))
        assertTrue(sent, sent.contains(INSTRUCTION))
        assertFalse("no image may be uploaded", sent.contains("\"type\":\"image\""))

        // Step 2: Minh and Lan are on the bill, and the beers, the sixth line, went to Minh.
        val result = checkNotNull(viewModel.state.value.result)
        assertEquals(listOf(ME_ID, "p1", "p2"), result.plan.participantIds)
        assertEquals(listOf("Minh", "Lan"), result.plan.participants.drop(1).map { it.name })
        assertEquals(mapOf("i6" to Division.Equally(among = setOf("p1"))), result.plan.setup.items)
        compose.onNodeWithText(vnd.format(902_664)).assertIsDisplayed()

        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Adds up to the bill total"))
        for (share in listOf(250_993L, 400_680L, 250_991L)) {
            list.performScrollToNode(hasText(vnd.format(share)))
            compose.onNodeWithText(vnd.format(share)).assertIsDisplayed()
        }

        // The user changes who shares an item: Lan did not have any of the water.
        list.performScrollToNode(hasText("Nước suối"))
        // Tapped through its action: the row may sit under a bar, which a touch would land on instead.
        compose.onNodeWithText("Nước suối").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasContentDescription("Lan") and isToggleable()).performClick()
        compose.onNodeWithText("Done").performClick()
        awaitState { viewModel.state.value.result?.plan?.setup?.items?.get("i7") == Division.Equally(among = setOf(ME_ID, "p1")) }
        val shares = checkNotNull(viewModel.state.value.result).split
        assertEquals("shares must still add up to the bill", 902_664L, shares.totalMinor)

        // Step 3: money is requested from everyone but the payer.
        val requested = shares.shares.filter { it.personId != ME_ID }.sumOf { it.totalMinor }
        compose.onNodeWithText("Request ${vnd.format(requested)} from 2 people").performClick()
        compose.onNodeWithText("Ask for the money").assertIsDisplayed()
        assertEquals(listOf("p1", "p2"), viewModel.state.value.sentRequests?.map { it.participant.id })

        // The finished split is kept on the phone with the shares as they were requested.
        val id = checkNotNull(viewModel.state.value.result).saved.id
        awaitState { runBlocking { storage.bills.get(id) }?.status == BillStatus.FINAL }
        val saved = checkNotNull(runBlocking { storage.bills.get(id) })
        assertEquals(shares.shares, saved.shares)
        assertEquals(listOf("Minh", "Lan"), saved.plan.participants.drop(1).map { it.name })
    }

    @Test
    fun `back from the result shows the photo step again, with the photo kept`() {
        val viewModel = showSplitScreen()
        compose.onNodeWithText("Use the sample bill").performClick()
        awaitState { viewModel.state.value.let { (it.photo != null || it.error != null) && !it.isBusy } }
        compose.onNodeWithText("Read and split the bill").performClick()
        awaitState { viewModel.state.value.let { it.result != null || it.error != null } }
        assertNull(viewModel.state.value.error)

        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("Read and split the bill").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bill photo").assertIsDisplayed()
        compose.onNodeWithText("Bill total").assertDoesNotExist()
    }

    /** Shows the split screen with the recorded text of the sample bill and the recorded answer for it. */
    private fun showSplitScreen(): SplitBillViewModel {
        server.enqueue(Fixtures.proxyAnswer())
        val proxy = TestProxy(server, context)
        val viewModel = testSplitViewModel(
            context,
            storage,
            account = proxy.account.account,
            recognizer = recordedMlKitOutput,
            analyzer = proxy.analyzer,
            localCurrency = "VND",
        )
        compose.setContent {
            SplitVibeTheme {
                SplitScreen(viewModel = viewModel, aiStatus = AiStatus.AVAILABLE, newCameraTarget = {
                    Uri.EMPTY
                }, scanIntent = { null }, onExit = {}, onOpenSettings = {})
            }
        }
        return viewModel
    }

    /**
     * Waits for work that finishes on a background thread. Under Robolectric the test itself runs on
     * the main thread, so results posted back to it are only delivered when the main looper is run.
     */
    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private companion object {
        const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
        const val TIMEOUT_MS = 30_000L
    }
}
