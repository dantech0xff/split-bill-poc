package com.dantech.splitvibe.ui.split

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.designsystem.components.SvBillPhoto
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvWorkingRow
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/** First step of a split: choose the bill photo and say how it should be split. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSplitContent(
    state: SplitUiState,
    aiStatus: AiStatus,
    newCameraTarget: () -> Uri,
    onBack: () -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onSamplePicked: () -> Unit,
    onInstructionChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("New split") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            AnalyzeBar(state = state, onAnalyze = onAnalyze, onCancel = onCancel)
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
                onPhotoPicked = onPhotoPicked,
                onSamplePicked = onSamplePicked,
            )

            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstructionChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy,
                label = { Text("How should it be split?") },
                supportingText = { Text("For example: Split with Minh and Lan. Minh had the beers.") },
                minLines = 2,
            )

            aiStatus.notice?.let { SvMessageCard(text = it, isError = false) }
            // An error that only repeats the notice above says nothing new.
            state.error?.takeIf { it != aiStatus.notice }?.let { error ->
                SvMessageCard(
                    text = error,
                    isError = true,
                    action = if (state.errorOpensSettings) "Open settings" else null,
                    onAction = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(state: SplitUiState, newCameraTarget: () -> Uri, onPhotoPicked: (Uri) -> Unit, onSamplePicked: () -> Unit) {
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraMissing by rememberSaveable { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoPicked(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = cameraTarget
        if (saved && target != null) onPhotoPicked(target)
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
                SvBillPhoto(
                    photo = image,
                    contentDescription = "Bill photo",
                    // From reading the text until the bill is worked out, not while the photo is still opening.
                    scanning = state.isBusy && state.progress != Progress.LOADING_PHOTO,
                )
            } else {
                Text("Add a photo of the bill", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The text is read on this phone. The photo itself is never uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                ) { Text("Take photo") }
                OutlinedButton(
                    enabled = !state.isBusy,
                    onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) { Text("Choose photo") }
            }
            TextButton(enabled = !state.isBusy, onClick = onSamplePicked) { Text("Use the sample bill") }

            if (cameraMissing) {
                Text(
                    "No camera app is available on this device. Choose a photo instead.",
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
                    Progress.LOADING_PHOTO -> "Opening the photo…"
                    Progress.READING_TEXT -> "Reading the bill on this phone…"
                    Progress.ASKING_CLAUDE -> "Claude is working out the bill…"
                    Progress.IDLE -> ""
                },
                cancelLabel = "Cancel",
                onCancel = onCancel,
            )
        } else {
            SvPrimaryButton("Read and split the bill", onClick = onAnalyze, enabled = state.photo != null)
        }
    }
}
