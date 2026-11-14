package com.dantech.splitvibe.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvPrimaryFab
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSuccessMark
import com.dantech.splitvibe.designsystem.components.SvWorkingRow
import com.dantech.splitvibe.designsystem.motion.SvAmount
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface

/** Every design system component with sample content, in the order of docs/design-system.md. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SvLargeTopBar(title = "Components", scrollBehavior = scrollBehavior) },
        bottomBar = {
            SvBottomBar { SvPrimaryButton("SvPrimaryButton in SvBottomBar", onClick = {}) }
        },
        floatingActionButton = { SvPrimaryFab(text = "SvPrimaryFab", icon = Icons.Default.Add, onClick = {}) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                SvSectionHeader("Colour", hint = "The roles a screen may use.")
                FlowRow(
                    modifier = Modifier.padding(horizontal = SvSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    val colors = MaterialTheme.colorScheme
                    Swatch("primary", colors.primary, colors.onPrimary)
                    Swatch("primaryContainer", colors.primaryContainer, colors.onPrimaryContainer)
                    Swatch("tertiaryContainer", colors.tertiaryContainer, colors.onTertiaryContainer)
                    Swatch("errorContainer", colors.errorContainer, colors.onErrorContainer)
                    Swatch("surfaceContainer", colors.surfaceContainer, colors.onSurface)
                }
            }

            item {
                SvSectionHeader("Amounts", hint = "SvAmount with the three amount styles.")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SvAmount(902_664, format = ::sampleMoney, style = MaterialTheme.typography.amountLarge, countUp = true)
                    SvAmount(400_680, format = ::sampleMoney, style = MaterialTheme.typography.amountMedium)
                    SvAmount(24_000, format = ::sampleMoney, style = MaterialTheme.typography.amountSmall)
                }
            }

            item { SvSectionHeader("Elevation", hint = "SvCard at each level.") }
            itemsIndexed(SvElevation.entries) { _, level ->
                SvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                    elevation = level,
                ) {
                    Text(level.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(SvSpacing.lg))
                }
            }

            item { SvSectionHeader("Grouped rows", hint = "SvListRow, with SvAvatar and an amount.") }
            itemsIndexed(SAMPLE_PEOPLE) { index, name ->
                SvListRow(position = SvRowPosition.of(index, SAMPLE_PEOPLE.size), onClick = {}, onClickLabel = "Open $name") {
                    SvAvatar(name)
                    Spacer(Modifier.width(SvSpacing.md))
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    SvAmount(250_000L + index * 75_340L, format = ::sampleMoney, style = MaterialTheme.typography.amountMedium)
                }
            }
            item {
                Spacer(Modifier.height(SvSpacing.sm))
                SvListRow(position = SvRowPosition.Single) {
                    Text("A single row", style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                SvSectionHeader("Messages")
                Column(
                    modifier = Modifier.padding(horizontal = SvSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    SvMessageCard(text = "A notice, with one action.", isError = false, action = "Act on it")
                    SvMessageCard(text = "Something went wrong.", isError = true)
                }
            }

            item {
                SvSectionHeader("Working and done")
                Column(modifier = Modifier.padding(horizontal = SvSpacing.lg)) {
                    SvWorkingRow(status = "SvWorkingRow: reading the bill…", cancelLabel = "Cancel", onCancel = {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvLoadingIndicator(modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(SvSpacing.lg))
                        SvSuccessMark(Icons.Default.CheckCircle)
                    }
                    Spacer(Modifier.height(SvSpacing.md))
                    SvPrimaryButton("SvPrimaryButton, disabled", onClick = {}, enabled = false)
                }
            }

            item {
                SvSectionHeader("Shadow by hand", hint = "Modifier.elevation and SvElevation.surface, for a shape that is not a card.")
                val level = SvElevation.Level3
                Box(
                    modifier = Modifier
                        .padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm)
                        .size(64.dp)
                        .elevation(level, CircleShape)
                        .background(level.surface, CircleShape),
                )
                // Room below the last sample, so the floating button never covers it.
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color, onColor: Color) {
    Box(
        modifier = Modifier
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = SvSpacing.md, vertical = SvSpacing.sm),
    ) {
        Text(name, color = onColor, style = MaterialTheme.typography.labelLarge)
    }
}

private fun sampleMoney(minor: Long): String = "₫%,d".format(minor)

private val SAMPLE_PEOPLE = listOf("Trần Quang Minh", "Lê Thị Lan", "Emma Wilson")
