package com.dantech.splitvibe.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.billing.BillingRepository
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.bills.BillPages
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.people.ContactNames
import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.history.HistoryViewModel
import com.dantech.splitvibe.ui.home.HomeViewModel
import com.dantech.splitvibe.ui.paywall.ProViewModel
import com.dantech.splitvibe.ui.settings.SettingsViewModel
import com.dantech.splitvibe.ui.split.SplitAssistantViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication

/**
 * Koin resolves dependencies when they are first asked for, so a missing definition would only
 * show up as a crash on the screen that needs it. This test asks for everything up front.
 */
@RunWith(AndroidJUnit4::class)
class AppModuleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every object the app asks for can be created`() {
        val koin = koinApplication {
            androidContext(context)
            modules(appModule)
        }.koin

        assertNotNull(koin.get<SettingsStore>())
        assertNotNull(koin.get<BillImageLoader>())
        assertNotNull(koin.get<BillTextRecognizer>())
        assertNotNull(koin.get<AccountStore>())
        assertNotNull(koin.get<ProxyApi>())
        assertNotNull(koin.get<BillAnalyzer>())
        assertNotNull(koin.get<AccountRepository>())
        assertNotNull(koin.get<DocumentScanner>())
        assertNotNull(koin.get<SplitVibeDatabase>())
        assertNotNull(koin.get<BillPages>())
        assertNotNull(koin.get<PeopleRepository>())
        assertNotNull(koin.get<BillRepository>())
        assertNotNull(koin.get<ContactNames>())
        assertNotNull(koin.get<FxRepository>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<BillingRepository>())
        assertNotNull(koin.get<ProViewModel>())
        assertNotNull(koin.get<HistoryViewModel> { parametersOf(SavedStateHandle()) })
        assertNotNull(koin.get<HomeViewModel>())
        // A screen hands the view model the state the system keeps for it.
        val split = koin.get<SplitBillViewModel> { parametersOf(SavedStateHandle()) }
        assertNotNull(koin.get<SplitAssistant>())
        assertNotNull(koin.get<VoiceInput>())
        // The assistant changes the bill through the split screen that has it open.
        assertNotNull(koin.get<SplitAssistantViewModel> { parametersOf(split) })

        assertSame("long-lived objects are created once", koin.get<SettingsStore>(), koin.get<SettingsStore>())
        assertSame("the database is opened once", koin.get<SplitVibeDatabase>(), koin.get<SplitVibeDatabase>())
        koin.get<SplitVibeDatabase>().close()
        koin.close()
    }
}
