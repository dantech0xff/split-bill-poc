package com.dantech.splitvibe.ui

import android.content.Intent
import android.net.Uri
import com.dantech.splitvibe.notifications.Notices
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Where a link or a tapped notice takes the app. */
sealed interface AppLink {
    /** An invite link, `/g/{token}` on the proxy's address. */
    data class JoinGroup(val token: String) : AppLink

    /** A payment request link, `/r/{token}` on the proxy's address. */
    data class OpenRequest(val token: String) : AppLink

    data class Group(val id: String) : AppLink

    data object Requests : AppLink

    companion object {
        private val TOKEN = Regex("[A-Za-z0-9_-]{16,128}")

        /**
         * What [intent] asks to open: a link on [proxyUrl]'s own address, or a notice of the app's
         * own. Anything else, such as a link to another address, opens nothing.
         */
        fun of(intent: Intent?, proxyUrl: String): AppLink? {
            if (intent == null) return null
            intent.getStringExtra(Notices.EXTRA_KIND)?.let { kind ->
                val groupId = intent.getStringExtra(Notices.EXTRA_GROUP_ID)
                return if (kind == "claim" && groupId != null) Group(groupId) else Requests
            }
            if (intent.action != Intent.ACTION_VIEW) return null
            return of(intent.data, proxyUrl)
        }

        fun of(uri: Uri?, proxyUrl: String): AppLink? {
            val host = proxyUrl.toHttpUrlOrNull()?.host ?: return null
            if (uri == null || uri.scheme != "https" || uri.host != host || uri.userInfo != null) return null
            val segments = uri.pathSegments
            if (segments.size != 2 || !TOKEN.matches(segments[1])) return null
            return when (segments[0]) {
                "g" -> JoinGroup(segments[1])
                "r" -> OpenRequest(segments[1])
                else -> null
            }
        }
    }
}
