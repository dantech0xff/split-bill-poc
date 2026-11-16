package com.dantech.splitvibe.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dantech.splitvibe.MainActivity
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.dayOf
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The notices the app shows: the push notices the proxy sends, and one for the drafts a recurring
 * bill makes. The push notices' words are the app's own, chosen by the kind of notice, and nothing
 * anyone typed, such as a name, a merchant or a link, comes in them. A recurring bill's notice names
 * it as the user called it, which a lock screen that hides private notices leaves out.
 */
object Notices {

    const val CHANNEL = "requests"

    /** The channel of the notices about drafts of recurring bills, which the user can turn off on its own. */
    const val DRAFTS_CHANNEL = "recurring_bills"

    /** The kind of the notice about a new draft of a recurring bill, in [EXTRA_KIND]. */
    const val KIND_DRAFT = "draft"

    private const val CLAIM_TEXT = "A person asked to take a place in one of your groups. Open Split Vibe to approve or reject it."

    /** Extras that tell the app what a tapped notice is about. */
    const val EXTRA_KIND = "com.dantech.splitvibe.notice.kind"
    const val EXTRA_GROUP_ID = "com.dantech.splitvibe.notice.group"
    const val EXTRA_BILL_ID = "com.dantech.splitvibe.notice.bill"

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
        if (!allowed(context)) return
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

    /**
     * Tells the user that the recurring bill called [name] made [drafts], earliest first, and that
     * they wait to be checked and finished, when the user allows notices. However long the app was
     * away, that is one notice, and tapping it opens the newest draft. A later run's notice is one of
     * its own, so it never takes this one's place.
     */
    fun showDrafts(context: Context, name: String, drafts: List<SavedBill>) {
        val newest = drafts.lastOrNull() ?: return
        if (!allowed(context)) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DRAFTS_CHANNEL, "Recurring bills", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a recurring bill, such as the rent, is due and its draft is ready to check."
            },
        )
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // The system tells pending intents apart by their address, never by their extras, so
            // each notice's has its draft's: two notices never share one tap.
            data = Uri.fromParts(DRAFT_SCHEME, newest.id, null)
            putExtra(EXTRA_KIND, KIND_DRAFT)
            putExtra(EXTRA_BILL_ID, newest.id)
        }
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val day = dayOf(newest.bill.date)?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it) }
        val text = when {
            drafts.size == 1 && day != null -> "Its draft for $day is ready to check and finish."
            drafts.size == 1 -> "Its draft is ready to check and finish."
            day != null -> "${drafts.size} drafts are ready to check and finish, the newest for $day."
            else -> "${drafts.size} drafts are ready to check and finish."
        }
        // What a lock screen that hides private notices shows instead: nothing the user typed.
        val hidden = NotificationCompat.Builder(context, DRAFTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(DRAFTS_DUE)
            .build()
        val notice = NotificationCompat.Builder(context, DRAFTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        NotificationManagerCompat.from(context).notify(newest.id, DRAFT_NOTICE, notice)
    }

    /** Whether the user lets the app show notices, which Android 13 and later ask leave for. */
    private fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** What the notice of a recurring bill's drafts says where private notices are hidden. */
    private const val DRAFTS_DUE = "A recurring bill is due"

    /** The scheme of the address that tells apart the taps of the drafts' notices; nothing opens it. */
    private const val DRAFT_SCHEME = "splitvibe-draft"

    /** The id of every drafts' notice, which its tag, the newest draft's id, tells apart. */
    private const val DRAFT_NOTICE = 1
}
