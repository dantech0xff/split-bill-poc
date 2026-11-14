package com.dantech.splitvibe.di

import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CredentialManagerGoogleAccounts
import com.dantech.splitvibe.data.contacts.ContactRepository
import com.dantech.splitvibe.data.contacts.SeededContactRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.GatewayBillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.MlKitBillTextRecognizer
import com.dantech.splitvibe.ui.settings.SettingsViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The app's long-lived objects and how they depend on each other. */
val appModule = module {
    single<ContactRepository> { SeededContactRepository() }
    single { SettingsStore(androidContext()) }
    single { AccountStore(androidContext(), proxySetUp = BuildConfig.AI_PROXY_URL.toHttpUrlOrNull() != null) }
    single { ProxyApi(BuildConfig.AI_PROXY_URL, account = get(), appVersion = BuildConfig.VERSION_CODE) }
    single<BillAnalyzer> { GatewayBillAnalyzer(api = get(), account = get()) }
    single {
        AccountRepository(
            api = get(),
            store = get(),
            // A build without the app's web client ID cannot sign in.
            google = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }?.let(::CredentialManagerGoogleAccounts),
        )
    }
    single { BillImageLoader(androidContext()) }
    single<BillTextRecognizer> { MlKitBillTextRecognizer() }

    viewModel { SettingsViewModel(repository = get()) }
    viewModel {
        SplitBillViewModel(
            contactRepository = get(),
            settings = get<SettingsStore>().settings,
            account = get<AccountStore>().account,
            imageLoader = get(),
            recognizer = get(),
            analyzer = get(),
        )
    }
}
