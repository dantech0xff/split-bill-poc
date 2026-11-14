package com.dantech.splitvibe.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.contacts.Contact
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvPrimaryFab
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.ui.SPLIT_SURFACE

/** The payment app's home: the entry to bill splitting and the user's in-app contacts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(contacts: List<Contact>, onSplitBill: () -> Unit, onOpenSettings: () -> Unit) {
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
                // Room below the last contact, so the floating button never covers it.
                bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
        ) {
            item {
                IntroCard(modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm))
            }
            item {
                Text(
                    text = "Contacts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = SvSpacing.lg, end = SvSpacing.lg, top = SvSpacing.lg, bottom = SvSpacing.xs),
                )
            }
            items(contacts, key = { it.id }) { contact ->
                ListItem(
                    headlineContent = { Text(contact.name) },
                    supportingContent = { Text("@${contact.handle}") },
                    leadingContent = { SvAvatar(contact.name) },
                )
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
            text = "Take a photo of the bill and say how to split it. The bill is read on this phone, " +
                "worked out by Claude, and you pick who shares what.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = SvSpacing.xl, end = SvSpacing.xl, bottom = SvSpacing.xl),
        )
    }
}

private val FAB_CLEARANCE = 88.dp
