package com.dantech.splitvibe.di

import com.dantech.splitvibe.data.contacts.ContactRepository
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.llm.BillAnalyzerProvider
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.MlKitBillTextRecognizer
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The app's long-lived objects and how they depend on each other. */
val appModule = module {
    single<ContactRepository> { SeededContactRepository() }
    single { SettingsStore(androidContext()) }
    single { BillImageLoader(androidContext()) }
    single<BillTextRecognizer> { MlKitBillTextRecognizer() }
    single { BillAnalyzerProvider() }

    viewModel {
        SplitBillViewModel(
            contactRepository = get(),
            settings = get<SettingsStore>().settings,
            imageLoader = get(),
            recognizer = get(),
            analyzerFor = get<BillAnalyzerProvider>()::analyzerFor,
        )
    }
}
