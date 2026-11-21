package com.dantech.splitvibe.ui.insights

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBarChart
import com.dantech.splitvibe.designsystem.components.SvBesideOrBelow
import com.dantech.splitvibe.designsystem.components.SvChartBar
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvIconTile
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietIconTile
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvStatBar
import com.dantech.splitvibe.designsystem.motion.rowMotion
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Spending
import com.dantech.splitvibe.domain.monthText
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.export.Exporter
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.labelRes
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.text.sentences
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.common.style

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
)

/**
 * What the user's finished bills add up to in their local currency, for the period they choose:
 * all together, by month, or for all time by year, as a chart and as a list, by category, by person
 * and by group, with notes on what the totals leave out. The bills of the period can be exported as
 * files, with the button at the end of the list, where the export then says how it went. It is one
 * of the main screens, with the navigation bar the app passes as [bottomBar] and no back button.
 */
@Composable
fun InsightsScreen(state: InsightsState?, export: ExportStatus, actions: InsightsActions, bottomBar: @Composable () -> Unit = {}) {
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
        topBar = { SvLargeTopBar(title = stringResource(R.string.home_insights)) },
        bottomBar = bottomBar,
    ) { padding ->
        // The list ends above the navigation bar, so nothing scrolls under it, and the export at its
        // end scrolls clear of the bar.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = SvSpacing.sm, bottom = SvSpacing.lg),
        ) {
            item(key = "period") {
                SvChoiceGroup(
                    options = InsightsPeriod.entries.map { stringResource(it.label) },
                    selected = (state?.period ?: InsightsPeriod.THIS_MONTH).ordinal,
                    onSelect = { actions.onChoosePeriod(InsightsPeriod.entries[it]) },
                    modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md),
                )
            }
            // How an export ended stands right above its button, where it was started; a period without
            // bills has no button, and then it stands at the top.
            val exportShown = state != null && state.anyFinished && state.bills.isNotEmpty()
            export.message?.takeUnless { exportShown }?.let { message ->
                item(key = "message") {
                    ExportOutcome(
                        message = message,
                        onDismiss = actions.onDismissMessage,
                        modifier = rowMotion().padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md),
                    )
                }
            }
            when {
                state == null -> item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }

                !state.anyFinished -> item(key = "empty") {
                    SvEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        text = stringResource(R.string.insights_empty),
                        modifier = rowMotion(),
                    )
                }

                else -> insights(state, export = export, onExport = { exporting = true }, onDismissMessage = actions.onDismissMessage)
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

private fun LazyListScope.insights(state: InsightsState, export: ExportStatus, onExport: () -> Unit, onDismissMessage: () -> Unit) {
    val insights = state.insights
    val currency = insights.currency
    item(key = "overview") {
        Overview(state, modifier = rowMotion().padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md))
    }
    itemsIndexed(state.notes, key = { index, _ -> "note $index" }) { _, note ->
        SvMessageCard(
            note.resolve(),
            isError = false,
            modifier = rowMotion().padding(start = SvSpacing.screen, end = SvSpacing.screen, bottom = SvSpacing.md),
        )
    }
    if (state.bills.isEmpty()) {
        item(key = "none") {
            SvEmptyState(Icons.Default.ShoppingCart, stringResource(R.string.insights_none_in_period), modifier = rowMotion())
        }
        return
    }
    spans(state)
    section(
        key = "category",
        title = R.string.insights_by_category,
        lines = insights.byCategory.map { (category, spending) ->
            SpendingLine(
                category.wireValue,
                text(category.labelRes),
                spending.totalMinor,
                shareAndCount(spending, currency::format),
                Mark.Category(category),
            )
        },
        format = currency::format,
    )
    section(
        key = "person",
        title = R.string.insights_by_person,
        hint = R.string.insights_by_person_hint,
        lines = insights.byPerson.map { person ->
            SpendingLine(person.personKey, personName(person), person.shareMinor, billCount(person.bills), Mark.Initials)
        },
        format = currency::format,
    )
    section(
        key = "group",
        title = R.string.insights_by_group,
        lines = insights.byGroup.map { (group, spending) ->
            val name = group?.name?.let(UiText::Raw) ?: text(R.string.insights_no_group)
            SpendingLine(
                group?.id.orEmpty(),
                name,
                spending.totalMinor,
                shareAndCount(spending, currency::format),
                if (group == null) Mark.NoGroup else Mark.Initials,
            )
        },
        format = currency::format,
    )
    export.message?.let { message ->
        item(key = "message") {
            ExportOutcome(
                message = message,
                onDismiss = onDismissMessage,
                modifier = rowMotion().padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.xl),
            )
        }
    }
    // At the end of the list, as the approved design has it: one bar at the bottom of a main screen is the navigation bar.
    item(key = "export") {
        SvPrimaryButton(
            text = stringResource(R.string.insights_export_bills),
            onClick = onExport,
            enabled = !export.working,
            icon = SvActionIcons.Export,
            modifier = rowMotion().padding(
                start = SvSpacing.screen,
                end = SvSpacing.screen,
                top = if (export.message == null) SvSpacing.xl else SvSpacing.md,
            ),
        )
    }
}

/** How the last export ended, which TalkBack reads out as it appears, with the button that puts it away. */
@Composable
private fun ExportOutcome(message: ExportMessage, onDismiss: () -> Unit, modifier: Modifier) {
    SvMessageCard(
        text = message.text.resolve(),
        isError = message.isError,
        action = stringResource(R.string.common_ok),
        onAction = onDismiss,
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    )
}

/** The total of the period and the user's own share of it. */
@Composable
private fun Overview(state: InsightsState, modifier: Modifier = Modifier) {
    val overall = state.insights.overall
    val currency = state.insights.currency
    val parts = currency.formatParts(overall.totalMinor)
    SvHero(
        modifier = modifier,
        label = { Text(state.periodName.resolve()) },
        amount = {
            SvHeroAmount(
                prefix = parts.prefix,
                number = parts.number,
                suffix = parts.suffix,
                spokenText = currency.format(overall.totalMinor),
            )
        },
    ) {
        Text(stringResource(R.string.insights_spent_on, billCount(overall.bills).resolve(), currency.format(overall.myShareMinor)))
    }
}

/**
 * The months of the period, or for all time its years: a chart of what each came to, which says in
 * words which were the highest, and below it each one that has bills, the latest first, with its
 * total, the user's share and how many bills it had. A period of one month, or all time within one
 * year, has no chart, as it would compare that one with nothing.
 */
private fun LazyListScope.spans(state: InsightsState) {
    val spans = state.spans
    val lines = spans.filter { it.spending.bills > 0 }.reversed()
    if (lines.isEmpty()) return
    val currency = state.insights.currency
    val chart = spans.size > 1
    // The chart is the first part of the card that holds the lines.
    val before = if (chart) 1 else 0
    val rows = lines.size + before
    val title = if (spans.first().span is Span.Year) R.string.insights_by_year else R.string.insights_by_month
    item(key = "span header") { SvSectionHeader(stringResource(title), modifier = rowMotion()) }
    if (chart) {
        item(key = "span chart") {
            SvListRow(position = SvRowPosition.of(0, rows), modifier = rowMotion()) {
                SvBarChart(
                    bars = spans.map { SvChartBar(barLabelOf(it.span), it.spending.totalMinor.toFloat()) },
                    description = chartDescription(spans).resolve(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = SvSpacing.sm),
                    // The month or the year that is running, which always ends the period.
                    highlighted = spans.lastIndex,
                )
            }
        }
    }
    itemsIndexed(lines, key = { _, line -> keyOf(line.span) }) { index, line ->
        SvListRow(position = SvRowPosition.of(index + before, rows), modifier = rowMotion()) {
            // Beside the name and the line under it while their longest words fit there, and below them when not.
            SvBesideOrBelow(
                first = {
                    Column {
                        Text(nameOf(line.span), style = MaterialTheme.typography.bodyMediumEmphasized)
                        Text(
                            shareAndCount(line.spending, currency::format).resolve(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                second = { Text(currency.format(line.spending.totalMinor), style = MaterialTheme.typography.amountSmall) },
                modifier = Modifier.weight(1f),
                secondBelowAt = Alignment.Start,
                stackedGap = SvSpacing.xs,
            )
        }
    }
}

/** What [span] is called in a line and in the words of the chart: "November 2026", or "2026". */
private fun nameOf(span: Span): String = when (span) {
    is Span.Month -> Exporter.monthName(span.month)
    is Span.Year -> span.year.toString()
}

/** What stands under the bar of [span]: the first letter of its month, or its year. */
private fun barLabelOf(span: Span): String = when (span) {
    is Span.Month -> monthText(span.month).take(1)
    is Span.Year -> span.year.toString()
}

/** The key of the line of [span] in the list. */
private fun keyOf(span: Span): String = when (span) {
    is Span.Month -> "month ${span.month}"
    is Span.Year -> "year ${span.year}"
}

/**
 * What the chart of [spans] says to a screen reader: the months or the years it runs over, and the
 * one that came to most, then the one after it, such as "Spending by month from December 2025 to
 * November 2026. November 2026 is the highest, then October 2026." Of two with the same total the
 * later comes first. [spans] holds one at least.
 */
internal fun chartDescription(spans: List<SpanSpending>): UiText {
    require(spans.isNotEmpty()) { "A chart of nothing" }
    val words = if (spans.first().span is Span.Year) R.string.insights_chart_years else R.string.insights_chart
    val range = text(words, nameOf(spans.first().span), nameOf(spans.last().span))
    val highest = spans.withIndex()
        .filter { (_, span) -> span.spending.totalMinor > 0 }
        .sortedWith(compareByDescending<IndexedValue<SpanSpending>> { it.value.spending.totalMinor }.thenByDescending { it.index })
        .map { nameOf(it.value.span) }
    val most = when (highest.size) {
        0 -> null
        1 -> text(R.string.insights_chart_highest, highest[0])
        else -> text(R.string.insights_chart_highest_then, highest[0], highest[1])
    }
    return sentences(range, most)
}

/** What stands at the start of a line of a breakdown. */
private sealed interface Mark {
    /** The tile of a category, whose colour the bar of the line takes too. */
    class Category(val category: BillCategory) : Mark

    /** The initials of the person or the group the line is about. */
    data object Initials : Mark

    /** A blank tile with a line across, for the bills in no group. */
    data object NoGroup : Mark
}

/** One line of a breakdown: what it is, its amount, a line about it, and what stands at its start. */
private class SpendingLine(val key: String, val label: UiText, val amountMinor: Long, val detail: UiText, val mark: Mark)

/** A breakdown under [title], each line with a bar as long as its share of the largest line; nothing when it has no lines. */
private fun LazyListScope.section(
    key: String,
    @StringRes title: Int,
    lines: List<SpendingLine>,
    format: (Long) -> String,
    @StringRes hint: Int? = null,
) {
    if (lines.isEmpty()) return
    val largest = lines.maxOf { it.amountMinor }.coerceAtLeast(1)
    item(key = "$key header") { SvSectionHeader(stringResource(title), modifier = rowMotion(), hint = hint?.let { stringResource(it) }) }
    itemsIndexed(lines, key = { _, line -> "$key ${line.key}" }) { index, line ->
        SpendingRow(
            line = line,
            fraction = (line.amountMinor.coerceAtLeast(0).toFloat() / largest).coerceIn(0f, 1f),
            format = format,
            position = SvRowPosition.of(index, lines.size),
            modifier = rowMotion(),
        )
    }
}

@Composable
private fun SpendingRow(line: SpendingLine, fraction: Float, format: (Long) -> String, position: SvRowPosition, modifier: Modifier) {
    val label = line.label.resolve()
    val category = (line.mark as? Mark.Category)?.category?.style
    SvListRow(position = position, modifier = modifier) {
        when (line.mark) {
            is Mark.Category -> category?.let { SvIconTile(it.icon, it.color) }
            Mark.Initials -> SvAvatar(label)
            Mark.NoGroup -> SvQuietIconTile(SvActionIcons.None, size = AVATAR_SIZE, blank = true)
        }
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            // Beside the name while its longest word fits there, and below it when not, as with large text.
            SvBesideOrBelow(
                first = { Text(label, style = MaterialTheme.typography.bodyMediumEmphasized) },
                second = { Text(format(line.amountMinor), style = MaterialTheme.typography.amountSmall) },
                secondBelowAt = Alignment.Start,
                stackedGap = SvSpacing.xs,
            )
            // The bar only repeats the amount beside it, so it says nothing to a screen reader.
            SvStatBar(fraction = fraction, description = null, color = category?.color ?: MaterialTheme.colorScheme.onSurface)
            Text(line.detail.resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareAndCount(spending: Spending, format: (Long) -> String): UiText =
    text(R.string.insights_share_and_count, format(spending.myShareMinor), billCount(spending.bills))

private fun billCount(bills: Int): UiText = plural(R.plurals.insights_bill_count, bills)

/** As large as the avatars of the groups beside it, which [SvAvatar] draws at its own size. */
private val AVATAR_SIZE = 40.dp
