package com.dantech.splitvibe

import android.app.Application
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.DeletionStore
import com.dantech.splitvibe.di.appModule
import com.dantech.splitvibe.di.spendingModule
import com.dantech.splitvibe.notifications.DeviceRegistrar
import com.dantech.splitvibe.notifications.Push
import com.dantech.splitvibe.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class SplitVibeApp : Application() {

    /** Work that outlives every screen, such as telling the proxy where push notices go. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Before anything reads a text: the forms of plurals come from resources made by this context.
        UiText.start(this)
        val koin = startKoin {
            androidContext(this@SplitVibeApp)
            modules(appModule, spendingModule)
        }.koin
        // A deletion of the account that the app was ended in the middle of is finished first, so that
        // a session the proxy deleted is gone before anything sends it.
        if (koin.get<DeletionStore>().pending != null) koin.get<AccountRepository>().finishInterruptedDeletion()
        Push.start(this)
        // Whenever someone signs in, the proxy learns where to send their push notices.
        if (Push.isConfigured) {
            val registrar = koin.get<DeviceRegistrar>()
            koin.get<AccountStore>().account
                .map { it.session != null }
                .distinctUntilChanged()
                .filter { it }
                .onEach { registrar.register() }
                .launchIn(scope)
        }
    }

    // A device never calls this. Local tests create one application per test in the same process,
    // and each of them has to start from a fresh set of objects.
    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }
}
