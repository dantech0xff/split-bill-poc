package com.dantech.splitvibe.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvPrimaryFab
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.ui.SPLIT_SURFACE
import com.dantech.splitvibe.ui.history.BillListingRow

/** The app's home: the way into a new split and the bills split last. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    /** The bills kept on the phone, newest first; the first few are shown. */
    bills: List<BillListing>,
    onSplitBill: () -> Unit,
    onOpenBill: (String) -> Unit,
    onSeeAllBills: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGroups: () -> Unit = {},
    onOpenRequests: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SvLargeTopBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            SvPrimaryFab(
                text = "Split a bill",
                icon = Icons.Default.Add,
                onClick = onSplitBill,
                modifier = Modifier.sharedSurface(SPLIT_SURFACE, resize = true),
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction),
                // Room below the last bill, so the floating button never covers it.
                bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
        ) {
            item {
                IntroCard(modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm))
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    FilledTonalButton(onClick = onOpenGroups) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(SvSpacing.sm))
                        Text("Groups")
                    }
                    FilledTonalButton(onClick = onOpenRequests) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        Spacer(Modifier.width(SvSpacing.sm))
                        Text("Requests")
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(start = SvSpacing.lg, end = SvSpacing.sm, top = SvSpacing.lg, bottom = SvSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Recent bills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (bills.isNotEmpty()) TextButton(onClick = onSeeAllBills) { Text("See all") }
                }
            }
            val recent = bills.take(RECENT_BILLS)
            if (recent.isEmpty()) {
                item {
                    SvEmptyState(icon = Icons.AutoMirrored.Filled.List, text = "Bills you split are kept here, also without a connection.")
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
            text = "Split a bill with AI",
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(start = SvSpacing.xl, end = SvSpacing.xl, top = SvSpacing.xl),
        )
        Spacer(Modifier.height(SvSpacing.sm))
        Text(
            text = "Scan the bill and say how to split it. The bill is read on this phone, " +
                "worked out by Claude, and you choose who pays what.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = SvSpacing.xl, end = SvSpacing.xl, bottom = SvSpacing.xl),
        )
    }
}

private val FAB_CLEARANCE = 88.dp

/** How many of the latest bills the home screen shows; the rest are a tap away. */
private const val RECENT_BILLS = 3
