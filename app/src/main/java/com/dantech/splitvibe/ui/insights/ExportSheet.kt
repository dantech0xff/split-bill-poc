package com.dantech.splitvibe.ui.insights

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.export.CsvExport
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve

/**
 * How to export the bills of the period on screen: as CSV files for a spreadsheet app or as a PDF
 * summary, saved where the user picks or sent through the share sheet.
 */
@Composable
internal fun ExportSheet(state: InsightsState, onShare: (ExportFormat) -> Unit, onSave: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.CSV) }
    SvBottomSheet(
        title = stringResource(R.string.insights_export),
        supportingText = plural(R.plurals.insights_export_hint, state.bills.size, state.bills.size, state.periodName).resolve(),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.insights_save_to_files), onClick = { onSave(format) }, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.insights_share),
                onClick = { onShare(format) },
                modifier = Modifier.weight(1f),
                icon = SvActionIcons.Share,
            )
        },
    ) {
        SvChoiceGroup(
            options = listOf(stringResource(R.string.insights_csv_files), stringResource(R.string.insights_pdf_summary)),
            selected = format.ordinal,
            onSelect = { format = ExportFormat.entries[it] },
            modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
        )
        Text(
            when (format) {
                ExportFormat.CSV -> stringResource(R.string.insights_csv_hint, CsvExport.BILLS, CsvExport.ITEMS, CsvExport.SHARES)
                ExportFormat.PDF -> stringResource(R.string.insights_pdf_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
        )
    }
}
