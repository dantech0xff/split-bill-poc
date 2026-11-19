package com.dantech.splitvibe.ui.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.GroupFixtures.AN
import com.dantech.splitvibe.GroupFixtures.GROUP_ID
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.account.Session
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.inEnglish
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JoinGroupViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val storage = TestStorage(context)
    private val settings = MutableStateFlow(AppSettings("THB"))
    private lateinit var groups: GroupRepository
    private lateinit var proxy: TestProxy

    private val invitation = """{"group":{"id":"$GROUP_ID","name":"Chiang Mai trip","currency":{"code":"THB","fractionDigits":2}},""" +
        """"placeholders":[{"id":"$AN","name":"An"}],"alreadyMember":false}"""

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        groups = GroupRepository(proxy.api, ProxyCache(storage.database.proxyCache()), proxy.account)
    }

    @After
    fun tearDown() {
        server.shutdown()
        storage.close()
    }

    @Test
    fun `joining as a new member opens the group`() {
        server.enqueue(json(invitation))
        server.enqueue(json(GroupFixtures.groupState()))
        server.enqueue(json(GroupFixtures.groupList()))
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.invitation != null }

        viewModel.join("Dana")
        awaitOnMain { viewModel.state.value.joinedGroupId != null }

        assertEquals(GROUP_ID, viewModel.state.value.joinedGroupId)
        val open = server.takeRequest()
        assertEquals("/v1/invites/open", open.path)
        assertEquals("token-7", Json.parseToJsonElement(open.body.readUtf8()).jsonObject["token"]!!.jsonPrimitive.content)
        val join = server.takeRequest()
        assertEquals("/v1/invites/join", join.path)
        val joined = Json.parseToJsonElement(join.body.readUtf8()).jsonObject
        assertEquals("Dana", joined["name"]!!.jsonPrimitive.content)
        assertEquals("token-7", joined["token"]!!.jsonPrimitive.content)
        assertEquals("Dana", settings.value.myName)
    }

    @Test
    fun `asking for a kept place waits for the owner`() {
        server.enqueue(json(invitation))
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"pending"}"""))
        server.enqueue(json(GroupFixtures.groupList(status = "pending")))
        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.invitation != null }

        viewModel.claim(AN, "An")
        awaitOnMain { viewModel.state.value.waiting }

        server.takeRequest()
        val claim = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(AN, claim["claimMemberId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an invite opened before signing in loads once someone signs in`() {
        proxy.account.clearSession()
        val viewModel = viewModel()
        assertEquals(0, server.requestCount)

        server.enqueue(json(invitation))
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))

        awaitOnMain { viewModel.state.value.invitation != null }
        assertEquals("/v1/invites/open", server.takeRequest().path)
    }

    @Test
    fun `a link that ran out says so`() {
        server.enqueue(MockResponse().setResponseCode(410).setBody("""{"error":"gone"}"""))

        val viewModel = viewModel()
        awaitOnMain { viewModel.state.value.message != null }

        assertTrue(viewModel.state.value.message.inEnglish()!!.contains("run out"))
    }

    private fun viewModel() = JoinGroupViewModel("token-7", groups, proxy.account.account, settings) {
        settings.value =
            settings.value.copy(myName = it)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
