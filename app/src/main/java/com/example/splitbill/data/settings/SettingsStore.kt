package com.example.splitbill.data.settings

import android.content.Context
import androidx.core.content.edit
import com.example.splitbill.BuildConfig
import com.example.splitbill.llm.ClaudeBillAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String,
    val model: String,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
}

/**
 * Where the Anthropic API key and model name are kept on the device.
 *
 * The key is encrypted with [KeystoreCipher] before it is written to preferences. Storing a real
 * API key on a phone is acceptable for a proof of concept only: a shipped app must call its own
 * backend and keep the key there.
 */
class SettingsStore(
    context: Context,
    private val cipher: KeystoreCipher = KeystoreCipher(),
    /** Key baked into debug builds from local.properties, used until the user saves their own. */
    private val defaultApiKey: String = BuildConfig.DEFAULT_ANTHROPIC_API_KEY,
) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun save(apiKey: String, model: String) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifEmpty { ClaudeBillAnalyzer.DEFAULT_MODEL }
        preferences.edit {
            if (cleanKey.isEmpty()) remove(KEY_API_KEY) else putString(KEY_API_KEY, cipher.encrypt(cleanKey))
            putString(KEY_MODEL, cleanModel)
        }
        state.value = read()
    }

    private fun read(): AppSettings {
        val storedKey = preferences.getString(KEY_API_KEY, null)?.let(cipher::decrypt)
        return AppSettings(
            apiKey = storedKey?.takeIf { it.isNotBlank() } ?: defaultApiKey,
            model = preferences.getString(KEY_MODEL, null) ?: ClaudeBillAnalyzer.DEFAULT_MODEL,
        )
    }

    private companion object {
        const val KEY_API_KEY = "anthropic_api_key_encrypted"
        const val KEY_MODEL = "model"
    }
}
