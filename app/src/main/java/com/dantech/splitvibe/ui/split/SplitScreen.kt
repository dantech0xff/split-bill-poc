package com.dantech.splitvibe.ui.split

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The split-bill flow. Which step is shown follows from the state: no result yet means the photo
 * and instruction step, a result means the bill and people step, and sent requests mean the
 * confirmation.
 */
@Composable
fun SplitScreen(
    viewModel: SplitBillViewModel,
    hasApiKey: Boolean,
    newCameraTarget: () -> Uri,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result = state.result
    val sentRequests = state.sentRequests

    when {
        result != null && sentRequests != null -> {
            val finish = {
                viewModel.startNewSplit()
                onExit()
            }
            BackHandler(onBack = finish)
            SentContent(bill = result.bill, requests = sentRequests, onDone = finish)
        }

        result != null -> {
            BackHandler(onBack = viewModel::backToInput)
            ResultContent(
                result = result,
                contacts = viewModel.contacts,
                onBack = viewModel::backToInput,
                onToggleMe = viewModel::toggleMe,
                onToggleContact = viewModel::toggleContact,
                onSetItemSharers = viewModel::setItemSharers,
                onSendRequests = viewModel::sendRequests,
            )
        }

        else -> {
            // While work is running, the first back press cancels it instead of leaving the screen.
            BackHandler(enabled = state.isBusy, onBack = viewModel::cancelWork)
            NewSplitContent(
                state = state,
                hasApiKey = hasApiKey,
                newCameraTarget = newCameraTarget,
                onBack = {
                    viewModel.cancelWork()
                    onExit()
                },
                onPhotoPicked = viewModel::onPhotoPicked,
                onSamplePicked = viewModel::onSamplePicked,
                onInstructionChanged = viewModel::onInstructionChanged,
                onAnalyze = viewModel::analyze,
                onCancel = viewModel::cancelWork,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}
