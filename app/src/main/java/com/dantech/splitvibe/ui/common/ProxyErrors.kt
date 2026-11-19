package com.dantech.splitvibe.ui.common

import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.text
import java.io.IOException

/** What a screen of groups or requests shows besides its data: whether a call is running, and what went wrong. */
data class ProxyWork(val working: Boolean = false, val message: UiText? = null)

/** What to tell the user when a call about groups or requests did not work. */
object ProxyErrors {

    fun messageOf(error: Throwable): UiText = when (error) {
        is ProxyException -> when {
            error.code == ProxyApi.SESSION_REFUSED -> text(R.string.proxy_sign_in)
            error.code == "pending" -> text(R.string.proxy_pending)
            error.status == 403 -> text(R.string.proxy_forbidden)
            error.status == 404 -> text(R.string.proxy_gone)
            error.status == 409 -> text(R.string.proxy_conflict)
            error.status == 410 -> text(R.string.proxy_link_ended)
            error.status == 413 -> text(R.string.proxy_too_large)
            error.status == 429 -> text(R.string.proxy_too_many)
            error.status == 503 -> text(R.string.proxy_not_set_up)
            error.status == 400 -> text(R.string.proxy_not_checked)
            else -> text(R.string.proxy_failed)
        }

        is IOException -> text(R.string.proxy_offline)

        else -> text(R.string.common_something_went_wrong)
    }
}
