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
import com.dantech.splitvibe.data.bills.SavedBill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/**
 * The notices the app shows: the push notices the proxy sends, and one for each draft a recurring
 * bill makes. Their words are the app's own, chosen by the kind of notice; nothing anyone typed,
 * such as a name, a merchant or a link, ever comes in a notice.
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

    /**
     * Tells the user that [draft], made by a recurring bill on its day, is waiting to be checked
     * and finished, when the user allows notices. Tapping the notice opens the draft. Each draft
     * has a notice of its own, so another one never takes its place.
     */
    fun showDraft(context: Context, draft: SavedBill) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DRAFTS_CHANNEL, "Recurring bills", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a recurring bill, such as the rent, is due and its draft is ready to check."
            },
        )
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_KIND, KIND_DRAFT)
            putExtra(EXTRA_BILL_ID, draft.id)
        }
        val tap = PendingIntent.getActivity(
            context,
            draft.id.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val day = draft.bill.date?.let { date ->
            try {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.parse(date))
            } catch (e: DateTimeParseException) {
                null
            }
        }
        val text = if (day == null) "Its draft is ready to check and finish." else "Its draft for $day is ready to check and finish."
        val notice = NotificationCompat.Builder(context, DRAFTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("A recurring bill is due")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        NotificationManagerCompat.from(context).notify(draft.id, DRAFT_NOTICE, notice)
    }

    /** The id of every draft's notice, which its tag, the draft's id, tells apart. */
    private const val DRAFT_NOTICE = 1
}
