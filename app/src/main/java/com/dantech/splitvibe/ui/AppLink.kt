package com.dantech.splitvibe.ui

import android.content.Intent
import android.net.Uri
import com.dantech.splitvibe.notifications.Notices
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where a link or a tapped notice takes the app. */
sealed interface AppLink {
    /** An invite link, `/g/#{token}` on the proxy's address. */
    data class JoinGroup(val token: String) : AppLink

    /** A payment request link, `/r/#{token}` on the proxy's address. */
    data class OpenRequest(val token: String) : AppLink

    data class Group(val id: String) : AppLink

    data object Requests : AppLink

    /** A bill on the phone, such as the draft a recurring bill made, whose notice was tapped. */
    data class OpenBill(val id: String) : AppLink

    companion object {
        private val TOKEN = Regex("[A-Za-z0-9_-]{16,128}")
        private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** What Firebase adds to the intent of a notice the system showed. */
        private const val FIREBASE_MESSAGE_ID = "google.message_id"

        /**
         * What [intent] asks to open: a link on [proxyUrl]'s own address, or a notice of the app's
         * own. Anything else, such as a link to another address, opens nothing.
         */
        fun of(intent: Intent?, proxyUrl: String): AppLink? {
            if (intent == null) return null
            // A notice the app showed itself, or one the system showed while the app was in the
            // background, whose data Firebase puts in the intent as it came.
            val fromSystem = intent.hasExtra(FIREBASE_MESSAGE_ID)
            val kind = intent.getStringExtra(Notices.EXTRA_KIND) ?: intent.getStringExtra("type").takeIf { fromSystem }
            if (kind == Notices.KIND_DRAFT) {
                // Any app can send extras, so only what has the shape of a bill's id opens a bill.
                return intent.getStringExtra(Notices.EXTRA_BILL_ID)?.takeIf { UUID.matches(it) }?.let(::OpenBill)
            }
            if (kind != null) {
                // Any app can send extras, so a group id must look like one before it goes into a call.
                val groupId = (intent.getStringExtra(Notices.EXTRA_GROUP_ID) ?: intent.getStringExtra("groupId").takeIf { fromSystem })
                    ?.takeIf { UUID.matches(it) }
                return if (kind == "claim" && groupId != null) Group(groupId) else Requests
            }
            if (intent.action != Intent.ACTION_VIEW) return null
            return of(intent.data, proxyUrl)
        }

        /**
         * The token of a link rides in its fragment, `/g/#{token}`, which the browser never sends to
         * the server, so no token ends up in a log or a trace on the way.
         */
        fun of(uri: Uri?, proxyUrl: String): AppLink? {
            val host = proxyUrl.toHttpUrlOrNull()?.host ?: return null
            if (uri == null || uri.scheme != "https" || uri.host != host || uri.userInfo != null) return null
            val token = uri.fragment?.takeIf { TOKEN.matches(it) } ?: return null
            return when (uri.path) {
                "/g/" -> JoinGroup(token)
                "/r/" -> OpenRequest(token)
                else -> null
            }
        }
    }
}
