package com.dantech.splitvibe.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.export.Exporter
import com.dantech.splitvibe.recurring.RecurringDrafts
import com.dantech.splitvibe.ui.insights.InsightsViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication

/** Every object of the insights, the exports and the recurring bills can be created, as [AppModuleTest] checks for the rest. */
@RunWith(AndroidJUnit4::class)
class SpendingModuleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every object the insights, exports and recurring bills ask for can be created`() {
        val koin = koinApplication {
            androidContext(context)
            modules(appModule, spendingModule)
        }.koin

        assertNotNull(koin.get<RecurringBillRepository>())
        assertNotNull(koin.get<Exporter>())
        // The worker asks for this one, through Koin, when WorkManager runs it.
        assertNotNull(koin.get<RecurringDrafts>())
        // A screen hands the view model the state the system keeps for it.
        assertNotNull(koin.get<InsightsViewModel> { parametersOf(SavedStateHandle()) })

        assertSame("the recurring bills are kept by one object", koin.get<RecurringDrafts>(), koin.get<RecurringDrafts>())
        koin.get<SplitVibeDatabase>().close()
        koin.close()
    }
}
