package com.dantech.splitvibe

import android.content.Context
import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.settings.TextCipher
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.GatewayBillAnalyzer
import com.dantech.splitvibe.ocr.OcrResult
import okhttp3.mockwebserver.MockWebServer

/**
 * The app's real proxy client, pointed at [server] in place of the AI proxy, as a phone that has
 * not signed in yet.
 */
class TestProxy(server: MockWebServer, context: Context, clock: () -> Long = System::currentTimeMillis) {
    val account = AccountStore(context, proxySetUp = true, cipher = PlainCipher, clock = clock)
    val api = ProxyApi(server.url("/").toString(), account, appVersion = APP_VERSION)
    val analyzer = GatewayBillAnalyzer(api, account)

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
    override suspend fun analyze(ocr: OcrResult, instruction: String, localCurrency: String): BillAnalysis =
        error("This test never gets as far as asking the model")
}
