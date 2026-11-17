package com.dantech.splitvibe.ui.settings

import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.data.account.GoogleSignInException
import com.dantech.splitvibe.data.proxy.ProxyException
import java.io.IOException
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMessagesTest {

    @Test
    fun `closing Google's picker needs no message`() {
        assertNull(messageOf(GoogleSignInException(GoogleSignInException.Reason.CANCELLED)))
    }

    @Test
    fun `each failure tells the person what to do`() {
        val cases = mapOf(
            GoogleSignInException(GoogleSignInException.Reason.NO_ACCOUNT) to "Add a Google account",
            GoogleSignInException(GoogleSignInException.Reason.UNAVAILABLE) to "not available on this phone",
            GoogleSignInException(GoogleSignInException.Reason.FAILED) to "did not work",
            ProxyException(401, "id_token", null, null) to "did not accept this Google sign-in",
            ProxyException(503, "sign_in_off", null, null) to "not available right now",
            ProxyException(401, "session", null, null) to "Sign in again",
            ProxyException(502, "http_502", null, null) to "server had a problem",
            ProxyException(429, "rate_limited", 2400, null) to "so nothing was changed. Try again in about 40 minutes.",
            ProxyException(429, "rate_limited", null, null) to "so nothing was changed. Try again later.",
            IOException("offline") to "Check the internet connection",
        )

        for ((error, says) in cases) {
            val message = messageOf(error).orEmpty()
            assertTrue("$message should say \"$says\"", message.contains(says))
        }
    }

    @Test
    fun `an unexpected failure says only that something went wrong, never what the exception says`() {
        assertEquals("Something went wrong. Please try again.", messageOf(IllegalStateException("Keystore key split_vibe_session is gone")))
    }

    @Test
    fun `calls left are shown without zeros that say nothing, and what each use takes is said`() {
        fun calls(limit: String, used: String, remaining: String, resetsAt: Long?) =
            CallsLeft(BigDecimal(limit), BigDecimal(used), BigDecimal(remaining), if (resetsAt == null) "trial" else "2026-11", resetsAt)

        assertEquals("2.8 of 3 free calls left on this phone.", callsLeftText(calls("3", "0.2", "2.8", resetsAt = null)))
        assertEquals(
            "2.8 of 3 calls left this month. They come back on 1 December.",
            callsLeftText(calls("3", "0.20", "2.80", resetsAt = DECEMBER_FIRST)),
        )
        assertEquals(
            "0 of 10 calls left this month. They come back on 1 December.",
            callsLeftText(calls("10", "10", "0.00", DECEMBER_FIRST)),
        )
        assertEquals("Reading a bill takes 1 call, and changing a split by a sentence takes 0.2.", CALL_COSTS)
    }

    private companion object {
        /** 1 December 2026, in UTC. */
        const val DECEMBER_FIRST = 1_796_083_200_000
    }
}
