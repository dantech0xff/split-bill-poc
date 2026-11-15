package com.dantech.splitvibe.ui.claim

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.NeverAsked
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.ShareFixtures
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.domain.BillEditor
import com.dantech.splitvibe.domain.SplitCalculator
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A bill that is on a link stays on it as it is, with the real split screen as the one writer of
 * the bill: every change it saves that the page shows goes to the page, and nothing it saves after
 * the amounts were sent takes them off again.
 */
@RunWith(AndroidJUnit4::class)
class BillLinkSyncTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val server = MockWebServer()
    private val store = ViewModelStore()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private lateinit var proxy: TestProxy

    /** What a copy of the bill sent to the link is answered with. */
    @Volatile
    private var putAnswer = json(ShareFixtures.owned())

    private val noOcr = object : BillTextRecognizer {
        override suspend fun recognize(image: Bitmap): OcrResult = error("Nothing is read in this test")
    }

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path.orEmpty()
                return when {
                    request.method == "PUT" -> putAnswer

                    path.endsWith(
                        "/lock",
                    ) -> json(ShareFixtures.share(ShareFixtures.view(ShareView.LOCKING, revision = 3, claims = ShareFixtures.CLAIMS)))

                    path.endsWith(
                        "/finalize",
                    ) -> json(ShareFixtures.share(ShareFixtures.view(ShareView.LOCKED, revision = 9, claims = ShareFixtures.CLAIMS)))

                    else -> json(ShareFixtures.owned(ShareFixtures.view(revision = 2, claims = ShareFixtures.CLAIMS)))
                }
            }
        }
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
    }

    @After
    fun tearDown() {
        store.clear()
        server.shutdown()
        storage.close()
    }

    private val shares by lazy { ShareRepository(proxy.api) }
    private val settings = MutableStateFlow(AppSettings(localCurrency = "VND", myName = "Dana"))

    private fun openLinkedBill(): SplitBillViewModel {
        val linked = ShareFixtures.saved.copy(revision = 0, share = ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL))
        val stored = runBlocking { storage.bills.save(linked) }
        val viewModel = testSplitViewModel(context, storage, proxy.account.account, noOcr, NeverAsked, settings = settings, shares = shares)
        viewModel.open(stored.id)
        awaitOnMain { viewModel.state.value.result != null }
        return viewModel
    }

    private fun claimScreen(host: SplitBillViewModel): ClaimViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer {
                ClaimViewModel(host, shares, PaymentMethodRepository(storage.database), settings, proxy.account.account)
            }
        },
    )[ClaimViewModel::class.java]

    private fun puts() = requests.filter { it.method == "PUT" }

    @Test
    fun `a change to a bill on a link shows on its page, as the revision it was saved at`() {
        val viewModel = openLinkedBill()
        val opened = runBlocking { storage.bills.get(ShareFixtures.saved.id) }!!.revision

        viewModel.renamePerson("p1", "Minh Nguyen")

        // The bill then records what its page shows, which changes nothing on the page.
        awaitOnMain { runBlocking { storage.bills.get(ShareFixtures.saved.id) }?.share?.shown != null }
        val body = Json.parseToJsonElement(puts().single().body.readUtf8()).jsonObject
        assertEquals("/v1/shares/${ShareFixtures.SHARE_ID}", puts().single().path)
        assertEquals(opened + 1, body["billRevision"]!!.jsonPrimitive.int)
        val names = body["bill"]!!.jsonObject["participants"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertEquals(listOf("Dana", "Minh Nguyen", "Lan"), names)
    }

    @Test
    fun `an item taken off the bill while its link is open goes off the page`() {
        val viewModel = openLinkedBill()

        viewModel.editBill { BillEditor.withoutItem(it, "i2") }

        awaitOnMain { puts().isNotEmpty() }
        val items = Json.parseToJsonElement(puts().last().body.readUtf8()).jsonObject["bill"]!!.jsonObject["items"]!!.jsonArray
        assertEquals(listOf("i1", "i3"), items.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun `a link that no longer works is forgotten by the bill`() {
        putAnswer = MockResponse().setResponseCode(410).setHeader("Content-Type", "application/json").setBody("""{"error":"gone"}""")
        val viewModel = openLinkedBill()

        viewModel.renamePerson("p1", "Minh Nguyen")

        awaitOnMain { runBlocking { storage.bills.get(ShareFixtures.saved.id) }?.share == null }
        assertEquals(null, viewModel.state.value.result?.saved?.share)
    }

    @Test
    fun `a change that takes the settled amounts off the page is told`() {
        putAnswer = json(ShareFixtures.owned(ShareFixtures.view(ShareView.LOCKING, revision = 6)))
        val viewModel = openLinkedBill()
        viewModel.updateLink(ShareFixtures.SHARE_ID) { it.copy(settled = true, shown = "as the amounts were sent") }

        viewModel.renamePerson("p1", "Minh Nguyen")

        awaitOnMain { viewModel.state.value.notice != null }
        awaitOnMain { runBlocking { storage.bills.get(ShareFixtures.saved.id) }?.share?.settled == false }
    }

    @Test
    fun `what everyone owes stays on the page, and is what the phone saved and the calculator gives`() {
        val viewModel = openLinkedBill()
        val claims = claimScreen(viewModel)
        awaitOnMain { claims.state.value.share?.claims?.size == 2 }

        claims.lock()
        awaitOnMain { claims.state.value.share?.status == ShareView.LOCKING && !claims.state.value.working }
        claims.send()
        awaitOnMain { runBlocking { storage.bills.get(ShareFixtures.saved.id) }?.share?.settled == true }

        val sent = requests.indexOfFirst { it.path.orEmpty().endsWith("/finalize") }
        assertFalse("No copy of the bill follows the amounts", requests.drop(sent + 1).any { it.method == "PUT" })
        val stored = checkNotNull(runBlocking { storage.bills.get(ShareFixtures.saved.id) })
        assertEquals(BillStatus.FINAL, stored.status)
        val settled = Json.parseToJsonElement(requests[sent].body.readUtf8()).jsonObject
        val onPage = settled["amounts"]!!.jsonObject.mapValues { (_, amount) ->
            amount.jsonObject["amountMinor"]!!.jsonPrimitive.content.toLong()
        }
        val onPhone = stored.shares.associate { it.personId to it.totalMinor }
        val calculated = SplitCalculator.split(stored.bill, stored.plan.participantIds, stored.plan.setup).shares.associate {
            it.personId to
                it.totalMinor
        }
        assertEquals(calculated, onPage)
        assertEquals(onPhone, onPage)
        val stranger = checkNotNull(stored.plan.participants.firstOrNull { it.name == "Stranger" }) { "The stranger is on the bill" }
        // The page says who each claim is on the bill: Minh as matched, the stranger as added.
        val matches = settled["matches"]!!.jsonObject.mapValues { (_, participant) -> participant.jsonPrimitive.content }
        assertEquals(mapOf("claim-minh" to "p1", "claim-x" to stranger.id), matches)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
