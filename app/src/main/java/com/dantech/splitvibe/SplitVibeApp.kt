package com.dantech.splitvibe

import android.app.Application
import com.dantech.splitvibe.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class SplitVibeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SplitVibeApp)
            modules(appModule)
        }
    }

    // A device never calls this. Local tests create one application per test in the same process,
    // and each of them has to start from a fresh set of objects.
    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }
}
