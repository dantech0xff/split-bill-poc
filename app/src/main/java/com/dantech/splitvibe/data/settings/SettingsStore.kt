package com.dantech.splitvibe.data.settings

import android.content.Context
import androidx.core.content.edit
import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.llm.ClaudeBillAnalyzer
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String,
    val model: String,
    /** ISO 4217 code of the currency this payment app works in, for example "VND". */
    val localCurrency: String,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
}

/**
 * Where the Anthropic API key, the model name and the app's local currency are kept on the device.
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

    /** A [localCurrency] that is not a known currency code is ignored and the previous one is kept. */
    fun save(apiKey: String, model: String, localCurrency: String) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifEmpty { ClaudeBillAnalyzer.DEFAULT_MODEL }
        val cleanCurrency = localCurrency.trim().uppercase(Locale.ROOT)
        preferences.edit {
            if (cleanKey.isEmpty()) remove(KEY_API_KEY) else putString(KEY_API_KEY, cipher.encrypt(cleanKey))
            putString(KEY_MODEL, cleanModel)
            if (BillCurrency.isKnownCode(cleanCurrency)) putString(KEY_LOCAL_CURRENCY, cleanCurrency)
        }
        state.value = read()
    }

    private fun read(): AppSettings {
        val storedKey = preferences.getString(KEY_API_KEY, null)?.let(cipher::decrypt)
        return AppSettings(
            apiKey = storedKey?.takeIf { it.isNotBlank() } ?: defaultApiKey,
            model = preferences.getString(KEY_MODEL, null) ?: ClaudeBillAnalyzer.DEFAULT_MODEL,
            localCurrency = preferences.getString(KEY_LOCAL_CURRENCY, null) ?: deviceCurrency(),
        )
    }

    /** The currency of the phone's region, as a sensible default for the app's own currency. */
    private fun deviceCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault(FALLBACK_CURRENCY)

    private companion object {
        const val KEY_API_KEY = "anthropic_api_key_encrypted"
        const val KEY_MODEL = "model"
        const val KEY_LOCAL_CURRENCY = "local_currency"
        const val FALLBACK_CURRENCY = "USD"
    }
}
