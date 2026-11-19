package com.dantech.splitvibe.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The privacy policy and the terms of use: pages the proxy serves from its own address. */
data class LegalLinks(val privacyPolicy: String, val terms: String) {
    companion object {
        /** The pages on [proxyUrl], this build's address of the proxy; null in a build without one, which shows no links. */
        fun of(proxyUrl: String): LegalLinks? {
            val base = proxyUrl.toHttpUrlOrNull() ?: return null
            fun page(name: String) = base.newBuilder().addPathSegment(name).build().toString()
            return LegalLinks(privacyPolicy = page("privacy"), terms = page("terms"))
        }
    }
}

/** Links to the privacy policy and the terms of use, which open in the browser, or a word when no app on the phone opens them. */
@Composable
fun LegalLinksRow(links: LegalLinks, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cannotOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        // A flow row: with large text the second link moves to its own line instead of wrapping its label.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            TextButton(onClick = { cannotOpen = !openLink(context, links.privacyPolicy) }) {
                Text(stringResource(R.string.legal_privacy_policy))
            }
            TextButton(onClick = { cannotOpen = !openLink(context, links.terms) }) {
                Text(stringResource(R.string.legal_terms_of_use))
            }
        }
        if (cannotOpen) {
            Text(stringResource(R.string.legal_cannot_open), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Opens [url] in the app that handles it, such as the browser or Google Play; false when no app on the phone does. */
fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    true
} catch (e: ActivityNotFoundException) {
    false
}
