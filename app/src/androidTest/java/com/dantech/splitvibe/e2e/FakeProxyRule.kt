package com.dantech.splitvibe.e2e

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.di.appModule
import com.dantech.splitvibe.di.spendingModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Runs the app against a [FakeProxy] for one test, on a MockWebServer at the phone's loopback
 * address, which debug builds may reach over plain HTTP. The app's own modules stay as they are but
 * for one definition loaded on top of them: [ProxyApi] reaches the fake rather than the build's
 * address. Objects the app made before, also in an earlier test of the same process, still hold the
 * build's address, so every object of the app's modules is let go first and made again when next
 * asked for. After the test they are let go once more, and made again as the app makes them.
 *
 * The test starts [signedIn] as the session the fake takes, or signed out, and ends signed out. It
 * has to be the outer rule, so that it runs before the activity starts and after it ends.
 */
class FakeProxyRule(private val signedIn: Boolean = false) : ExternalResource() {

    val proxy = FakeProxy()
    private val server = MockWebServer()

    override fun before() {
        server.dispatcher = proxy
        server.start()
        val address = server.url("/").toString()
        makeAppObjectsAgain(module { single { ProxyApi(address, account = get(), appVersion = BuildConfig.VERSION_CODE) } })
        if (signedIn) signIn() else signOut()
    }

    override fun after() {
        signOut()
        makeAppObjectsAgain()
        server.shutdown()
    }
}

/** The modules the app starts with, as SplitVibeApp loads them. */
private val appModules = listOf(appModule, spendingModule)

/**
 * Lets go of every object made from the app's modules, then loads the modules again with
 * [overrides] after them, whose definitions take the place of the app's own.
 */
private fun makeAppObjectsAgain(vararg overrides: Module) {
    unloadKoinModules(appModules)
    loadKoinModules(appModules + overrides)
}

/**
 * Keeps [FakeProxy.SESSION] in the app's own store, where a Google sign-in leaves its session,
 * since Google's sign-in cannot run in a test. Answers kept for whoever was signed in before are
 * dropped, as a sign-in drops them.
 */
private fun signIn() {
    // A build with push asks to show notices once someone signs in, and the system's question would cover the app.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(app, Manifest.permission.POST_NOTIFICATIONS)
    }
    val koin = GlobalContext.get()
    runBlocking { koin.get<ProxyCache>().clear() }
    val session = Session(FakeProxy.SESSION, expiresAt = System.currentTimeMillis() + SESSION_MS)
    koin.get<AccountStore>().saveSession(session, signedInAs = "lan@example.com")
}

/** Forgets the sign-in and what was kept for it on the phone, as signing out in Settings does. */
private fun signOut() {
    val koin = GlobalContext.get()
    koin.get<AccountStore>().clearSession()
    runBlocking { koin.get<ProxyCache>().clear() }
}

/** Long enough for any test, short enough that a session left behind runs out the same day. */
private const val SESSION_MS = 60 * 60 * 1000L
