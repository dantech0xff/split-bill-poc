package com.dantech.splitvibe.data.account

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.PlainCipher
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private var now = 1_000_000L

    private fun store(proxySetUp: Boolean = true) = AccountStore(context, proxySetUp, cipher = PlainCipher, clock = { now })

    private fun storedValues(): List<String> = preferences.all.values.map { it.toString() }

    @Test
    fun `an install makes its id once and keeps it`() {
        val first = store().account.value.installId

        assertTrue(Regex("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$").matches(first))
        assertEquals(first, store().account.value.installId)
    }

    @Test
    fun `a session and the account's address are kept through the cipher and read back by the next start of the app`() {
        store().saveSession(Session("signed-session", expiresAt = now + 60_000), signedInAs = "minh.tran@example.com")

        assertFalse("the session is only written encrypted", "signed-session" in storedValues())
        assertFalse("the address is only written encrypted", "minh.tran@example.com" in storedValues())
        val restarted = store()
        assertEquals(Session("signed-session", now + 60_000), restarted.currentSession())
        assertEquals("minh.tran@example.com", restarted.account.value.signedInAs)
    }

    @Test
    fun `a session that has run out is not sent, and the person stays someone who has to sign in again`() {
        val store = store()
        store.saveSession(Session("signed-session", expiresAt = now + 60_000), signedInAs = "minh.tran@example.com")

        now += 60_000

        assertNull(store.currentSession())
        assertTrue(store.account.value.signInExpired)
        assertEquals("minh.tran@example.com", store.account.value.signedInAs)
        val restarted = store()
        assertNull(restarted.account.value.session)
        assertTrue("the next start of the app still knows", restarted.account.value.signInExpired)
    }

    @Test
    fun `a session that ran out while the app was closed is found at the next start`() {
        store().saveSession(Session("signed-session", expiresAt = now + 60_000), signedInAs = "minh.tran@example.com")

        now += 60_000
        val restarted = store()

        assertNull(restarted.currentSession())
        assertTrue(restarted.account.value.signInExpired)
        assertEquals("minh.tran@example.com", restarted.account.value.signedInAs)
    }

    @Test
    fun `a session the proxy refused is kept as a sign-in that has run out`() {
        val store = store()
        store.saveSession(Session("signed-session", expiresAt = now + 60_000), signedInAs = "minh.tran@example.com")
        store.onCallsLeft(CallsLeft(BigDecimal(10), BigDecimal("4.2"), BigDecimal("5.8"), period = "2026-11", resetsAt = 1_796_083_200_000))

        store.expireSession()

        assertNull(store.currentSession())
        assertTrue(store.account.value.signInExpired)
        assertNull("the account's count no longer applies", store.account.value.callsLeft)
        assertFalse("the refused session is not kept", "plain:signed-session" in storedValues())
        assertTrue(store().account.value.signInExpired)
    }

    @Test
    fun `a session this phone can no longer decrypt is a sign-in that has run out`() {
        preferences.edit {
            // What is left when the Keystore key is gone: text that no key opens.
            putString("session_encrypted", "unreadable")
            putLong("session_expires_at", now + 60_000)
        }

        val store = store()

        assertNull(store.currentSession())
        assertTrue(store.account.value.signInExpired)
    }

    @Test
    fun `signing in again or signing out ends a sign-in that has run out`() {
        val store = store()
        store.saveSession(Session("signed-session", expiresAt = now + 60_000))
        store.expireSession()

        store.saveSession(Session("new-session", expiresAt = now + 60_000))
        assertFalse(store.account.value.signInExpired)
        assertFalse(store().account.value.signInExpired)

        store.expireSession()
        store.clearSession()
        assertFalse(store.account.value.signInExpired)
        assertNull(store.account.value.signedInAs)
        assertFalse(store().account.value.signInExpired)
    }

    @Test
    fun `a refusal that arrives after signing out changes nothing`() {
        val store = store()

        store.expireSession()

        assertFalse(store.account.value.signInExpired)
        assertFalse(store().account.value.signInExpired)
    }

    @Test
    fun `signing out forgets the session and the calls left that went with it`() {
        val store = store()
        store.saveSession(Session("signed-session", expiresAt = now + 60_000))
        store.onCallsLeft(CallsLeft(BigDecimal(10), BigDecimal("4.2"), BigDecimal("5.8"), period = "2026-11", resetsAt = 1_796_083_200_000))

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
