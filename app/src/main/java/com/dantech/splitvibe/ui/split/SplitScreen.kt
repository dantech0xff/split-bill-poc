package com.dantech.splitvibe.ui.split

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.designsystem.motion.SvSteps
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.ui.SPLIT_SURFACE
import com.dantech.splitvibe.ui.editor.BillEditorContent
import kotlinx.coroutines.Dispatchers

/** The button that sends the requests turns into the mark that confirms them; both carry this name. */
internal const val SEND_SURFACE = "send"

/**
 * The split-bill flow. Which step is shown follows from the state: no bill yet means the photo and
 * instruction step, a bill being edited the editor, a bill the split, and sent requests the
 * confirmation.
 */
@Composable
fun SplitScreen(
    viewModel: SplitBillViewModel,
    aiStatus: AiStatus,
    newCameraTarget: () -> Uri,
    /** What to launch to scan a bill, or null when this phone cannot scan. */
    scanIntent: suspend (Activity) -> IntentSender?,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Collected without waiting for the next frame, so a field always shows what was just typed
    // into it and never a value from a keystroke before.
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val people by viewModel.people.collectAsStateWithLifecycle()
    val result = state.result
    val sentRequests = state.sentRequests
    val step = when {
        result != null && sentRequests != null -> SplitStep.Sent(result, sentRequests)
        result != null && state.editing -> SplitStep.Edit(result)
        result != null -> SplitStep.Result(result)
        else -> SplitStep.Input(state)
    }
    // The split is left as it is: the screen shrinks back into the button that opened it showing
    // what it showed, and the next tap on that button starts a new split.
    val finish = onExit
    // A bill opened from the list goes back there; a new one goes back to its photo.
    val leaveResult = if (state.openedFromHistory) finish else viewModel::backToInput

    // Back belongs to the step on screen, not to the one that is fading out.
    when (step) {
        is SplitStep.Sent -> BackHandler(onBack = finish)

        is SplitStep.Edit -> BackHandler(onBack = viewModel::doneEditing)

        is SplitStep.Result -> BackHandler(onBack = leaveResult)

        // While work is running, the first back press cancels it instead of leaving the screen.
        is SplitStep.Input -> BackHandler(enabled = state.isBusy, onBack = viewModel::cancelWork)
    }

    val resultActions =
        ResultActions(
            onBack = leaveResult,
            onEditBill = viewModel::startEditing,
            onAddPerson = viewModel::addPerson,
            onContactPicked = viewModel::onContactPicked,
            onRenamePerson = viewModel::renamePerson,
            onRemovePerson = viewModel::removePerson,
            onToggleMe = viewModel::toggleMeSharing,
            onSetMode = viewModel::setMode,
            onSetWholeValue = viewModel::setWholeValue,
            onSetItemDivision = viewModel::setItemDivision,
            onSetPayments = viewModel::setPayments,
            onSetExchangeRate = viewModel::setExchangeRate,
            onUseDayRate = viewModel::useDayRate,
            onSendRequests = viewModel::sendRequests,
            onFinish = {
                viewModel.finish()
                finish()
            },
        )

    // One surface with the button that opened this screen, so the screen grows out of that button.
    Box(modifier = Modifier.sharedSurface(SPLIT_SURFACE)) {
        SvSteps(step = step, stepOf = SplitStep::name) { shown ->
            when (shown) {
                is SplitStep.Sent -> SentContent(result = shown.result, requests = shown.requests, onDone = finish)

                is SplitStep.Edit -> BillEditorContent(
                    bill = shown.result.bill,
                    onEdit = viewModel::editBill,
                    onDone = viewModel::doneEditing,
                )

                is SplitStep.Result -> ResultContent(result = shown.result, people = people, actions = resultActions)

                is SplitStep.Input -> NewSplitContent(
                    state = shown.state,
                    aiStatus = aiStatus,
                    newCameraTarget = newCameraTarget,
                    scanIntent = scanIntent,
                    onBack = {
                        viewModel.cancelWork()
                        onExit()
                    },
                    onPagesPicked = viewModel::onPagesPicked,
                    onSamplePicked = viewModel::onSamplePicked,
                    onManualEntry = viewModel::startManualEntry,
                    onInstructionChanged = viewModel::onInstructionChanged,
                    onAnalyze = viewModel::analyze,
                    onCancel = viewModel::cancelWork,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            confirmButton = { TextButton(onClick = viewModel::dismissNotice) { Text("OK") } },
            text = { Text(notice) },
        )
    }
}

/**
 * A step of the flow with what it shows. A step that is being left is drawn with what it showed
 * last, so it does not change while it fades out.
 */
private sealed interface SplitStep {
    /** Fixed for each step, so that what is saved inside a step comes back after the system ends the app. */
    val name: String

    data class Input(val state: SplitUiState) : SplitStep {
        override val name get() = "input"
    }

    data class Edit(val result: SplitResultState) : SplitStep {
        override val name get() = "edit"
    }

    data class Result(val result: SplitResultState) : SplitStep {
        override val name get() = "result"
    }

    data class Sent(val result: SplitResultState, val requests: List<PaymentRequest>) : SplitStep {
        override val name get() = "sent"
    }
}
