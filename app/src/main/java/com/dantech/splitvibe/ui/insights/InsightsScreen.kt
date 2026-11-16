package com.dantech.splitvibe.ui.insights

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.motion.SvAmount
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.Spending
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.export.Exporter

/** What the insights screen can ask for. */
class InsightsActions(
    val onChoosePeriod: (InsightsPeriod) -> Unit,
    val onShare: (ExportFormat) -> Unit,
    /** Saves the CSV files into a folder the user picked. */
    val onSaveToFolder: (Uri) -> Unit,
    /** Saves the PDF summary into a file the user made. */
    val onSaveTo: (Uri) -> Unit,
    val onShared: () -> Unit,
    val onPickerMissing: () -> Unit,
    val onDismissMessage: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * What the user's finished bills add up to in their local currency, for the period they choose:
 * all together, by month, by category, by person and by group, with notes on what the totals
 * leave out. The bills of the period can be exported as files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(state: InsightsState?, export: ExportStatus, actions: InsightsActions) {
    var exporting by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        folder?.let(actions.onSaveToFolder)
    }
    val makeFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.PDF_TYPE)) { document ->
        document?.let(actions.onSaveTo)
    }
    LaunchedEffect(export.share) {
        val sheet = export.share ?: return@LaunchedEffect
        try {
            context.startActivity(sheet)
        } catch (e: ActivityNotFoundException) {
            // No app takes files on this phone; saving them is still possible.
        }
        actions.onShared()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            if (!state?.bills.isNullOrEmpty()) {
                SvBottomBar { SvPrimaryButton("Export these bills", onClick = { exporting = true }, enabled = !export.working) }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "period") {
                SvChoiceGroup(
                    options = InsightsPeriod.entries.map { it.label },
                    selected = (state?.period ?: InsightsPeriod.THIS_MONTH).ordinal,
                    onSelect = { actions.onChoosePeriod(InsightsPeriod.entries[it]) },
                    modifier = Modifier.padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                )
            }
            export.message?.let { message ->
                item(key = "message") {
                    SvMessageCard(
                        text = message.text,
                        isError = message.isError,
                        action = "OK",
                        onAction = actions.onDismissMessage,
                        modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
                    )
                }
            }
            when {
                state == null -> item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

                !state.anyFinished -> item(key = "empty") {
                    SvEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        text = "Bills you finish are added up here: by month, category, person and group.",
                        modifier = rowMotion(),
                    )
                }

                else -> insights(state)
            }
        }
    }

    if (exporting && state != null) {
        ExportSheet(
            state = state,
            onShare = { format ->
                exporting = false
                actions.onShare(format)
            },
            onSave = { format ->
                exporting = false
                try {
                    when (format) {
                        ExportFormat.CSV -> pickFolder.launch(null)
                        ExportFormat.PDF -> makeFile.launch(Exporter.pdfName(state.periodKey))
                    }
                } catch (e: ActivityNotFoundException) {
                    actions.onPickerMissing()
                }
            },
            onDismiss = { exporting = false },
        )
    }
}

private fun LazyListScope.insights(state: InsightsState) {
    val insights = state.insights
    val currency = insights.currency
    item(key = "overview") { Overview(state, modifier = rowMotion()) }
    itemsIndexed(state.notes, key = { index, _ -> "note $index" }) { _, note ->
        SvMessageCard(note, isError = false, modifier = rowMotion().padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs))
    }
    if (state.bills.isEmpty()) {
        item(key = "none") {
            SvEmptyState(Icons.Default.ShoppingCart, "No finished bill is dated in this period.", modifier = rowMotion())
        }
        return
    }
    section(
        key = "month",
        title = "By month",
        lines = insights.byMonth.entries.reversed().map { (month, spending) ->
            SpendingLine("$month", Exporter.monthName(month), spending.totalMinor, shareAndCount(spending, currency::format))
        },
        format = currency::format,
    )
    section(
        key = "category",
        title = "By category",
        lines = insights.byCategory.map { (category, spending) ->
            SpendingLine(category.wireValue, category.label, spending.totalMinor, shareAndCount(spending, currency::format))
        },
        format = currency::format,
    )
    section(
        key = "person",
        title = "By person",
        hint = "What each person's part of the bills came to",
        lines = insights.byPerson.map { person -> SpendingLine(person.personKey, person.name, person.shareMinor, billCount(person.bills)) },
        format = currency::format,
    )
    section(
        key = "group",
        title = "By group",
        lines = insights.byGroup.map { (group, spending) ->
            SpendingLine(group?.id.orEmpty(), Exporter.groupName(group), spending.totalMinor, shareAndCount(spending, currency::format))
        },
        format = currency::format,
    )
    item(key = "end") { Spacer(Modifier.height(SvSpacing.xl)) }
}

/** The total of the period and the user's own share of it. */
@Composable
private fun Overview(state: InsightsState, modifier: Modifier = Modifier) {
    val overall = state.insights.overall
    val currency = state.insights.currency
    SvCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
        elevation = SvElevation.Level2,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(SvSpacing.xl)) {
            Text(state.periodName, style = MaterialTheme.typography.titleMedium)
            SvAmount(amountMinor = overall.totalMinor, format = currency::format, style = MaterialTheme.typography.amountLarge)
            Spacer(Modifier.height(SvSpacing.xs))
            Text(
                "Spent on ${billCount(overall.bills)} · Your share ${currency.format(overall.myShareMinor)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** One line of a breakdown: what it is, its amount, and a line about it. */
private class SpendingLine(val key: String, val label: String, val amountMinor: Long, val detail: String)

/** A breakdown under [title], each line with a bar as long as its share of the largest line; nothing when it has no lines. */
private fun LazyListScope.section(key: String, title: String, lines: List<SpendingLine>, format: (Long) -> String, hint: String? = null) {
    if (lines.isEmpty()) return
    val largest = lines.maxOf { it.amountMinor }.coerceAtLeast(1)
    item(key = "$key header") { SvSectionHeader(title, modifier = rowMotion(), hint = hint) }
    itemsIndexed(lines, key = { _, line -> "$key ${line.key}" }) { index, line ->
        SvListRow(position = SvRowPosition.of(index, lines.size), modifier = rowMotion()) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(SvSpacing.md))
                    Text(format(line.amountMinor), style = MaterialTheme.typography.amountSmall)
                }
                Spacer(Modifier.height(SvSpacing.xs))
                Bar(fraction = (line.amountMinor.coerceAtLeast(0).toFloat() / largest).coerceIn(0f, 1f))
                Spacer(Modifier.height(SvSpacing.xs))
                Text(line.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A bar across the row, filled as far as [fraction] of its length. It only repeats the amount beside
 * it, so it says nothing to a screen reader.
 */
@Composable
private fun Bar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun shareAndCount(spending: Spending, format: (Long) -> String): String =
    "Your share ${format(spending.myShareMinor)} · ${billCount(spending.bills)}"

private fun billCount(bills: Int): String = if (bills == 1) "1 bill" else "$bills bills"

private val BAR_HEIGHT = 8.dp
