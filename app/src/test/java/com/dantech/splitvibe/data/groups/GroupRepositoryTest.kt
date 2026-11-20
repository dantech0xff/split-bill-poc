package com.dantech.splitvibe.data.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.GroupFixtures.AN
import com.dantech.splitvibe.GroupFixtures.GROUP_ID
import com.dantech.splitvibe.GroupFixtures.LAN
import com.dantech.splitvibe.GroupFixtures.ME
import com.dantech.splitvibe.GroupFixtures.MINH
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Transfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private lateinit var proxy: TestProxy
    private lateinit var groups: GroupRepository

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE), signedInAs = "dana@example.com")
        groups = GroupRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `a group's balances and suggested transfers come from its bills and payments back`() = runBlocking {
        server.enqueue(json(GroupFixtures.groupState()))

        groups.refresh(GROUP_ID)

        val state = checkNotNull(groups.group(GROUP_ID).first())
        assertEquals(mapOf(ME to 3_543L, MINH to 150_628L, LAN to -126_250L, AN to -27_921L), state.balances)
        assertEquals(
            listOf(Transfer(LAN, MINH, 126_250), Transfer(AN, MINH, 24_378), Transfer(AN, ME, 3_543)),
            state.suggestedTransfers,
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/groups/$GROUP_ID", request.path)
        assertEquals("Bearer signed-session", request.getHeader("Authorization"))
    }

    @Test
    fun `a bill that does not add up is left out whole, so the balances still add up to zero`() = runBlocking {
        val broken = "," + GroupFixtures.bill("b6", MINH, "Broken", 1_000, """{"$ME":1000}""", """{"$MINH":999}""")
        val stranger = "," + GroupFixtures.bill("b7", MINH, "Stranger", 1_000, """{"m-nobody":1000}""", """{"$MINH":1000}""")
        server.enqueue(json(GroupFixtures.groupState(extraBills = broken + stranger)))

        groups.refresh(GROUP_ID)

        val state = checkNotNull(groups.group(GROUP_ID).first())
        assertEquals(listOf("b1", "b2", "b3", "b4", "b5"), state.countedBills.map { it.id })
        assertEquals(0L, state.balances!!.values.sum())
    }

    @Test
    fun `creating a group sends its name, currency and the creator's name, then lists the groups again`() = runBlocking {
        server.enqueue(json(GroupFixtures.groupState()))
        server.enqueue(json(GroupFixtures.groupList()))

        val state = groups.create(" Chiang Mai trip ", BillCurrency("THB", 2), "Dana ")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Chiang Mai trip", body["name"]!!.jsonPrimitive.content)
        assertEquals("Dana", body["memberName"]!!.jsonPrimitive.content)
        assertEquals("""{"code":"THB","fractionDigits":2}""", body["currency"].toString())
        assertEquals(GROUP_ID, state.group.id)
        assertEquals(listOf(GROUP_ID), groups.groups.first()?.map { it.id })
    }

    @Test
    fun `a group the user can no longer read is forgotten`() = runBlocking {
        server.enqueue(json(GroupFixtures.groupState()))
        groups.refresh(GROUP_ID)
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))
        server.enqueue(json("""{"groups":[]}"""))

        try {
            groups.refresh(GROUP_ID)
            fail("A refused read must be reported")
        } catch (e: ProxyException) {
            assertEquals("forbidden", e.code)
        }

        assertNull(groups.group(GROUP_ID).first())
        assertEquals(emptyList<GroupSummary>(), groups.groups.first())
    }

    @Test
    fun `a session the proxy refuses turns into a sign-in to renew`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"session"}"""))

        try {
            groups.refreshGroups()
            fail("A refused session must be reported")
        } catch (e: ProxyException) {
            assertEquals(ProxyApi.SESSION_REFUSED, e.code)
        }

        assertTrue(proxy.account.account.value.signInExpired)
    }

    @Test
    fun `nothing is asked of the proxy before anyone signs in`() = runBlocking {
        proxy.account.clearSession()

        try {
            groups.refreshGroups()
            fail("Groups need a sign-in")
        } catch (e: ProxyException) {
            assertEquals(ProxyApi.SESSION_REFUSED, e.code)
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `paying back is recorded by the member who got the money`() = runBlocking {
        server.enqueue(json(GroupFixtures.groupState()))

        groups.recordPaidBack(GROUP_ID, LAN, 126_250, "6f1c2d3e-0000-4000-8000-00000000000a")

        val request = server.takeRequest()
        assertEquals("/v1/groups/$GROUP_ID/settlements", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(LAN, body["fromMemberId"]!!.jsonPrimitive.content)
        assertEquals("126250", body["amountMinor"]!!.jsonPrimitive.content)
        // The id the screen made for this payment back, so a retry records it only once.
        assertEquals("6f1c2d3e-0000-4000-8000-00000000000a", body["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the phone keeps a group's bills by name and amounts, not their whole documents`() = runBlocking {
        val big = "x".repeat(50_000)
        val bulky = "," + GroupFixtures.bill("b6", MINH, "Big", 1_000, """{"$ME":1000}""", """{"$MINH":1000}""")
            .replace(""""document":{"merchant":"Big"}""", """"document":{"merchant":"Big","notes":"$big"}""")
        server.enqueue(json(GroupFixtures.groupState(extraBills = bulky)))

        groups.refresh(GROUP_ID)

        val kept = checkNotNull(groups.group(GROUP_ID).first())
        assertEquals("Big", kept.bills.single { it.id == "b6" }.title)
        assertTrue(ProxyCache.json.encodeToString(GroupState.serializer(), kept).length < 10_000)
    }

    @Test
    fun `the latest group is the one whose state the phone kept last, and none is left once the cache is emptied`() = runBlocking {
        var now = 1_000L
        val cache = ProxyCache(storage.database.proxyCache()) { now }
        val repository = GroupRepository(proxy.api, cache, proxy.account)
        assertNull("nothing kept yet", repository.latestGroup.first())

        server.enqueue(json(GroupFixtures.groupState()))
        repository.refresh(GROUP_ID)
        now = 2_000
        server.enqueue(json(flat()))
        repository.refresh(FLAT_ID)
        assertEquals("the group refreshed last", "Flat 3B", repository.latestGroup.first()?.group?.name)

        // A change answered later makes its group the latest again; the list of groups is no group's state.
        now = 3_000
        server.enqueue(json(GroupFixtures.groupState()))
        repository.rename(GROUP_ID, "Chiang Mai trip")
        now = 4_000
        server.enqueue(json(GroupFixtures.groupList()))
        repository.refreshGroups()
        assertEquals("the group changed last", GROUP_ID, repository.latestGroup.first()?.group?.id)

        // A group the user left goes with its state, and the one kept before it is the latest again.
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json(GroupFixtures.groupList()))
        repository.leave(GROUP_ID)
        assertEquals("the group kept before the one left", FLAT_ID, repository.latestGroup.first()?.group?.id)

        // As on every sign-in, sign-out and account deletion.
        cache.clear()
        assertNull("nothing of the account that was signed in", repository.latestGroup.first())
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    /** A second group: the flat the user shares, kept in baht like the trip. */
    private fun flat() = GroupFixtures.groupState().replace(GROUP_ID, FLAT_ID).replace("Chiang Mai trip", "Flat 3B")

    private companion object {
        const val FLAT_ID = "8f9a2c1e-0000-4000-8000-000000000002"
    }
}
