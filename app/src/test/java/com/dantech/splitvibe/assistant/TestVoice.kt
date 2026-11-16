package com.dantech.splitvibe.assistant

import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSpeechRecognizer

/** The speech recognizers a phone may have, set up under Robolectric, and what they hear. */
object TestVoice {

    /** The phone gets a speech service, which may send what is said over the internet. */
    fun installSpeechService(context: Context) {
        val service = ComponentName("com.example.speech", "com.example.speech.RecognitionService")
        val packages = shadowOf(context.packageManager)
        packages.addServiceIfNotPresent(service)
        packages.addIntentFilterForService(service, IntentFilter(RecognitionService.SERVICE_INTERFACE))
    }

    /**
     * Whether the phone recognizes speech by itself, which Android 12 and later can. Under
     * Robolectric it does, unless a test says otherwise.
     */
    fun recognizeOnDevice(available: Boolean = true) = ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(available)

    /**
     * The recognizer listening now, as the test drives it. The main looper runs first: a recognizer
     * takes its listener once it is connected, which Robolectric does on the main looper.
     */
    fun latest(): ShadowSpeechRecognizer {
        shadowOf(Looper.getMainLooper()).idle()
        return shadowOf(checkNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer()) { "Nothing listens" })
    }

    /** What a recognizer reports for [text]. */
    fun heard(text: String): Bundle = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }
}
