package com.dantech.splitvibe.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.dantech.splitvibe.data.settings.SettingsStore

/** How this phone can turn what the user says into text. */
enum class VoiceSupport {
    /** The phone recognizes speech by itself, so the sound never leaves it. */
    ON_DEVICE,

    /** Only through the phone's speech service, which may send the sound over the internet. The user is asked first. */
    NETWORK,

    /** Not at all. The mic is not offered, and changes are typed. */
    NONE,
}

/** How listening for one phrase ended. */
sealed interface VoiceEnd {
    /** What the user said, as text for them to check. */
    data class Heard(val text: String) : VoiceEnd

    /** Nothing was understood, or listening stopped before anything was said. */
    data object NothingHeard : VoiceEnd

    /** The microphone is not allowed. */
    data object NoPermission : VoiceEnd

    /**
     * The recognizer cannot take the phone's language. When it was the phone's own and the phone has
     * a speech service, which may take it, [speechServiceNext] is true: the speech service listens next.
     */
    data class LanguageUnavailable(val speechServiceNext: Boolean) : VoiceEnd

    /** The recognizer failed, for example without a connection it needed. */
    data object Failed : VoiceEnd
}

/**
 * Turns what the user says into text with Android's [SpeechRecognizer], one phrase at a time. It
 * listens on the phone itself when the phone can, on Android 12 and later, so the sound stays on
 * the phone. Otherwise it uses the phone's speech service, which may send the sound away; the
 * caller asks the user before the first use and records the answer in [networkAllowed], which is
 * kept with the app's [settings], where the user can take it back.
 *
 * The text only goes back to the caller, for the user to check before anything is sent. Use it on
 * the main thread, as the recognizer must be.
 */
class VoiceInput(context: Context, private val settings: SettingsStore) {

    private val context: Context = context.applicationContext

    /** The recognizer listening now; null while nothing is. */
    private var recognizer: SpeechRecognizer? = null

    /**
     * The phone's own recognizer did not take its language, so the speech service listens in its
     * place for as long as this screen lasts. The next screen tries the phone's own first again: once
     * the phone has the language, for example after downloading it, the sound stays on the phone.
     */
    private var onDeviceUnusable = false

    val support: VoiceSupport
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !onDeviceUnusable &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> VoiceSupport.ON_DEVICE

            SpeechRecognizer.isRecognitionAvailable(context) -> VoiceSupport.NETWORK

            else -> VoiceSupport.NONE
        }

    /** Whether the user agreed that the phone's speech service hears them. It is asked once and remembered, until Settings takes it back. */
    var networkAllowed: Boolean
        get() = settings.settings.value.speechServiceAllowed
        set(value) = settings.allowSpeechService(value)

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val isListening: Boolean get() = recognizer != null

    /**
     * Listens for one phrase in the phone's language. [onPartial] gets the words heard so far, and
     * [onEnd] how it ended, once, unless [cancel] comes first.
     */
    fun listen(onPartial: (String) -> Unit, onEnd: (VoiceEnd) -> Unit) {
        cancel()
        val support = support
        val onDevice = support == VoiceSupport.ON_DEVICE
        val created = when {
            support == VoiceSupport.NONE -> return onEnd(VoiceEnd.Failed)
            onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else -> SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = created
        created.setRecognitionListener(Listener(created, onDevice, onPartial, onEnd))
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // The speech service stays on the phone too when it can.
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
        )
    }

    /** Stops listening; what was said so far still arrives, as the end of the phrase. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Stops listening and lets go of the recognizer; nothing more arrives. */
    fun cancel() {
        val listening = recognizer ?: return
        recognizer = null
        listening.destroy()
    }

    private inner class Listener(
        private val owner: SpeechRecognizer,
        private val onDevice: Boolean,
        private val onPartial: (String) -> Unit,
        private val onEnd: (VoiceEnd) -> Unit,
    ) : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            if (recognizer === owner) textOf(partialResults)?.let(onPartial)
        }

        override fun onResults(results: Bundle?) = end(textOf(results)?.let(VoiceEnd::Heard) ?: VoiceEnd.NothingHeard)

        override fun onError(error: Int) = end(endOf(error))

        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun end(end: VoiceEnd) {
            // A recognizer that was cancelled or replaced says nothing more.
            if (recognizer !== owner) return
            cancel()
            if (onDevice && end is VoiceEnd.LanguageUnavailable) {
                onDeviceUnusable = true
                return onEnd(VoiceEnd.LanguageUnavailable(speechServiceNext = support == VoiceSupport.NETWORK))
            }
            onEnd(end)
        }
    }

    private companion object {
        fun textOf(results: Bundle?): String? =
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.ifEmpty { null }

        fun endOf(error: Int): VoiceEnd = when {
            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceEnd.NothingHeard

            // What a recognizer reports when it was stopped before it heard anything.
            error == SpeechRecognizer.ERROR_CLIENT -> VoiceEnd.NothingHeard

            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceEnd.NoPermission

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) ->
                VoiceEnd.LanguageUnavailable(speechServiceNext = false)

            else -> VoiceEnd.Failed
        }
    }
}
