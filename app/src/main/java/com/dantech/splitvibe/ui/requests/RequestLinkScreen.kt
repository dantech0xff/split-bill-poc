package com.dantech.splitvibe.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.text.resolve

/** A payment request someone sent as a link: what it is for, how to pay it, and a way to say it is paid. */
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text(stringResource(R.string.requests_share_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(SvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
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
            SvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                    Text(
                        listOfNotNull(
                            state.creditorName ?: request.creditorName ?: stringResource(R.string.common_someone),
                            request.title,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(request.bill.format(request.amountMinor), style = MaterialTheme.typography.amountLarge)
                    Text(statusText(request.copy(role = "debtor")).resolve(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (request.status == RequestStatus.WAITING) {
                PayPanel(request)
                SvPrimaryButton(stringResource(R.string.requests_i_have_paid), onClick = onReportPaid, enabled = !state.working)
                if (!state.accepted) {
                    OutlinedButton(onClick = onAccept, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.requests_keep_for_later))
                    }
                }
            }
        }
    }
}
