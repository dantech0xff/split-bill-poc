package com.dantech.splitvibe.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Receives the proxy's push notices, and a new token whenever Firebase hands this install one. */
class PushService : FirebaseMessagingService() {

    private val registrar: DeviceRegistrar by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { registrar.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Notices.show(this, message.data)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
