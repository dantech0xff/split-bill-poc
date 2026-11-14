package com.dantech.splitvibe.llm

import com.dantech.splitvibe.data.settings.AppSettings

/** Hands out the analyzer for the current key and model. The HTTP client is reused until one of them changes. */
class BillAnalyzerProvider {

    private var cachedAnalyzer: ClaudeBillAnalyzer? = null
    private var cachedFor: Pair<String, String>? = null

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
