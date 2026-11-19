package com.dantech.splitvibe.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.TestProxy
import com.dantech.splitvibe.data.account.Session
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoticesAndDevicesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var proxy: TestProxy

    @Before
    fun setUp() {
        server.start()
        proxy = TestProxy(server, context)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `every notice has fixed words of the app's own, and an unknown kind shows nothing`() {
        listOf("request", "reported", "confirmed", "reminder", "claim", "unpaid", "unconfirmed").forEach { kind ->
            val (title, text) = checkNotNull(Notices.textOf(kind))
            assertTrue(kind, context.getString(title).isNotBlank() && context.getString(text).isNotBlank())
        }
        assertNull(Notices.textOf("anything else"))
        assertNull(Notices.textOf(null))
    }

    @Test
    fun `the device is registered under the install's id once someone is signed in`() = runBlocking {
        val registrar = DeviceRegistrar(proxy.api, proxy.account) { "fcm-token-1" }

        registrar.register()
        assertEquals(0, server.requestCount)

        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(MockResponse().setResponseCode(204))
        registrar.register()

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/devices/${proxy.account.account.value.installId}", request.path)
        assertEquals("""{"token":"fcm-token-1"}""", request.body.readUtf8())
    }

    @Test
    fun `a device that cannot be registered now changes nothing else`() = runBlocking {
        proxy.account.saveSession(Session("signed-session", GroupFixtures.FAR_FUTURE))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"not_configured"}"""))

        DeviceRegistrar(proxy.api, proxy.account) { "fcm-token-1" }.register()

        assertEquals("signed-session", proxy.account.account.value.session?.token)
    }
}
