package com.dantech.splitvibe.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dantech.splitvibe.MainActivity
import com.dantech.splitvibe.R

/**
 * The push notices the app shows. Their words are the app's own, chosen by the kind of notice the
 * proxy sent; nothing anyone typed, such as a name, a merchant or a link, ever comes in a notice.
 */
object Notices {

    const val CHANNEL = "requests"

    private const val CLAIM_TEXT = "A person asked to take a place in one of your groups. Open Split Vibe to approve or reject it."

    /** Extras that tell the app what a tapped notice is about. */
    const val EXTRA_KIND = "com.dantech.splitvibe.notice.kind"
    const val EXTRA_GROUP_ID = "com.dantech.splitvibe.notice.group"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Payment requests and groups", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When someone asks you to pay, says they paid, confirms a payment or asks to join a group."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * The words of a notice of [kind], or null for a kind this version of the app does not know.
     * They are the proxy's own words (`proxy/src/push.ts`), which the system shows while the app is
     * in the background, so a notice reads the same either way.
     */
    fun textOf(kind: String?): Pair<String, String>? = when (kind) {
        "request" -> "New payment request" to "Someone asked you to pay them back. Open Split Vibe to see the request."
        "reported" -> "Payment reported" to "A payment you asked for was reported as paid. Open Split Vibe to confirm it."
        "confirmed" -> "Payment confirmed" to "A payment you reported was confirmed. Open Split Vibe to see it."
        "reminder" -> "Payment reminder" to "A payment request is still waiting for you. Open Split Vibe to see it."
        "claim" -> "Someone asked to join" to CLAIM_TEXT
        "unpaid" -> "Payment still open" to "Someone has not paid you back yet. Open Split Vibe to send them a reminder."
        "unconfirmed" -> "Payment to confirm" to "A payment was reported to you. Open Split Vibe to confirm it once it arrived."
        else -> null
    }

    /** Shows the notice the proxy sent as [data], when the user allows notices. */
    fun show(context: Context, data: Map<String, String>) {
        val kind = data["type"]
        val (title, text) = textOf(kind) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_KIND, kind)
            data["groupId"]?.let { putExtra(EXTRA_GROUP_ID, it) }
        }
        val tap = PendingIntent.getActivity(
            context,
            kind.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        NotificationManagerCompat.from(context).notify(kind.hashCode(), notice)
    }
}
