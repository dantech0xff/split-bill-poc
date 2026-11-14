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

    private fun read(): AppSettings = AppSettings(localCurrency = preferences.getString(KEY_LOCAL_CURRENCY, null) ?: deviceCurrency())

    /** The currency of the phone's region, as a sensible default for the app's own currency. */
    private fun deviceCurrency(): String =
        runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault(FALLBACK_CURRENCY)

    private companion object {
        const val KEY_LOCAL_CURRENCY = "local_currency"
        const val OLD_KEY_API_KEY = "anthropic_api_key_encrypted"
        const val OLD_KEY_MODEL = "model"
        const val FALLBACK_CURRENCY = "USD"
    }
}
