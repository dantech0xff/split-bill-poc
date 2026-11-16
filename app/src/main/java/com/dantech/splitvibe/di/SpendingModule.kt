package com.dantech.splitvibe.di

import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.export.Exporter
import com.dantech.splitvibe.notifications.Notices
import com.dantech.splitvibe.recurring.RecurringBillWorker
import com.dantech.splitvibe.recurring.RecurringDrafts
import com.dantech.splitvibe.ui.insights.InsightsViewModel
import com.dantech.splitvibe.ui.recurring.RecurringViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * What the user spends: the insights and exports of the finished bills, and the recurring bills
 * that make a draft on their days. It sits next to [appModule], which holds what they build on.
 */
val spendingModule = module {
    single { RecurringBillRepository(database = get(), clock = PhoneClock) }
    single {
        val context = androidContext()
        RecurringDrafts(
            recurring = get(),
            bills = get(),
            notify = { template, drafts -> Notices.showDrafts(context, template.name, drafts) },
            clock = PhoneClock,
        )
    }
    single { Exporter(androidContext()) }

    viewModel {
        InsightsViewModel(bills = get(), settings = get<SettingsStore>().settings, exporter = get(), clock = PhoneClock, savedState = get())
    }
    viewModel {
        val context = androidContext()
        RecurringViewModel(recurring = get(), bills = get(), runDrafts = { RecurringBillWorker.schedule(context) }, clock = PhoneClock)
    }
}

/** The phone's clock, in the zone the phone is in at the moment, which can change while the app runs. */
private object PhoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}
