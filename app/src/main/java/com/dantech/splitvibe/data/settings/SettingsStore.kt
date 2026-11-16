package com.dantech.splitvibe.data.settings

import android.content.Context
import androidx.core.content.edit
import com.dantech.splitvibe.domain.BillCurrency
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** ISO 4217 code of the currency this payment app works in, for example "VND". */
    val localCurrency: String,
    /** The name the user goes by in groups and on their requests; empty until they give one. */
    val myName: String = "",
    /**
     * The user agreed that the phone's online speech service hears them, which is asked the first
     * time they say a change on a phone that cannot turn speech into text by itself. Settings can
     * take it back, and the question is then asked again.
     */
    val speechServiceAllowed: Boolean = false,
)

/** Where the app's own settings are kept on the device. */
class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state: MutableStateFlow<AppSettings>

    val settings: StateFlow<AppSettings>

    init {
        // Builds before the AI proxy kept an Anthropic key and a model name here; neither is used now.
        if (preferences.contains(OLD_KEY_API_KEY) || preferences.contains(OLD_KEY_MODEL)) {
            preferences.edit {
                remove(OLD_KEY_API_KEY)
                remove(OLD_KEY_MODEL)
            }
        }
        state = MutableStateFlow(read())
        settings = state.asStateFlow()
    }

    /** A [localCurrency] that is not a known currency code is ignored and the previous one is kept. */
    fun save(localCurrency: String) {
        val cleanCurrency = localCurrency.trim().uppercase(Locale.ROOT)
        if (BillCurrency.isKnownCode(cleanCurrency)) preferences.edit { putString(KEY_LOCAL_CURRENCY, cleanCurrency) }
        state.value = read()
    }

    /** Keeps the name the user goes by, trimmed and at most [MAX_NAME] characters; a blank one is ignored. */
    fun saveName(name: String) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isNotEmpty()) preferences.edit { putString(KEY_MY_NAME, clean) }
        state.value = read()
    }

    /** Keeps whether the phone's online speech service may hear the user ([AppSettings.speechServiceAllowed]). */
    fun allowSpeechService(allowed: Boolean) {
        preferences.edit { putBoolean(KEY_SPEECH_SERVICE_ALLOWED, allowed) }
        state.value = read()
    }

    private fun read(): AppSettings = AppSettings(
        localCurrency = preferences.getString(KEY_LOCAL_CURRENCY, null) ?: deviceCurrency(),
        myName = preferences.getString(KEY_MY_NAME, null).orEmpty(),
        speechServiceAllowed = preferences.getBoolean(KEY_SPEECH_SERVICE_ALLOWED, false),
    )

    /** The currency of the phone's region, as a sensible default for the app's own currency. */
    private fun deviceCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault(FALLBACK_CURRENCY)

    companion object {
        /** As long as the proxy takes a name. */
        const val MAX_NAME = 60

        private const val KEY_LOCAL_CURRENCY = "local_currency"
        private const val KEY_MY_NAME = "my_name"
        private const val KEY_SPEECH_SERVICE_ALLOWED = "speech_service_allowed"
        private const val OLD_KEY_API_KEY = "anthropic_api_key_encrypted"
        private const val OLD_KEY_MODEL = "model"
        private const val FALLBACK_CURRENCY = "USD"
    }
}
