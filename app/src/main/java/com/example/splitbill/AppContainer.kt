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

    private var cachedAnalyzer: ClaudeBillAnalyzer? = null
    private var cachedFor: Pair<String, String>? = null

    /** The analyzer for the given key and model. The HTTP client is reused until one of them changes. */
    @Synchronized
    fun analyzerFor(settings: AppSettings): BillAnalyzer {
        val wanted = settings.apiKey to settings.model
        cachedAnalyzer?.let { analyzer ->
            if (cachedFor == wanted) return analyzer
            analyzer.close()
        }
        return ClaudeBillAnalyzer(settings.apiKey, settings.model).also {
            cachedAnalyzer = it
            cachedFor = wanted
        }
    }
}
