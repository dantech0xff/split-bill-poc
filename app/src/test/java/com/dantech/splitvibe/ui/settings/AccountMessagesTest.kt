package com.dantech.splitvibe.ui.settings

import com.dantech.splitvibe.data.account.GoogleSignInException
import com.dantech.splitvibe.data.proxy.ProxyException
import java.io.IOException
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
            GoogleSignInException(GoogleSignInException.Reason.FAILED) to "did not work",
            ProxyException(401, "id_token", null, null) to "did not accept this Google sign-in",
            ProxyException(503, "sign_in_off", null, null) to "not available right now",
            ProxyException(401, "session", null, null) to "Sign in again",
            ProxyException(502, "http_502", null, null) to "server had a problem",
            IOException("offline") to "Check the internet connection",
        )

        for ((error, says) in cases) {
            val message = messageOf(error).orEmpty()
            assertTrue("$message should say \"$says\"", message.contains(says))
        }
    }
}
