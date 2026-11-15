package com.dantech.splitvibe.ui.claim

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.NeverAsked
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.ShareFixtures
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrResult
import com.dantech.splitvibe.testSplitViewModel
import com.dantech.splitvibe.ui.split.SplitBillViewModel
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A bill that is on a link stays on it as it is: every change the split screen saves goes to the page too. */
@RunWith(AndroidJUnit4::class)
class BillLinkSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private val puts: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var answer = MockResponse().setHeader("Content-Type", "application/json").setBody(ShareFixtures.owned())

    private val noOcr = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = error("Nothing is read in this test")
    }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "PUT") puts += request
                return answer
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    private fun openLinkedBill(): SplitBillViewModel {
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        val linked = ShareFixtures.saved.copy(revision = 0, share = ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL))
        val stored = runBlocking { storage.bills.save(linked) }
        val viewModel = testSplitViewModel(
            context,
            storage,
            proxy.account.account,
            noOcr,
            NeverAsked,
            settings = MutableStateFlow(AppSettings(localCurrency = "VND", myName = "Dana")),
            shares = ShareRepository(proxy.api),
        )
        viewModel.open(stored.id)
        awaitOnMain { viewModel.state.value.result != null }
        return viewModel
    }

    @Test
    fun `a change to a bill on a link shows on its page, as the revision it was saved at`() {
        val viewModel = openLinkedBill()

        viewModel.renamePerson("p1", "Minh Nguyen")

        awaitOnMain { puts.isNotEmpty() }
        val body = Json.parseToJsonElement(puts.last().body.readUtf8()).jsonObject
        assertEquals("/v1/shares/${ShareFixtures.SHARE_ID}", puts.last().path)
        val stored = runBlocking { storage.bills.get(ShareFixtures.saved.id) }!!
        assertEquals(stored.revision, body["billRevision"]!!.jsonPrimitive.int)
        val names = body["bill"]!!.jsonObject["participants"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("Dana", "Minh Nguyen", "Lan"), names)
    }

    @Test
    fun `a link that no longer works is forgotten by the bill`() {
        answer = MockResponse().setResponseCode(410).setHeader("Content-Type", "application/json").setBody("""{"error":"gone"}""")
        val viewModel = openLinkedBill()

        viewModel.renamePerson("p1", "Minh Nguyen")

        awaitOnMain { runBlocking { storage.bills.get(ShareFixtures.saved.id) }?.share == null }
        assertEquals(null, viewModel.state.value.result?.saved?.share)
    }
}
