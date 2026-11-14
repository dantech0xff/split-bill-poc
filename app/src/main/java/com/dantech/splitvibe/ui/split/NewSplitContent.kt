package com.dantech.splitvibe.ui.split

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** First step of a split: choose the bill photo and say how it should be split. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSplitContent(
    state: SplitUiState,
    hasApiKey: Boolean,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            if (!hasApiKey) {
                MessageCard(
                    text = "An Anthropic API key is needed to work out the bill.",
                    isError = false,
                    action = "Open settings",
                    onAction = onOpenSettings,
                )
            }
            state.error?.let { MessageCard(text = it, isError = true) }
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

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val photo = state.photo
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Bill photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("Add a photo of the bill", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The text is read on this phone. The photo itself is never uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = when (state.progress) {
                        Progress.LOADING_PHOTO -> "Opening the photo…"
                        Progress.READING_TEXT -> "Reading the bill on this phone…"
                        Progress.ASKING_CLAUDE -> "Claude is working out the bill…"
                        Progress.IDLE -> ""
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Button(
                    onClick = onAnalyze,
                    enabled = state.photo != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Read and split the bill") }
            }
        }
    }
}

@Composable
internal fun MessageCard(text: String, isError: Boolean, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) colors.errorContainer else colors.tertiaryContainer,
            contentColor = if (isError) colors.onErrorContainer else colors.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
            }
        }
    }
}
