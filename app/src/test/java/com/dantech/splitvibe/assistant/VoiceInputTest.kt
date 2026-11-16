package com.dantech.splitvibe.assistant

import android.Manifest
import android.app.Application
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        assertEquals(VoiceSupport.NONE, VoiceInput(context).support)
    }

    @Test
    fun `the phone's own recognizer comes before its speech service`() {
        TestVoice.recognizeOnDevice(false)
        TestVoice.installSpeechService(context)
        assertEquals(VoiceSupport.NETWORK, VoiceInput(context).support)

        TestVoice.recognizeOnDevice()

        assertEquals(VoiceSupport.ON_DEVICE, VoiceInput(context).support)
    }

    @Test
    fun `what is said arrives word by word and then whole, and the recognizer is let go`() {
        TestVoice.installSpeechService(context)
        TestVoice.recognizeOnDevice()
        val voice = VoiceInput(context)
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
        val voice = VoiceInput(context)
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
        val voice = VoiceInput(context)
        var end: VoiceEnd? = null

        voice.listen(onPartial = {}, onEnd = { end = it })
        TestVoice.latest().triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        idle()

        assertEquals(VoiceEnd.LanguageUnavailable, end)
        assertEquals(VoiceSupport.NETWORK, voice.support)
    }

    @Test
    fun `the microphone and the speech service are each the user's to allow`() {
        val voice = VoiceInput(context)
        assertFalse(voice.hasPermission)
        assertFalse(voice.networkAllowed)

        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        voice.networkAllowed = true

        assertTrue(voice.hasPermission)
        assertTrue("remembered for the next screen", VoiceInput(context).networkAllowed)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
