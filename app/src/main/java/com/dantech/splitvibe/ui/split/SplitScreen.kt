package com.dantech.splitvibe.ui.split

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.splitvibe.designsystem.motion.SvSteps
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.ui.SPLIT_SURFACE

/** The button that sends the requests turns into the mark that confirms them; both carry this name. */
internal const val SEND_SURFACE = "send"

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
    val step = when {
        result != null && sentRequests != null -> SplitStep.Sent(result, sentRequests)
        result != null -> SplitStep.Result(result)
        else -> SplitStep.Input(state)
    }
    val finish = {
        viewModel.startNewSplit()
        onExit()
    }

    // Back belongs to the step on screen, not to the one that is fading out.
    when (step) {
        is SplitStep.Sent -> BackHandler(onBack = finish)

        is SplitStep.Result -> BackHandler(onBack = viewModel::backToInput)

        // While work is running, the first back press cancels it instead of leaving the screen.
        is SplitStep.Input -> BackHandler(enabled = state.isBusy, onBack = viewModel::cancelWork)
    }

    // One surface with the button that opened this screen, so the screen grows out of that button.
    Box(modifier = Modifier.sharedSurface(SPLIT_SURFACE)) {
        SvSteps(step = step, stepOf = { it::class }) { shown ->
            when (shown) {
                is SplitStep.Sent -> SentContent(bill = shown.result.bill, requests = shown.requests, onDone = finish)

                is SplitStep.Result -> ResultContent(
                    result = shown.result,
                    contacts = viewModel.contacts,
                    onBack = viewModel::backToInput,
                    onToggleMe = viewModel::toggleMe,
                    onToggleContact = viewModel::toggleContact,
                    onSetItemSharers = viewModel::setItemSharers,
                    onSendRequests = viewModel::sendRequests,
                )

                is SplitStep.Input -> NewSplitContent(
                    state = shown.state,
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
}

/**
 * A step of the flow with what it shows. A step that is being left is drawn with what it showed
 * last, so it does not change while it fades out.
 */
private sealed interface SplitStep {
    data class Input(val state: SplitUiState) : SplitStep

    data class Result(val result: SplitResultState) : SplitStep

    data class Sent(val result: SplitResultState, val requests: List<PaymentRequest>) : SplitStep
}
