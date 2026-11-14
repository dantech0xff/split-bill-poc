package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.PlainCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = 1_000_000L

    private fun store(proxySetUp: Boolean = true) = AccountStore(context, proxySetUp, cipher = PlainCipher, clock = { now })

    @Test
    fun `an install makes its id once and keeps it`() {
        val first = store().account.value.installId

        assertTrue(Regex("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$").matches(first))
        assertEquals(first, store().account.value.installId)
    }

    @Test
    fun `a session is kept through the cipher and read back by the next start of the app`() {
        store().saveSession(Session("signed-session", expiresAt = now + 60_000))

        val stored = context.getSharedPreferences("account", Context.MODE_PRIVATE).all.values.map { it.toString() }
        assertFalse("the session is only written encrypted", "signed-session" in stored)
        assertEquals(Session("signed-session", now + 60_000), store().currentSession())
    }

    @Test
    fun `a session that has run out is not sent, and is forgotten`() {
        val store = store()
        store.saveSession(Session("signed-session", expiresAt = now + 60_000))

        now += 60_000

        assertNull(store.currentSession())
        assertNull(store.account.value.session)
        assertNull(store().account.value.session)
    }

    @Test
    fun `signing out forgets the session and the calls left that went with it`() {
        val store = store()
        store.saveSession(Session("signed-session", expiresAt = now + 60_000))
        store.onCallsLeft(CallsLeft(limit = 10, used = 4, remaining = 6, period = "2026-11", resetsAt = 1_796_083_200_000))

        store.clearSession()

        assertNull(store.account.value.session)
        assertNull(store.account.value.callsLeft)
        assertNull(store().currentSession())
    }

    @Test
    fun `AI is off when the proxy says so, and never set up in a build without its address`() {
        val store = store()
        assertEquals(AiStatus.AVAILABLE, store.account.value.ai)
        store.onAiEnabled(false)
        assertEquals(AiStatus.OFF, store.account.value.ai)

        val withoutProxy = store(proxySetUp = false)
        withoutProxy.onAiEnabled(true)
        assertEquals(AiStatus.NOT_SET_UP, withoutProxy.account.value.ai)
    }
}
