package com.dantech.splitvibe.ui.split

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSuccessMark
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium

/** Last step: confirms which requests would go out. No real payment request is created. */
@Composable
fun SentContent(result: SplitResultState, requests: List<PaymentRequest>, onDone: () -> Unit) {
    val bill = result.bill
    Scaffold(
        bottomBar = {
            SvBottomBar { SvPrimaryButton("Done", onClick = onDone) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.xl, vertical = SvSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The request button of the step before turns into this mark.
                    SvSuccessMark(Icons.Default.Check, modifier = Modifier.sharedSurface(SEND_SURFACE, resize = true))
                    Spacer(Modifier.height(SvSpacing.lg))
                    Text("Requests sent", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Spacer(Modifier.height(SvSpacing.sm))
                    Text(
                        "Demo only: no real payment requests were created.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            itemsIndexed(requests, key = { _, request -> request.participant.id }) { index, request ->
                val name = result.nameOf(request.participant.id)
                SvListRow(position = SvRowPosition.of(index, requests.size)) {
                    SvAvatar(name)
                    Spacer(Modifier.width(SvSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        result.converted(request.amountMinor)?.let { converted ->
                            Text(
                                "About $converted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(SvSpacing.md))
                    Text(bill.format(request.amountMinor), style = MaterialTheme.typography.amountMedium)
                }
            }
        }
    }
}
