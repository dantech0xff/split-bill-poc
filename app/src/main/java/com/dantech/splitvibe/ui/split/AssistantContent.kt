package com.dantech.splitvibe.ui.split

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.CallsLeft
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvWorkingRow
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve

/** What the assistant on the result screen asks the flow to do. */
class AssistantActions(
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onCancel: () -> Unit,
    val onToggleEdit: (index: Int) -> Unit,
    val onApply: () -> Unit,
    val onDiscard: () -> Unit,
    val onUndo: () -> Unit,
    val onForgetQuestion: () -> Unit,
    val onDismissError: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenPro: () -> Unit,
    val onMic: () -> Unit,
    /** The user's answer to whether the phone's speech service may hear them. */
    val onNetworkVoice: (allowed: Boolean) -> Unit,
    val onMicPermissionAsked: () -> Unit,
    val onMicPermission: (granted: Boolean) -> Unit,
)

/**
 * The field where the user types or says how to change the split, with what the AI made of the
 * last thing sent when it is not a list of changes: a question back, an error, or nothing to change.
 */
@Composable
internal fun AssistantCard(state: AssistantUiState, actions: AssistantActions, modifier: Modifier = Modifier) {
    val voice = state.voice
    val answering = state.question != null
    SvCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            state.notice?.let { SvMessageCard(text = it.resolve(), isError = false) }
            state.question?.let { question ->
                // Read out as it arrives: the answer goes into the field below.
                SvMessageCard(
                    text = question,
                    isError = false,
                    action = stringResource(R.string.assistant_ask_something_else),
                    onAction = actions.onForgetQuestion,
                    modifier = announced,
                )
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = actions.onTextChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.working && !voice.listening,
                label = { Text(stringResource(if (answering) R.string.assistant_your_answer else R.string.assistant_change_the_split)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (voice.available) {
                            IconButton(onClick = actions.onMic, enabled = !state.working) {
                                Icon(
                                    MIC,
                                    contentDescription = stringResource(
                                        if (voice.listening) R.string.assistant_stop_listening else R.string.assistant_say_the_change,
                                    ),
                                    tint = if (voice.listening) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            }
                        }
                        IconButton(onClick = actions.onSend, enabled = state.canSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.assistant_ask_the_ai))
                        }
                    }
                },
                supportingText = {
                    Text(
                        when {
                            voice.listening -> stringResource(R.string.assistant_listening)
                            voice.note != null -> voice.note.resolve()
                            answering -> stringResource(R.string.assistant_answer_hint)
                            else -> stringResource(R.string.assistant_hint, CallsLeft.text(CallsLeft.REVISION))
                        },
                    )
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (state.canSend) actions.onSend() }),
            )

            if (state.working) {
                SvWorkingRow(
                    status = stringResource(R.string.assistant_working),
                    cancelLabel = stringResource(R.string.common_cancel),
                    onCancel = actions.onCancel,
                )
            }
            state.error?.let { error ->
                SvMessageCard(
                    text = error.resolve(),
                    isError = true,
                    action = when {
                        state.errorOpensPro -> stringResource(R.string.common_get_pro)
                        state.errorOpensSettings -> stringResource(R.string.common_open_settings)
                        else -> stringResource(R.string.common_ok)
                    },
                    onAction = when {
                        state.errorOpensPro -> actions.onOpenPro
                        state.errorOpensSettings -> actions.onOpenSettings
                        else -> actions.onDismissError
                    },
                    modifier = announced,
                )
            }
            state.note?.let { Text(it.resolve(), modifier = announced, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * The changes the AI proposed, worked out on the split on screen: each in words with a tick to keep
 * or drop it, why a kept one cannot be applied, and what everyone pays before and after. Nothing
 * changes until the user applies them.
 */
@Composable
internal fun ProposalCard(proposal: ProposalUi, actions: AssistantActions, modifier: Modifier = Modifier) {
    SvCard(modifier = modifier.fillMaxWidth(), elevation = SvElevation.Level2) {
        Column(modifier = Modifier.padding(vertical = SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            Column(modifier = Modifier.padding(horizontal = SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
                Text(
                    stringResource(R.string.assistant_proposed_changes),
                    modifier = announced,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.assistant_you_asked, proposal.instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (proposal.explanation.isNotBlank()) Text(proposal.explanation, style = MaterialTheme.typography.bodyMedium)
            }

            proposal.edits.forEach { edit -> EditRow(edit, onToggle = { actions.onToggleEdit(edit.index) }) }
            if (proposal.unreadable > 0) {
                Note(plural(R.plurals.assistant_unreadable, proposal.unreadable).resolve())
            }

            // A proposal that could not be worked out has no amounts to show.
            if (proposal.shares.isNotEmpty()) {
                Text(
                    stringResource(R.string.split_each_person_pays),
                    modifier = Modifier.padding(start = SvSpacing.lg, end = SvSpacing.lg, top = SvSpacing.sm),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            proposal.shares.forEach { ShareChangeRow(it, proposal.currency) }
            proposal.problems.forEach { problem -> Note(stringResource(R.string.assistant_after_changes, problem), isError = true) }
            proposal.outcome.note?.let { Note(stringResource(it), isError = proposal.outcome.isError) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SvSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm, Alignment.End),
            ) {
                TextButton(onClick = actions.onDiscard) { Text(stringResource(R.string.assistant_discard)) }
                FilledTonalButton(onClick = actions.onApply, enabled = proposal.canApply) { Text(stringResource(R.string.assistant_apply)) }
            }
        }
    }
}

/**
 * Asks for the microphone, and about the phone's speech service, when listening needs them. It
 * sits outside the list, so an answer that comes back while the field is scrolled away still arrives.
 */
@Composable
internal fun AssistantVoicePrompts(voice: VoiceUi, actions: AssistantActions) {
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        actions.onMicPermission(granted)
    }
    LaunchedEffect(voice.askingPermission) {
        if (!voice.askingPermission) return@LaunchedEffect
        askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        actions.onMicPermissionAsked()
    }

    if (voice.askingNetwork) {
        AlertDialog(
            onDismissRequest = { actions.onNetworkVoice(false) },
            title = { Text(stringResource(R.string.assistant_speech_service_title)) },
            text = {
                Text(stringResource(R.string.assistant_speech_service_text))
            },
            confirmButton = { TextButton(onClick = { actions.onNetworkVoice(true) }) { Text(stringResource(R.string.assistant_use_it)) } },
            dismissButton = {
                TextButton(onClick = { actions.onNetworkVoice(false) }) { Text(stringResource(R.string.assistant_type_instead)) }
            },
        )
    }
}

/** One proposed edit with a tick that keeps it. The whole row is one tick box for a screen reader. */
@Composable
private fun EditRow(edit: ProposedEdit, onToggle: () -> Unit) {
    val label = listOfNotNull(
        edit.text,
        if (edit.kept) null else stringResource(R.string.assistant_dropped),
        edit.reason?.let { stringResource(R.string.assistant_cannot_apply_in_sentence, it) },
    ).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = edit.kept, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = label }
            .padding(horizontal = SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = edit.kept, onCheckedChange = null)
        Spacer(Modifier.width(SvSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                edit.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (edit.kept) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (edit.kept) null else TextDecoration.LineThrough,
            )
            edit.reason?.let {
                Text(
                    stringResource(R.string.assistant_cannot_apply, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** One person's share before the changes and after them; a person the changes add or take off has none on one side. */
@Composable
private fun ShareChangeRow(change: ShareChange, currency: BillCurrency) {
    val before = change.beforeMinor?.takeIf { change.onBillBefore }?.let(currency::format)
    val after = change.afterMinor?.takeIf { change.onBillAfter }?.let(currency::format)
    val name = change.name.resolve()
    val notWorkedOut = stringResource(R.string.assistant_part_not_worked_out)
    val said = when {
        !change.onBillBefore -> stringResource(R.string.assistant_joins, name, after ?: notWorkedOut)
        !change.onBillAfter -> stringResource(R.string.assistant_taken_off, name)
        !change.changed -> stringResource(R.string.assistant_still_pays, name, after ?: notWorkedOut)
        else -> stringResource(R.string.assistant_pays_instead, name, after ?: notWorkedOut, before ?: notWorkedOut)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.xs)
            .clearAndSetSemantics { contentDescription = said },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SvAvatar(name)
        Spacer(Modifier.width(SvSpacing.md))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(before ?: NONE, style = MaterialTheme.typography.amountSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = SvSpacing.xs)
                .size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            after ?: NONE,
            style = MaterialTheme.typography.amountMedium,
            color = if (change.changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Note(text: String, isError: Boolean = false) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = SvSpacing.lg),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Read out by TalkBack when it appears or changes: what the AI made of what was sent. */
private val announced = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }

/** Shown in place of an amount a person does not have on one side of the change. */
private const val NONE = "—"

/** The microphone of Material's icons, which the small set of icons the app ships does not hold. */
private val MIC = materialIcon(name = "Filled.Mic") {
    materialPath {
        moveTo(12f, 14f)
        curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
        lineTo(15f, 5f)
        curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
        reflectiveCurveTo(9f, 3.34f, 9f, 5f)
        verticalLineToRelative(6f)
        curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
        close()
        moveTo(17.3f, 11f)
        curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
        reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
        lineTo(5f, 11f)
        curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
        lineTo(11f, 21f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(-3.28f)
        curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
        horizontalLineToRelative(-1.7f)
        close()
    }
}
