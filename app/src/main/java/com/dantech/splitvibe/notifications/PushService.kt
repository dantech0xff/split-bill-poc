package com.dantech.splitvibe.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

/** Receives the proxy's push notices, and a new token whenever Firebase hands this install one. */
class PushService : FirebaseMessagingService() {

    private val registrar: DeviceRegistrar by inject()

    // Firebase calls this on a thread of its own and stops the service once it returns, so the
    // token is sent before returning, for a short while at most.
    override fun onNewToken(token: String) {
        runBlocking { withTimeoutOrNull(REGISTER_TIMEOUT_MS) { registrar.register(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Notices.show(this, message.data)
    }

    private companion object {
        const val REGISTER_TIMEOUT_MS = 10_000L
    }
}
