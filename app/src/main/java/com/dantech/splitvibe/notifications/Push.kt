package com.dantech.splitvibe.notifications

import android.content.Context
import com.dantech.splitvibe.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Push notices about requests and groups, which the proxy sends through Firebase Cloud Messaging.
 * Firebase is used for nothing else, and only in a build given its four settings; without them no
 * notice arrives and every flow works the same.
 */
object Push {

    val isConfigured: Boolean
        get() = listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        )
            .all { it.isNotBlank() }

    /** Starts Firebase with the build's settings, once, and makes the channel the notices come in. */
    fun start(context: Context) {
        if (!isConfigured) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        Notices.createChannel(context)
    }

    /** This install's address for push notices, or null when push is off or Firebase gave none. */
    suspend fun token(): String? = if (!isConfigured) null else FirebaseMessaging.getInstance().token.awaitOrNull()

    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> continuation.resume(if (task.isSuccessful) task.result else null) }
    }
}
