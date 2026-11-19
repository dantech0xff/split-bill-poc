package com.dantech.splitvibe.ui.split

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.Fixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The consent to the AI, on the view models of the split screen and against a local server that
 * answers as the proxy does: no call goes to the AI before the person agrees on the consent screen,
 * and none after Settings takes the consent back, until they agree again.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AiConsentFlowTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()

    /** The app's settings as the split screen reads them: the person has not agreed to the AI yet. */
    private val settings = MutableStateFlow(AppSettings(localCurrency = "VND"))
    private lateinit var proxy: TestProxy
    private lateinit var split: SplitBillViewModel

    private val recorded = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = Fixtures.ocr("sample_bill_ocr.json")
    }

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        split = testSplitViewModel(context, storage, proxy.account.account, recorded, proxy.analyzer, settings = settings)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `nothing goes to the AI before the person agrees, and the read they asked for goes ahead once they do`() {
        pickTheSampleBill()
        server.enqueue(Fixtures.proxyAnswer())

        split.analyze(defaultInstruction = INSTRUCTION)

        assertTrue(split.state.value.askingAiConsent)
        assertNothingSentFor(500)
        assertNull(split.state.value.result)

        split.agreeToAi()

        awaitOnMain { split.state.value.let { it.result != null || it.error != null } }
        assertNull(split.state.value.error)
        assertFalse(split.state.value.askingAiConsent)
        assertTrue("kept for the next read", settings.value.aiAllowed)
        assertEquals("/v1/bill-analysis", server.takeRequest(5, TimeUnit.SECONDS)?.path)
    }

    @Test
    fun `typing the bill in by hand instead keeps its photo and asks the AI nothing`() {
        pickTheSampleBill()
        val photos = split.state.value.pages

        split.analyze(defaultInstruction = INSTRUCTION)
        split.typeInsteadOfAi()

        val typed = checkNotNull(split.state.value.result)
        assertTrue("the editor of a bill typed in by hand", split.state.value.editing && typed.bill.items.isEmpty())
        assertEquals(photos, split.state.value.pages)
        assertEquals(1, typed.saved.pages.size)
        assertFalse(split.state.value.askingAiConsent)
        assertFalse(settings.value.aiAllowed)
        assertNothingSentFor(500)
    }

    @Test
    fun `closing the consent screen leaves the photo and the instruction as they were`() {
        pickTheSampleBill()
        split.onInstructionChanged(INSTRUCTION)

        split.analyze(defaultInstruction = "Split this bill")
        split.closeAiConsent()

        assertFalse(split.state.value.askingAiConsent)
        assertEquals(INSTRUCTION, split.state.value.instruction)
        assertEquals(1, split.state.value.pages.size)
        assertNull(split.state.value.result)
        assertNothingSentFor(500)
    }

    @Test
    fun `once the consent is taken back, no AI call goes out until the person agrees again`() {
        settings.update { it.copy(aiAllowed = true) }
        pickTheSampleBill()
        server.enqueue(Fixtures.proxyAnswer())
        split.analyze(defaultInstruction = INSTRUCTION)
        awaitOnMain { split.state.value.result != null }
        assertEquals(1, server.requestCount)
        val assistant = assistant()

        // Settings takes it back.
        settings.update { it.copy(aiAllowed = false) }
        assistant.onTextChanged("Lan didn't have beer")
        assistant.send()
        awaitOnMain { assistant.state.value.askingAiConsent }
        split.backToInput()
        split.analyze(defaultInstruction = INSTRUCTION)

        assertTrue(split.state.value.askingAiConsent)
        assertNothingSentFor(500, sentBefore = 1)
    }

    @Test
    fun `a change of the split asks first, turning it down keeps the split, and agreeing sends it`() {
        split.startManualEntry()
        split.editBill { BillEditor.withItemAdded(it, "Bia", BigDecimal(3), unitPriceMinor = 30_000) }
        split.addPerson("Lan")
        split.doneEditing()
        val before = checkNotNull(split.billOnScreen.value)
        val assistant = assistant()
        assistant.onTextChanged("Lan didn't have beer")

        assistant.send()
        awaitOnMain { assistant.state.value.askingAiConsent }
        assistant.declineAi()

        awaitOnMain { !assistant.state.value.askingAiConsent }
        assertFalse(assistant.state.value.working)
        assertNothingSentFor(500)
        awaitOnMain { split.billOnScreen.value == before }

        server.enqueue(MockResponse().setResponseCode(503).setHeader("content-type", "application/json").setBody("""{"error":"ai_off"}"""))
        assistant.send()
        assistant.agreeToAi()

        assertEquals("/v1/split-revision", server.takeRequest(5, TimeUnit.SECONDS)?.path)
        assertTrue(settings.value.aiAllowed)
    }

    /** The assistant of the split screen, which reads the same settings and keeps the consent in them. */
    private fun assistant() = SplitAssistantViewModel(
        host = split,
        assistant = SplitAssistant(proxy.api, proxy.account, canSignIn = true, sellsPro = false),
        voice = VoiceInput(context, SettingsStore(context)),
        settings = settings,
        allowAi = { allowed -> settings.update { it.copy(aiAllowed = allowed) } },
        account = proxy.account.account,
    )

    private fun pickTheSampleBill() {
        split.onSamplePicked()
        awaitOnMain { split.state.value.let { it.photo != null && !it.isBusy } }
    }

    /** Waits [millis] and checks that no request reached the proxy beyond the [sentBefore] that had. */
    private fun assertNothingSentFor(millis: Long, sentBefore: Int = 0) {
        val until = System.currentTimeMillis() + millis
        awaitOnMain { System.currentTimeMillis() > until }
        assertEquals("requests to the proxy", sentBefore, server.requestCount)
    }

    private companion object {
        const val INSTRUCTION = "Split with Minh and Lan. Minh had the beers."
    }
}
