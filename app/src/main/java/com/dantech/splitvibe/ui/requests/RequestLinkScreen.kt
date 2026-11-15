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
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge

/** A payment request someone sent as a link: what it is for, how to pay it, and a way to say it is paid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestLinkScreen(
    state: RequestLinkState,
    signedIn: Boolean,
    onAccept: () -> Unit,
    onReportPaid: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment request") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
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
                    text = "Sign in with Google to open this request in the app. The message it came with has the details to pay it.",
                    isError = false,
                    action = "Sign in in Settings",
                    onAction = onOpenSettings,
                )
                return@Column
            }
            state.message?.let { SvMessageCard(text = it, isError = true) }
            val request = state.request
            if (request == null) {
                if (state.working) SvLoadingIndicator()
                return@Column
            }
            SvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                    Text(
                        listOfNotNull(request.creditorName ?: "Someone", request.title).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(request.bill.format(request.amountMinor), style = MaterialTheme.typography.amountLarge)
                    Text(statusText(request.copy(role = "debtor")), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (request.status == RequestStatus.WAITING) {
                PayPanel(request)
                SvPrimaryButton("I have paid", onClick = onReportPaid, enabled = !state.working)
                if (!state.accepted) {
                    OutlinedButton(onClick = onAccept, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                        Text("Keep it in my requests for later")
                    }
                }
            }
        }
    }
}
