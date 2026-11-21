package com.dantech.splitvibe.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.components.SvAvatarStack
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.text.resolve

/**
 * A payment request someone sent as a link: who asks, what for and how much, how it stands, how to
 * pay it, and a way to say it is paid or to keep it for later.
 */
@Composable
fun RequestLinkScreen(
    state: RequestLinkState,
    signedIn: Boolean,
    onAccept: () -> Unit,
    onReportPaid: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SvTopBar(
                title = stringResource(R.string.requests_share_title),
                backLabel = stringResource(R.string.common_back),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            if (!signedIn) {
                SvMessageCard(
                    text = stringResource(R.string.requests_link_sign_in),
                    isError = false,
                    action = stringResource(R.string.common_sign_in_in_settings),
                    onAction = onOpenSettings,
                )
                return@Column
            }
            state.message?.let {
                SvMessageCard(text = it.resolve(), isError = true, action = stringResource(R.string.common_try_again), onAction = onRetry)
            }
            val request = state.request
            if (request == null) {
                if (state.working) SvLoadingIndicator()
                return@Column
            }
            RequestHero(request, who = state.creditorName ?: request.creditorName ?: stringResource(R.string.common_someone))
            if (request.status == RequestStatus.WAITING) {
                PayPanel(request)
                SvPrimaryButton(stringResource(R.string.requests_i_have_paid), onClick = onReportPaid, enabled = !state.working)
                if (!state.accepted) {
                    SvQuietButton(
                        text = stringResource(R.string.requests_keep_for_later),
                        onClick = onAccept,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.working,
                    )
                }
            }
        }
    }
}

/**
 * The block at the top of the request: who asks, [who], and what for, the amount, and how the
 * request stands for the one asked to pay it, a paid one with a tag that says so. A screen reader
 * reads it as one element.
 */
@Composable
private fun RequestHero(request: RequestView, who: String) {
    // How it stands for the payer, whoever opened the link.
    val status = statusText(request.copy(role = RequestView.DEBTOR)).resolve()
    val parts = request.bill.formatParts(request.amountMinor)
    SvHero(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        label = {
            SvAvatarStack(listOf(who), size = AVATAR_SIZE)
            Text(listOfNotNull(who, request.title).joinToString(" · "), modifier = Modifier.weight(1f))
        },
        amount = {
            SvHeroAmount(
                prefix = parts.prefix,
                number = parts.number,
                suffix = parts.suffix,
                spokenText = request.bill.format(request.amountMinor),
            )
        },
    ) {
        if (request.status == RequestStatus.PAID) {
            SvTag(status, kind = SvTagKind.Accent, icon = Icons.Default.Check)
        } else {
            SvTag(status)
        }
    }
}

private val AVATAR_SIZE = 32.dp
