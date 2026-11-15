package com.dantech.splitvibe.ui.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.GroupFixtures.GROUP_ID
import com.dantech.splitvibe.GroupFixtures.LAN
import com.dantech.splitvibe.GroupFixtures.ME
import com.dantech.splitvibe.GroupFixtures.MINH
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.SplitPlan
import java.io.File
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddToGroupViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private val received = mutableListOf<RecordedRequest>()
    private lateinit var viewModel: AddToGroupViewModel

    private val usd = BillCurrency("USD", 2)

    /** A dinner in dollars that the user paid, shared with Minh and a Lan typed in by hand. */
    private val dinner = SavedBill(
        id = "bill-9",
        createdAt = 1,
        updatedAt = 1,
        bill = Bill("Dinner", "2026-11-14", usd, emptyList(), emptyList(), printedTotalMinor = 1_000),
        plan = SplitPlan(
            participants = listOf(Participant.ME, Participant("p1", "Minh", personId = "local-person-1"), Participant("p2", "lan")),
            payments = listOf(Payment(ME_ID, 1_000)),
        ),
        ocrDocument = "TEXT READ FROM THE PHOTO",
        instruction = "Split with Minh and Lan",
        pages = listOf("page-1.jpg"),
    )

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                received += request
                val body = when {
                    request.path == "/v1/groups" -> GroupFixtures.groupList()
                    request.path == "/v1/fx-rates" -> """{"base":"EUR","source":"Frankfurter","fetchedAt":1,"rates":""" +
                        """{"EUR":{"rate":1,"date":"2026-11-14"},"USD":{"rate":1.25,"date":"2026-11-14"},"THB":{"rate":44.375,"date":"2026-11-14"}}}"""
                    request.method == "PUT" -> GroupFixtures.groupState(extraBills = "," + GroupFixtures.bill("bill-9", ME, "Dinner", 35_500, "{}", "{}"))
                    else -> GroupFixtures.groupState()
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        val proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        val groups = GroupRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account)
        viewModel = AddToGroupViewModel(groups, FxRepository(proxy.api, File(context.cacheDir, "fx-add-to-group.json")))
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `people are matched by name, the total is converted once and the group gets no photos or read text`() {
        viewModel.choose(dinner, GROUP_ID)
        awaitOnMain { viewModel.state.value.group != null && !viewModel.state.value.working }

        // You are your own member, Minh and lan are matched by name.
        assertEquals(mapOf("me" to ME, "p1" to MINH, "p2" to LAN), viewModel.state.value.memberOf)
        // 1.25 USD and 44.375 THB to the euro: 35.5 baht to the dollar.
        assertEquals(0, BigDecimal("35.5").compareTo(viewModel.state.value.rate!!.rate))
        val amounts = checkNotNull(viewModel.amounts(dinner))
        assertEquals(35_500L, amounts.totalMinor)
        assertEquals(35_500L, amounts.shares.values.sum())
        assertEquals(mapOf(ME to 35_500L), amounts.paid)

        viewModel.submit(dinner)
        awaitOnMain { viewModel.state.value.done != null }

        val put = received.single { it.method == "PUT" }
        assertEquals("/v1/groups/$GROUP_ID/bills/bill-9", put.path)
        val body = Json.parseToJsonElement(put.body.readUtf8()).jsonObject
        assertEquals("0", body["expectedRevision"]!!.jsonPrimitive.content)
        assertEquals("35500", body["totalMinor"]!!.jsonPrimitive.content)
        assertEquals("35.5", body["conversion"]!!.jsonObject["rate"]!!.jsonPrimitive.content)
        val document = body["document"].toString()
        assertFalse(document, document.contains("TEXT READ FROM THE PHOTO"))
        assertFalse(document, document.contains("page-1.jpg"))
        assertFalse(document, document.contains("Split with Minh and Lan"))
        assertFalse(document, document.contains("local-person-1"))
        assertEquals(GroupLink(GROUP_ID, "Chiang Mai trip", 1, mapOf("me" to ME, "p1" to MINH, "p2" to LAN)), viewModel.state.value.done)
    }

    @Test
    fun `a bill already in the group is sent as its next revision, with the members chosen before`() {
        val linked = dinner.copy(group = GroupLink(GROUP_ID, "Chiang Mai trip", 4, mapOf("me" to ME, "p1" to LAN, "p2" to MINH)))

        viewModel.start(linked)
        awaitOnMain { viewModel.state.value.group != null && !viewModel.state.value.working }
        assertEquals(mapOf("me" to ME, "p1" to LAN, "p2" to MINH), viewModel.state.value.memberOf)

        viewModel.submit(linked)
        awaitOnMain { viewModel.state.value.done != null }

        val body = Json.parseToJsonElement(received.single { it.method == "PUT" }.body.readUtf8()).jsonObject
        assertEquals("4", body["expectedRevision"]!!.jsonPrimitive.content)
    }

    @Test
    fun `someone nobody in the group is called stays unmatched until the user picks`() {
        val withStranger = dinner.copy(plan = dinner.plan.copy(participants = dinner.plan.participants + Participant("p3", "Zed")))

        viewModel.choose(withStranger, GROUP_ID)
        awaitOnMain { viewModel.state.value.group != null && !viewModel.state.value.working }

        assertNull(viewModel.amounts(withStranger))
        viewModel.match("p3", MINH)
        assertEquals(35_500L, viewModel.amounts(withStranger)?.totalMinor)
    }
}
