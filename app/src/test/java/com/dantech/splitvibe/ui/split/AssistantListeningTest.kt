package com.dantech.splitvibe.ui.split

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.NeverAsked
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.TestVoice
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Listening for a change of the split lasts no longer than the screen is seen: the microphone is never left on behind another app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AssistantListeningTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()

    private val noPhotos = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = error("This bill is typed in by hand")
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `listening stops when the screen stops, and what was heard so far stays in the field`() {
        TestVoice.recognizeOnDevice()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val proxy = TestProxy(server, context)
        val split = testSplitViewModel(context, storage, account = proxy.account.account, recognizer = noPhotos, analyzer = NeverAsked)
        split.startManualEntry()
        split.editBill { BillEditor.withItemAdded(it, "Bia", BigDecimal(3), unitPriceMinor = 30_000) }
        split.doneEditing()
        val assistant = SplitAssistantViewModel(
            host = split,
            assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true, sellsPro = false),
            voice = VoiceInput(context, SettingsStore(context)),
            settings = MutableStateFlow(AppSettings(localCurrency = "VND")),
            account = proxy.account.account,
        )
        val screen = Screen()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                SplitVibeTheme {
                    SplitScreen(
                        viewModel = split,
                        aiStatus = AiStatus.AVAILABLE,
                        newCameraTarget = { Uri.EMPTY },
                        scanIntent = { null },
                        onExit = {},
                        onOpenSettings = {},
                        onOpenPro = {},
                        assistant = assistant,
                    )
                }
            }
        }
        assistant.onMic()
        val recognizer = TestVoice.latest()
        recognizer.triggerOnPartialResults(TestVoice.heard("Lan didn't"))
        compose.waitForIdle()
        assertTrue(assistant.state.value.voice.listening)

        compose.runOnIdle { screen.stop() }
        compose.waitForIdle()

        assertTrue("the recognizer is let go", recognizer.isDestroyed)
        assertFalse(assistant.state.value.voice.listening)
        assertEquals("Lan didn't", assistant.state.value.text)
    }

    /** The screen's lifecycle, as the test moves it: shown until it is stopped. */
    private class Screen : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }

        override val lifecycle: Lifecycle get() = registry

        fun stop() {
            registry.currentState = Lifecycle.State.CREATED
        }
    }
}
