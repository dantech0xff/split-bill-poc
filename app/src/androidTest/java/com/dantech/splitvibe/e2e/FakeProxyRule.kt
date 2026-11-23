package com.dantech.splitvibe.e2e

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.splitvibe.BuildConfig
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.di.appModule
import com.dantech.splitvibe.di.spendingModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.rules.ExternalResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Runs the app against a [FakeProxy] for one test, on a MockWebServer at the phone's loopback
 * address, which debug builds may reach over plain HTTP. The app's own modules stay as they are but
 * for two definitions loaded on top of them: [ProxyApi] reaches the fake rather than the build's
 * address, and [AccountStore] takes the proxy as set up. Objects the app made before, also in an earlier test of the same process, still hold the
 * build's address, so every object of the app's modules is let go first and made again when next
 * asked for. After the test they are let go once more, and made again as the app makes them.
 *
 * The test starts [signedIn] as the session the fake takes, or signed out, and ends signed out. It
 * starts and ends without the consent to the AI, so a journey that reads a bill first answers the
 * consent screen. It has to be the outer rule, so that it runs before the activity starts and after
 * it ends.
 */
class FakeProxyRule(private val signedIn: Boolean = false) : ExternalResource() {

    val proxy = FakeProxy()
    private val server = MockWebServer()

    override fun before() {
        try {
            server.dispatcher = proxy
            server.start()
            val address = server.url("/").toString()
            makeAppObjectsAgain(
                module {
                    // The build may know no proxy, and then its store would report AI reading as not set up.
                    single { AccountStore(androidContext(), proxySetUp = true) }
                    single { ProxyApi(address, account = get(), appVersion = BuildConfig.VERSION_CODE) }
                },
            )
            if (signedIn) signIn() else signOut()
            withoutAiConsent()
        } catch (failure: Throwable) {
            // JUnit runs after() only once before() has passed, so a setup that failed part-way is
            // undone here; otherwise the fake's address and the server would stay for later tests.
            undo()?.let(failure::addSuppressed)
            throw failure
        }
    }

    override fun after() {
        undo()?.let { throw it }
    }

    /**
     * Signs out, loads the app's modules again as the app loads them, and stops the server, each step
     * whatever became of the one before. Returns the first failure, with any later ones suppressed.
     */
    private fun undo(): Throwable? {
        var first: Throwable? = null
        for (step in listOf(::signOut, ::withoutAiConsent, { makeAppObjectsAgain() }, server::shutdown)) {
            try {
                step()
            } catch (failure: Throwable) {
                first?.addSuppressed(failure) ?: run { first = failure }
            }
        }
        return first
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

/** Takes the consent to the AI back, as Settings does, so the consent screen shows before the next bill read. */
private fun withoutAiConsent() = GlobalContext.get().get<SettingsStore>().allowAi(false)

/** Long enough for any test, short enough that a session left behind runs out the same day. */
private const val SESSION_MS = 60 * 60 * 1000L
