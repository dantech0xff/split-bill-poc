package com.dantech.splitvibe.ui.common

import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import java.io.IOException

/** What to tell the user when a call about groups or requests did not work. */
object ProxyErrors {

    fun messageOf(error: Throwable): String = when (error) {
        is ProxyException -> when {
            error.code == ProxyApi.SESSION_REFUSED -> "Sign in with Google in Settings to share groups and requests."
            error.code == "pending" -> "The group's owner has not let you in yet."
            error.status == 403 -> "You cannot do that in this group."
            error.status == 404 -> "That is not there any more."
            error.status == 409 -> "Someone changed this at the same moment, so nothing was changed. Have a look and try again."
            error.status == 410 -> "This link has run out or was taken back. Ask for a new one."
            error.status == 413 -> "This is too large to share."
            error.status == 429 -> "That was a lot at once. Please try again in a while."
            error.status == 503 -> "Groups and requests are not set up on Split Vibe's server yet."
            error.status == 400 -> "Something in this did not check out. Please look it over."
            else -> "Split Vibe's server could not do that just now. Please try again."
        }

        is IOException -> "There is no connection. What you see was saved earlier."

        else -> "Something went wrong. Please try again."
    }
}
