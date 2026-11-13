package com.example.splitbill

import android.content.Context
import com.example.splitbill.data.contacts.ContactRepository
import com.example.splitbill.data.contacts.SeededContactRepository
import com.example.splitbill.data.settings.AppSettings
import com.example.splitbill.data.settings.SettingsStore
import com.example.splitbill.llm.BillAnalyzer
import com.example.splitbill.llm.ClaudeBillAnalyzer
import com.example.splitbill.ocr.BillImageLoader
import com.example.splitbill.ocr.BillTextRecognizer
import com.example.splitbill.ocr.MlKitBillTextRecognizer

/** The app's few long-lived objects, wired by hand. Small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val contacts: ContactRepository = SeededContactRepository()
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val imageLoader = BillImageLoader(appContext)
    val recognizer: BillTextRecognizer = MlKitBillTextRecognizer()

    private var cachedAnalyzer: Pair<AppSettings, ClaudeBillAnalyzer>? = null

    /** The analyzer for the given key and model. The HTTP client is reused until the settings change. */
    @Synchronized
    fun analyzerFor(settings: AppSettings): BillAnalyzer {
        cachedAnalyzer?.let { (cachedSettings, analyzer) ->
            if (cachedSettings == settings) return analyzer
            analyzer.close()
        }
        return ClaudeBillAnalyzer(settings.apiKey, settings.model).also { cachedAnalyzer = settings to it }
    }
}
