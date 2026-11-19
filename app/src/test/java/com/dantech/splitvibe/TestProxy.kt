package com.dantech.splitvibe

import android.content.Context
import android.os.Looper
import com.dantech.splitvibe.data.account.AccountRepository
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.account.GoogleAccount
import com.dantech.splitvibe.data.account.GoogleAccounts
import com.dantech.splitvibe.data.account.GoogleSignInException
import com.dantech.splitvibe.data.account.PhoneData
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.settings.TextCipher
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.GatewayBillAnalyzer
import com.dantech.splitvibe.notifications.DeviceRegistrar
import com.dantech.splitvibe.ocr.OcrResult
import okhttp3.mockwebserver.MockWebServer
import org.robolectric.Shadows.shadowOf

/**
 * The app's real proxy client, pointed at [server] in place of the AI proxy, as a phone that has
 * not signed in yet.
 */
class TestProxy(server: MockWebServer, context: Context, clock: () -> Long = System::currentTimeMillis) {
    val account = AccountStore(context, proxySetUp = true, cipher = PlainCipher, clock = clock)
    val api = ProxyApi(server.url("/").toString(), account, appVersion = APP_VERSION)
    val analyzer = GatewayBillAnalyzer(api, account, canSignIn = true, sellsPro = false)

    /**
     * The account's actions as the app wires them, with [cache] for groups and requests, [phone] for
     * what only the phone keeps, and no push.
     */
    fun accountRepository(google: GoogleAccounts?, cache: ProxyCache, phone: PhoneData, proxy: ProxyApi = api) =
        AccountRepository(proxy, account, google, cache, DeviceRegistrar(proxy, account) { null }, phone)

    companion object {
        const val APP_VERSION = 7
    }
}

/** Stands in for the Android Keystore, which local tests do not have. */
object PlainCipher : TextCipher {
    override fun encrypt(plainText: String): String = "plain:$plainText"

    override fun decrypt(encoded: String): String? = encoded.removePrefix("plain:").takeIf { encoded.startsWith("plain:") }
}

/** For tests that stop before the bill is sent to be read. */
object NeverAsked : BillAnalyzer {
    override suspend fun analyze(pages: List<OcrResult>, instruction: String, localCurrency: String): BillAnalysis =
        error("This test never gets as far as asking the model")
}

/** Google's account picker, as a person who picks Minh's account, or as [failure] when it is set. */
class FakeGoogleAccounts : GoogleAccounts {
    var failure: GoogleSignInException? = null
    var forgotten = false

    override suspend fun pick(activity: Context): GoogleAccount {
        failure?.let { throw it }
        return GoogleAccount(idToken = "google-id-token", email = "minh.tran@example.com")
    }

    override suspend fun forget(context: Context) {
        forgotten = true
    }
}

/**
 * Waits for work that finishes on another thread and reports back on the main thread. Under
 * Robolectric the test runs on the main thread, so what is posted to it runs only when its looper is.
 */
fun awaitOnMain(timeoutMs: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.currentTimeMillis() < deadline) { "Still waiting after $timeoutMs ms" }
        Thread.sleep(10)
    }
}
