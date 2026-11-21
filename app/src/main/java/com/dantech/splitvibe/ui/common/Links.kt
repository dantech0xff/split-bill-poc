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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The privacy policy and the terms of use: pages the proxy serves from its own address. */
data class LegalLinks(val privacyPolicy: String, val terms: String) {
    /** The two pages, each with the name of its link, for [PageLinks]. */
    internal val pages: List<Pair<Int, String>>
        get() = listOf(R.string.legal_privacy_policy to privacyPolicy, R.string.legal_terms_of_use to terms)

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
fun LegalLinksRow(links: LegalLinks, modifier: Modifier = Modifier) = PageLinks(links.pages, modifier)

/** The link to the privacy policy alone, as where the person decides what the app may send. */
@Composable
fun PrivacyPolicyLink(links: LegalLinks, modifier: Modifier = Modifier) =
    PageLinks(listOf(R.string.legal_privacy_policy to links.privacyPolicy), modifier)

/** A page to link to, as [PageLinks] gives it to what draws its links: what its link says, and what opens it. */
internal class PageLink(val label: String, val open: () -> Unit)

/**
 * A link for each of [pages], each the resource of its label and its address, and below them a word,
 * with [noticeModifier], when no app on the phone opens them. [links] draws the links; by default they
 * are buttons of text in a row.
 */
@Composable
internal fun PageLinks(
    pages: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
    noticeModifier: Modifier = Modifier,
    links: @Composable (List<PageLink>) -> Unit = { TextLinks(it) },
) {
    val context = LocalContext.current
    var cannotOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier) {
        links(pages.map { (label, url) -> PageLink(stringResource(label)) { cannotOpen = !openLink(context, url) } })
        if (cannotOpen) {
            // Read out by TalkBack as it appears, since nothing else happens after the tap.
            Text(
                stringResource(R.string.legal_cannot_open),
                modifier = noticeModifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Links as buttons of text, in a flow row: with large text the second moves to its own line instead of wrapping its label. */
@Composable
private fun TextLinks(links: List<PageLink>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
        links.forEach { link -> TextButton(onClick = link.open) { Text(link.label) } }
    }
}

/** Opens [url] in the app that handles it, such as the browser or Google Play; false when no app on the phone does. */
fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    true
} catch (e: ActivityNotFoundException) {
    false
}
