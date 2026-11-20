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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvActionTile
import com.dantech.splitvibe.designsystem.components.SvBillPhoto
import com.dantech.splitvibe.designsystem.components.SvBillPhotoPlaceholder
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.components.SvWorkingRow
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.llm.MAX_INSTRUCTION_CHARS
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import kotlinx.coroutines.launch

/**
 * First step of a split: scan or photograph the bill and say how it should be split, or type the
 * bill in by hand. While the bill is read, the main action at the bottom gives way to the floating
 * bar with what is happening and a way to stop it.
 */
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
        // The instruction is typed in here, so the page and its bar end above the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            SvTopBar(title = stringResource(R.string.split_new_title), backLabel = stringResource(R.string.common_back), onBack = onBack)
        },
        bottomBar = {
            AnalyzeBar(state = state, onAnalyze = { onAnalyze(defaultInstruction) }, onCancel = onCancel)
        },
    ) { padding ->
        // The page ends above the main action, so nothing scrolls under it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            PhotoSection(
                state = state,
                newCameraTarget = newCameraTarget,
                scanIntent = scanIntent,
                onPagesPicked = onPagesPicked,
                onSamplePicked = onSamplePicked,
                onManualEntry = onManualEntry,
            )

            aiStatus.notice?.let { SvMessageCard(text = it.resolve(), isError = false) }

            SvTextField(
                value = state.instruction ?: defaultInstruction,
                onValueChange = onInstructionChanged,
                label = stringResource(R.string.split_instruction_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SvSpacing.xs),
                enabled = !state.isBusy,
                supportingText = stringResource(R.string.split_instruction_hint),
                singleLine = false,
                minLines = INSTRUCTION_LINES,
                // The view model keeps no more than the AI proxy takes, so the field may hold as much.
                maxLength = MAX_INSTRUCTION_CHARS,
                titleLabel = true,
            )

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

/**
 * The photo of the bill, or the tray that asks for one, and the ways to add it: scanning, a photo
 * taken or chosen, the sample bill, or typing the bill in by hand.
 */
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

    Column(verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
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
                Text(
                    plural(R.plurals.split_pages_as_one, pages).resolve(),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            SvBillPhotoPlaceholder(
                title = stringResource(R.string.split_add_the_bill),
                text = stringResource(R.string.split_add_the_bill_hint),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            val tile = Modifier.weight(1f)
            // The scanner stays offered after it failed once: it may only have been getting ready.
            if (activity != null) {
                SvActionTile(
                    text = stringResource(if (photo == null) R.string.split_scan else R.string.split_scan_again),
                    icon = SvActionIcons.Scan,
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
                    modifier = tile,
                    enabled = !state.isBusy,
                    prominent = true,
                )
            }
            SvActionTile(
                text = stringResource(R.string.split_take_photo),
                icon = SvActionIcons.Camera,
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
                modifier = tile,
                enabled = !state.isBusy,
            )
            SvActionTile(
                text = stringResource(R.string.split_choose_photos),
                icon = SvActionIcons.Photos,
                onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = tile,
                enabled = !state.isBusy,
            )
        }

        // A flow row: with large text the second action moves to its own line instead of wrapping its label.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SvSpacing.xl, Alignment.CenterHorizontally),
        ) {
            SvTextAction(stringResource(R.string.split_use_sample), onClick = onSamplePicked, enabled = !state.isBusy)
            SvTextAction(stringResource(R.string.split_type_by_hand), onClick = onManualEntry, enabled = !state.isBusy)
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

/**
 * The main action, reading and splitting the bill, as the violet pill at the bottom of the page; while
 * the bill is read, the floating bar with what is happening and a way to stop it takes its place.
 */
@Composable
private fun AnalyzeBar(state: SplitUiState, onAnalyze: () -> Unit, onCancel: () -> Unit) {
    if (state.isBusy) {
        SvBottomBar {
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
        }
    } else {
        // Where the floating bar puts its main action, so that the action does not move when the bar comes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.xl),
        ) {
            SvPrimaryButton(
                stringResource(R.string.split_read_and_split),
                onClick = onAnalyze,
                enabled = state.pages.isNotEmpty(),
                icon = SvActionIcons.Spark,
            )
        }
    }
}

/** How tall the field of the instruction starts, as the approved design draws it. */
private const val INSTRUCTION_LINES = 2

private val TILE_GAP = 10.dp
