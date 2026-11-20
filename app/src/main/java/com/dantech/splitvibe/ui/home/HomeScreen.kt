package com.dantech.splitvibe.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.ui.history.BillListingRow

/**
 * The app's home: the bills split last, with the way to all of them. A new split starts from the
 * navigation bar, which the app passes as [bottomBar].
 */
@Composable
fun HomeScreen(
    /** The bills kept on the phone, newest first; the first few are shown. */
    bills: List<BillListing>,
    onOpenBill: (String) -> Unit,
    onSeeAllBills: () -> Unit,
    onOpenSettings: () -> Unit,
    /**
     * Whether the phone keeps a recurring bill, which stays when the bills it came from are deleted.
     * The list of bills then still opens, as recurring bills are one of its tabs.
     */
    hasRecurringBills: Boolean = false,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            SvLargeTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        // The list ends above the navigation bar, so no row ever passes under it half hidden.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                IntroCard(modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm))
            }
            item {
                Row(
                    modifier = Modifier.padding(start = SvSpacing.lg, end = SvSpacing.sm, top = SvSpacing.lg, bottom = SvSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_recent_bills),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (bills.isNotEmpty() || hasRecurringBills) {
                        TextButton(onClick = onSeeAllBills) { Text(stringResource(R.string.home_see_all)) }
                    }
                }
            }
            val recent = bills.take(RECENT_BILLS)
            if (recent.isEmpty()) {
                item {
                    SvEmptyState(icon = Icons.AutoMirrored.Filled.List, text = stringResource(R.string.home_no_bills))
                }
            }
            itemsIndexed(recent, key = { _, listing -> listing.id }) { index, listing ->
                BillListingRow(listing = listing, position = SvRowPosition.of(index, recent.size), onOpen = { onOpenBill(listing.id) })
            }
        }
    }
}

@Composable
private fun IntroCard(modifier: Modifier = Modifier) {
    SvCard(
        modifier = modifier.fillMaxWidth(),
        elevation = SvElevation.Level2,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = stringResource(R.string.home_intro_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(start = SvSpacing.xl, end = SvSpacing.xl, top = SvSpacing.xl),
        )
        Spacer(Modifier.height(SvSpacing.sm))
        Text(
            text = stringResource(R.string.home_intro_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = SvSpacing.xl, end = SvSpacing.xl, bottom = SvSpacing.xl),
        )
    }
}

/** How many of the latest bills the home screen shows; the rest are a tap away. */
private const val RECENT_BILLS = 3
