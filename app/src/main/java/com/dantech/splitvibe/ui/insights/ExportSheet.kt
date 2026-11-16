package com.dantech.splitvibe.ui.insights

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.export.CsvExport
import com.dantech.splitvibe.export.ExportFormat

/**
 * How to export the bills of the period on screen: as CSV files for a spreadsheet app or as a PDF
 * summary, saved where the user picks or sent through the share sheet.
 */
@Composable
internal fun ExportSheet(state: InsightsState, onShare: (ExportFormat) -> Unit, onSave: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.CSV) }
    val count = if (state.bills.size == 1) "The 1 finished bill" else "The ${state.bills.size} finished bills"
    SvBottomSheet(
        title = "Export",
        supportingText = "$count of ${state.periodName}, as files to keep or send.",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = { onSave(format) }) { Text("Save to files") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onShare(format) }) { Text("Share") }
        },
    ) {
        SvChoiceGroup(
            options = listOf("CSV files", "PDF summary"),
            selected = format.ordinal,
            onSelect = { format = ExportFormat.entries[it] },
            modifier = Modifier.padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm),
        )
        Text(
            when (format) {
                ExportFormat.CSV ->
                    "${CsvExport.BILLS}, ${CsvExport.ITEMS} and ${CsvExport.SHARES}, for a spreadsheet app. Numbers are written the way " +
                        "this phone's language writes them, so a spreadsheet app on it reads them right."

                ExportFormat.PDF -> "A summary to read or print: the totals by month, category, person and group, and every bill."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm),
        )
    }
}
