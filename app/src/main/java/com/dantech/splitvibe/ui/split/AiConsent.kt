package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvAiMark
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.common.PrivacyPolicyLink

/**
 * Asks, before the first call to the AI and again after Settings took the consent back, whether
 * Split Vibe may send what the AI needs to Anthropic: the text read from a bill, or a change typed
 * or said with the bill it is about. Agreeing lets the call go ahead; [declineLabel] says what
 * happens instead, such as typing the bill in by hand. Nothing else in the app waits for it.
 */
@Composable
internal fun AiConsentDialog(
    declineLabel: String,
    legalLinks: LegalLinks?,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    /** Back, without an answer. */
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AiConsentScreen(declineLabel = declineLabel, legalLinks = legalLinks, onAgree = onAgree, onDecline = onDecline)
    }
}

/**
 * What the consent screen shows: the mark of the AI, what goes to the AI and where, the privacy
 * policy, and the two answers. The answers stand at the end of the screen, below the text that
 * scrolls when large text makes it taller than the screen, so they are always in view.
 */
@Composable
internal fun AiConsentScreen(
    declineLabel: String,
    legalLinks: LegalLinks?,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.xl, bottom = SvSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                SvAiMark(size = MARK_SIZE)
                Text(
                    stringResource(R.string.ai_consent_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(stringResource(R.string.ai_consent_where), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ai_consent_bill_read), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ai_consent_split_edit), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ai_consent_choice), style = MaterialTheme.typography.bodyMedium)
                legalLinks?.let { PrivacyPolicyLink(it) }
            }
            Column(
                modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            ) {
                SvPrimaryButton(stringResource(R.string.ai_consent_agree), onClick = onAgree)
                SvQuietButton(declineLabel, onClick = onDecline, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val MARK_SIZE = 48.dp
