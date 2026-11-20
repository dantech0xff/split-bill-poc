package com.dantech.splitvibe.ui.split

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.designsystem.components.SvBillPhoto
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvWorkingRow
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import kotlinx.coroutines.launch

/**
 * First step of a split: scan or photograph the bill and say how it should be split, or type the
 * bill in by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSplitContent(
    state: SplitUiState,
    aiStatus: AiStatus,
    newCameraTarget: () -> Uri,
    /** What to launch to scan a bill, or null when this phone cannot scan. */
    scanIntent: suspend (Activity) -> IntentSender?,
    onBack: () -> Unit,
    /** The pages of one bill, in order. */
    onPagesPicked: (List<Uri>) -> Unit,
    onSamplePicked: () -> Unit,
    onManualEntry: () -> Unit,
    onInstructionChanged: (String) -> Unit,
    /** Reads the bill, with the default instruction the field shows until the user types. */
    onAnalyze: (defaultInstruction: String) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
) {
    val defaultInstruction = stringResource(R.string.split_default_instruction)
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        bottomBar = {
            AnalyzeBar(state = state, onAnalyze = { onAnalyze(defaultInstruction) }, onCancel = onCancel)
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
            PhotoSection(
                state = state,
                newCameraTarget = newCameraTarget,
                scanIntent = scanIntent,
                onPagesPicked = onPagesPicked,
                onSamplePicked = onSamplePicked,
                onManualEntry = onManualEntry,
            )

            OutlinedTextField(
                value = state.instruction ?: defaultInstruction,
                onValueChange = onInstructionChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
                label = { Text(stringResource(R.string.split_instruction_label)) },
                supportingText = { Text(stringResource(R.string.split_instruction_hint)) },
                minLines = 2,
            )

            aiStatus.notice?.let { SvMessageCard(text = it.resolve(), isError = false) }
            // An error that only repeats the notice above says nothing new.
            state.error?.takeIf { it != aiStatus.notice }?.let { error ->
                SvMessageCard(
                    text = error.resolve(),
                    isError = true,
                    action = when {
                        state.errorOpensPro -> stringResource(R.string.common_get_pro)
                        state.errorOpensSettings -> stringResource(R.string.common_open_settings)
                        else -> null
                    },
                    onAction = if (state.errorOpensPro) onOpenPro else onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(
    state: SplitUiState,
    newCameraTarget: () -> Uri,
    scanIntent: suspend (Activity) -> IntentSender?,
    onPagesPicked: (List<Uri>) -> Unit,
    onSamplePicked: () -> Unit,
    onManualEntry: () -> Unit,
) {
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraMissing by rememberSaveable { mutableStateOf(false) }
    var scannerFailed by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onPagesPicked(DocumentScanner.pagesOf(result))
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(DocumentScanner.MAX_PAGES)) { uris ->
        onPagesPicked(uris)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = cameraTarget
        if (saved && target != null) onPagesPicked(listOf(target))
    }

    SvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SvSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            val photo = state.photo
            if (photo != null) {
                val image = remember(photo) { photo.asImageBitmap() }
                val pages = state.pages.size
                SvBillPhoto(
                    photo = image,
                    contentDescription = if (pages > 1) {
                        plural(R.plurals.split_photo_first_page, pages).resolve()
                    } else {
                        stringResource(R.string.split_photo)
                    },
                    // From reading the text until the bill is worked out, not while the photo is still opening.
                    scanning = state.isBusy && state.progress != Progress.LOADING_PHOTO,
                )
                if (pages > 1) {
                    Text(plural(R.plurals.split_pages_as_one, pages).resolve(), style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Text(stringResource(R.string.split_add_the_bill), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.split_add_the_bill_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The scanner stays offered after it failed once: it may only have been getting ready.
            if (activity != null) {
                FilledTonalButton(
                    enabled = !state.isBusy,
                    onClick = {
                        scope.launch {
                            val intent = scanIntent(activity)
                            scannerFailed = intent == null
                            if (intent != null) {
                                try {
                                    scan.launch(IntentSenderRequest.Builder(intent).build())
                                } catch (e: ActivityNotFoundException) {
                                    scannerFailed = true
                                }
                            }
                        }
                    },
                ) { Text(stringResource(if (photo == null) R.string.split_scan else R.string.split_scan_again)) }
            }

            // A flow row: with large text the second button moves to its own line instead of wrapping its label.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm, Alignment.CenterHorizontally)) {
                OutlinedButton(
                    enabled = !state.isBusy,
                    onClick = {
                        val target = newCameraTarget()
                        cameraTarget = target
                        try {
                            takePhoto.launch(target)
                            cameraMissing = false
                        } catch (e: ActivityNotFoundException) {
                            cameraMissing = true
                        }
                    },
                ) { Text(stringResource(R.string.split_take_photo)) }
                OutlinedButton(
                    enabled = !state.isBusy,
                    onClick = {
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) { Text(stringResource(R.string.split_choose_photos)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm, Alignment.CenterHorizontally)) {
                TextButton(enabled = !state.isBusy, onClick = onSamplePicked) { Text(stringResource(R.string.split_use_sample)) }
                TextButton(enabled = !state.isBusy, onClick = onManualEntry) { Text(stringResource(R.string.split_type_by_hand)) }
            }

            if (scannerFailed) {
                Text(
                    stringResource(R.string.split_scanner_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cameraMissing) {
                Text(
                    stringResource(R.string.split_no_camera),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AnalyzeBar(state: SplitUiState, onAnalyze: () -> Unit, onCancel: () -> Unit) {
    SvBottomBar {
        if (state.isBusy) {
            SvWorkingRow(
                status = when (state.progress) {
                    Progress.LOADING_PHOTO -> stringResource(R.string.split_opening_photo)
                    Progress.READING_TEXT -> stringResource(R.string.split_reading_on_phone)
                    Progress.ASKING_CLAUDE -> stringResource(R.string.split_reading_your_bill)
                    Progress.IDLE -> ""
                },
                cancelLabel = stringResource(R.string.common_cancel),
                onCancel = onCancel,
            )
        } else {
            SvPrimaryButton(stringResource(R.string.split_read_and_split), onClick = onAnalyze, enabled = state.pages.isNotEmpty())
        }
    }
}
