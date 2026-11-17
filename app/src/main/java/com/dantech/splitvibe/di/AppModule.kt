package com.dantech.splitvibe.di

import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.billing.BillingRepository
import com.dantech.splitvibe.billing.RevenueCatBilling
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.CredentialManagerGoogleAccounts
import com.dantech.splitvibe.data.bills.BillPages
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.people.ContactNames
import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.GatewayBillAnalyzer
import com.dantech.splitvibe.notifications.DeviceRegistrar
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.MlKitBillTextRecognizer
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.claim.BillOnScreen
import com.dantech.splitvibe.ui.claim.ClaimViewModel
import com.dantech.splitvibe.ui.groups.AddToGroupViewModel
import com.dantech.splitvibe.ui.groups.GroupViewModel
import com.dantech.splitvibe.ui.groups.GroupsViewModel
import com.dantech.splitvibe.ui.groups.JoinGroupViewModel
import com.dantech.splitvibe.ui.history.HistoryViewModel
import com.dantech.splitvibe.ui.payments.PaymentMethodsViewModel
import com.dantech.splitvibe.ui.paywall.ProViewModel
import com.dantech.splitvibe.ui.requests.RequestLinkViewModel
import com.dantech.splitvibe.ui.requests.RequestMoneyViewModel
import com.dantech.splitvibe.ui.requests.RequestsViewModel
import com.dantech.splitvibe.ui.settings.SettingsViewModel
import com.dantech.splitvibe.ui.split.SplitAssistantViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import com.dantech.splitvibe.ui.split.SplitOnScreen
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The app's long-lived objects and how they depend on each other. */
val appModule = module {
    single { SettingsStore(androidContext()) }
    single { AccountStore(androidContext(), proxySetUp = BuildConfig.AI_PROXY_URL.toHttpUrlOrNull() != null) }
    single { ProxyApi(BuildConfig.AI_PROXY_URL, account = get(), appVersion = BuildConfig.VERSION_CODE) }
    single<BillAnalyzer> {
        GatewayBillAnalyzer(
            api = get(),
            account = get(),
            canSignIn = get<AccountRepository>().canSignIn,
            sellsPro = get<BillingRepository>().sellsPro,
        )
    }
    single {
        SplitAssistant(
            api = get(),
            account = get(),
            canSignIn = get<AccountRepository>().canSignIn,
            sellsPro = get<BillingRepository>().sellsPro,
        )
    }
    single {
        BillingRepository(
            // A build without RevenueCat's key sells no Pro.
            billing = BuildConfig.REVENUECAT_API_KEY.takeIf { it.isNotBlank() }?.let { RevenueCatBilling(androidContext(), it) },
            api = get(),
            store = get(),
            canSignIn = get<AccountRepository>().canSignIn,
        )
    }
    // One for each screen that listens, which lets go of the recognizer when the screen goes.
    factory { VoiceInput(androidContext(), settings = get()) }
    single {
        AccountRepository(
            api = get(),
            store = get(),
            // A build without the app's web client ID cannot sign in.
            google = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() }?.let(::CredentialManagerGoogleAccounts),
            cache = get(),
            devices = get(),
        )
    }
    single { DeviceRegistrar(api = get(), account = get()) }
    single { BillImageLoader(androidContext()) }
    single<BillTextRecognizer> { MlKitBillTextRecognizer() }
    single { DocumentScanner() }

    // Bills, the people split with and the photos of the bills all stay on the phone.
    single { SplitVibeDatabase.create(androidContext()) }
    single { BillPages(androidContext()) }
    single { PeopleRepository(database = get()) }
    single { BillRepository(database = get(), people = get(), pages = get()) }
    single { ContactNames(androidContext().contentResolver) }
    single { FxRepository(api = get(), file = File(androidContext().filesDir, "fx-rates.json")) }

    // The ways the user takes money stay on the phone; groups and requests live on the proxy, with
    // their last answers kept here to read without a connection.
    single { PaymentMethodRepository(database = get()) }
    single { ProxyCache(get<SplitVibeDatabase>().proxyCache()) }
    single { GroupRepository(api = get(), cache = get(), account = get()) }
    single { RequestRepository(api = get(), cache = get(), account = get()) }
    single { ShareRepository(api = get()) }

    viewModel { SettingsViewModel(repository = get()) }
    viewModel { ProViewModel(billing = get(), accounts = get(), account = get<AccountStore>().account) }
    viewModel { HistoryViewModel(bills = get(), savedState = get()) }
    viewModel { PaymentMethodsViewModel(repository = get()) }
    viewModel {
        GroupsViewModel(
            repository = get(),
            account = get<AccountStore>().account,
            settings = get<SettingsStore>().settings,
            saveName = get<SettingsStore>()::saveName,
        )
    }
    viewModel { (groupId: String) ->
        GroupViewModel(groupId, repository = get(), requests = get(), paymentMethods = get(), settings = get<SettingsStore>().settings)
    }
    viewModel { (token: String) ->
        JoinGroupViewModel(
            token,
            repository = get(),
            account = get<AccountStore>().account,
            settings = get<SettingsStore>().settings,
            saveName = get<SettingsStore>()::saveName,
        )
    }
    viewModel { AddToGroupViewModel(groups = get(), requests = get(), fx = get()) }
    viewModel { (host: BillOnScreen) ->
        ClaimViewModel(
            host = host,
            shares = get(),
            paymentMethods = get(),
            settings = get<SettingsStore>().settings,
            account = get<AccountStore>().account,
        )
    }
    viewModel { RequestsViewModel(repository = get(), groups = get(), account = get<AccountStore>().account) }
    viewModel { (token: String) -> RequestLinkViewModel(token, repository = get(), groups = get(), account = get<AccountStore>().account) }
    viewModel {
        RequestMoneyViewModel(
            paymentMethods = get(),
            requests = get(),
            account = get<AccountStore>().account,
            settings = get<SettingsStore>().settings,
            saveName = get<SettingsStore>()::saveName,
        )
    }
    viewModel { (host: SplitOnScreen) ->
        SplitAssistantViewModel(
            host = host,
            assistant = get(),
            voice = get(),
            settings = get<SettingsStore>().settings,
            account = get<AccountStore>().account,
        )
    }
    viewModel {
        SplitBillViewModel(
            settings = get<SettingsStore>().settings,
            account = get<AccountStore>().account,
            canSignIn = get<AccountRepository>().canSignIn,
            sellsPro = get<BillingRepository>().sellsPro,
            imageLoader = get(),
            recognizer = get(),
            analyzer = get(),
            bills = get(),
            peopleRepository = get(),
            pageStore = get(),
            fx = get(),
            contactNames = get(),
            savedState = get(),
            shares = get(),
        )
    }
}
