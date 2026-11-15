package com.dantech.splitvibe.notifications

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.proxy.ProxyApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tells the proxy where to send the signed-in account's push notices: this install's Firebase token,
 * under the install's own random id. Signing out takes it back, and deleting the account removes it
 * on the proxy's side.
 */
class DeviceRegistrar(
    private val api: ProxyApi,
    private val account: AccountStore,
    private val token: suspend () -> String? = {
        Push.enable()
        Push.token()
    },
) {

    /** Sends [newToken], or the current token when it is not given; does nothing without push or a sign-in. */
    suspend fun register(newToken: String? = null) {
        if (account.currentSession() == null) return
        val current = newToken ?: token() ?: return
        bestEffort { api.signedIn("PUT", path(), buildJsonObject { put("token", current) }) }
    }

    /** Stops notices to this install, before signing out; a phone without a connection simply stops asking for them. */
    suspend fun unregister() {
        if (account.currentSession() == null || !Push.isConfigured) return
        bestEffort { api.signedIn("DELETE", path()) }
    }

    private fun path() = "v1/devices/${account.account.value.installId}"

    // A notice that cannot be set up now is set up on the next start; nothing else waits for it.
    private suspend fun bestEffort(block: suspend () -> Unit) = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Unit
    }
}
