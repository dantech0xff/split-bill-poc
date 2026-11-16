package com.dantech.splitvibe.assistant

import android.Manifest
import android.app.Application
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Turning speech into text, on what a test can drive without a microphone: the recognizers a phone has and what they report. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VoiceInputTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a phone that cannot turn speech into text is offered no mic`() {
        TestVoice.recognizeOnDevice(false)

        assertEquals(VoiceSupport.NONE, voiceInput().support)
    }

    @Test
    fun `the phone's own recognizer comes before its speech service`() {
        TestVoice.recognizeOnDevice(false)
        TestVoice.installSpeechService(context)
        assertEquals(VoiceSupport.NETWORK, voiceInput().support)

        TestVoice.recognizeOnDevice()

        assertEquals(VoiceSupport.ON_DEVICE, voiceInput().support)
    }

    @Test
    fun `what is said arrives word by word and then whole, and the recognizer is let go`() {
        TestVoice.installSpeechService(context)
        TestVoice.recognizeOnDevice()
        val voice = voiceInput()
        val partial = mutableListOf<String>()
        val ends = mutableListOf<VoiceEnd>()

        voice.listen(onPartial = { partial += it }, onEnd = { ends += it })
        val recognizer = TestVoice.latest()
        recognizer.triggerOnPartialResults(TestVoice.heard("Lan didn't"))
        recognizer.triggerOnResults(TestVoice.heard(" Lan didn't have beer "))
        idle()

        assertEquals(listOf("Lan didn't"), partial)
        assertEquals(listOf<VoiceEnd>(VoiceEnd.Heard("Lan didn't have beer")), ends)
        assertFalse(voice.isListening)
        assertTrue(recognizer.isDestroyed)
        val asked = checkNotNull(recognizer.lastRecognizerIntent)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, asked.action)
        assertTrue(asked.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun `how listening fails is told apart, and a cancelled recognizer says nothing more`() {
        TestVoice.installSpeechService(context)
        val voice = voiceInput()
        val cases = mapOf(
            SpeechRecognizer.ERROR_NO_MATCH to VoiceEnd.NothingHeard,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT to VoiceEnd.NothingHeard,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to VoiceEnd.NoPermission,
            SpeechRecognizer.ERROR_NETWORK to VoiceEnd.Failed,
        )
        for ((error, expected) in cases) {
            var end: VoiceEnd? = null
            voice.listen(onPartial = {}, onEnd = { end = it })
            TestVoice.latest().triggerOnError(error)
            idle()
            assertEquals("error $error", expected, end)
        }

        var late: VoiceEnd? = null
        voice.listen(onPartial = {}, onEnd = { late = it })
        val cancelled = TestVoice.latest()
        voice.cancel()
        cancelled.triggerOnResults(TestVoice.heard("too late"))
        idle()
        assertNull(late)
        assertTrue(cancelled.isDestroyed)
    }

    @Test
    fun `when the phone cannot recognize its language by itself, its speech service is offered next`() {
        TestVoice.installSpeechService(context)
        TestVoice.recognizeOnDevice()
        val voice = voiceInput()
        var end: VoiceEnd? = null

        voice.listen(onPartial = {}, onEnd = { end = it })
        TestVoice.latest().triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        idle()

        assertEquals(VoiceEnd.LanguageUnavailable(speechServiceNext = true), end)
        assertEquals(VoiceSupport.NETWORK, voice.support)
        // The next screen tries the phone's own recognizer first again.
        assertEquals(VoiceSupport.ON_DEVICE, voiceInput().support)
    }

    @Test
    fun `when the speech service cannot take the phone's language either, nothing more is offered for it`() {
        TestVoice.recognizeOnDevice(false)
        TestVoice.installSpeechService(context)

        assertEquals(VoiceEnd.LanguageUnavailable(speechServiceNext = false), endOfLanguageUnavailable(voiceInput()))
    }

    @Test
    fun `when the phone's own recognizer cannot take its language and it has no speech service, the mic goes`() {
        TestVoice.recognizeOnDevice()
        val voice = voiceInput()
        assertEquals(VoiceSupport.ON_DEVICE, voice.support)

        assertEquals(VoiceEnd.LanguageUnavailable(speechServiceNext = false), endOfLanguageUnavailable(voice))
        assertEquals(VoiceSupport.NONE, voice.support)
    }

    @Test
    fun `the microphone and the speech service are each the user's to allow, and Settings can take the speech service back`() {
        val settings = SettingsStore(context)
        val voice = VoiceInput(context, settings)
        assertFalse(voice.hasPermission)
        assertFalse(voice.networkAllowed)

        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        voice.networkAllowed = true

        assertTrue(voice.hasPermission)
        assertTrue("remembered for the next screen", voiceInput().networkAllowed)
        assertTrue("and kept with the settings", settings.settings.value.speechServiceAllowed)

        settings.allowSpeechService(false)

        assertFalse(voice.networkAllowed)
        assertFalse(voiceInput().networkAllowed)
    }

    /** A screen's voice input, with the app's settings as the phone keeps them. */
    private fun voiceInput() = VoiceInput(context, SettingsStore(context))

    /** How listening ends when the recognizer reports that it cannot take the phone's language. */
    private fun endOfLanguageUnavailable(voice: VoiceInput): VoiceEnd? {
        var end: VoiceEnd? = null
        voice.listen(onPartial = {}, onEnd = { end = it })
        TestVoice.latest().triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        idle()
        return end
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
