package com.dantech.splitvibe.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.contacts.ContactRepository
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ui.settings.SettingsViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
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

        assertNotNull(koin.get<ContactRepository>())
        assertNotNull(koin.get<SettingsStore>())
        assertNotNull(koin.get<BillImageLoader>())
        assertNotNull(koin.get<BillTextRecognizer>())
        assertNotNull(koin.get<AccountStore>())
        assertNotNull(koin.get<ProxyApi>())
        assertNotNull(koin.get<BillAnalyzer>())
        assertNotNull(koin.get<AccountRepository>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<SplitBillViewModel>())

        assertSame("long-lived objects are created once", koin.get<SettingsStore>(), koin.get<SettingsStore>())
        koin.close()
    }
}
