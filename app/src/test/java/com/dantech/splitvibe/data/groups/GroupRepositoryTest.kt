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

        groups.recordPaidBack(GROUP_ID, LAN, 126_250)

        val request = server.takeRequest()
        assertEquals("/v1/groups/$GROUP_ID/settlements", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(LAN, body["fromMemberId"]!!.jsonPrimitive.content)
        assertEquals("126250", body["amountMinor"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("id"))
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
