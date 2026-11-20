package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dantech.splitvibe.designsystem.motion.LocalSvSharedTransition
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A question that interrupts the screen until it is answered, such as whether to leave a group: a
 * card over the dimmed screen with the [title], the [text] that explains what will happen, optional
 * [content] such as a tick box or a field, and two buttons of equal width side by side, or one above
 * the other when a label does not fit on one line. [dismissLabel] is the button that changes nothing,
 * such as "Cancel", and [confirmLabel] the one that goes ahead; leave out [dismissLabel] for a notice
 * that has only one button, such as "OK". [onDismiss] runs for the button that changes nothing, for
 * back, and for a tap outside the card.
 *
 * Pass [irreversible] when going ahead cannot be undone, such as deleting the account: the button
 * that goes ahead is then red. [confirmEnabled] keeps it from being used until the content is
 * complete, such as a name that was not typed yet. The title is a heading for a screen reader, and
 * the text and the content scroll when larger text makes them taller than the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SvDialog(
    title: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
    irreversible: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        // As wide as the content of a screen, rather than the narrower width of the platform's dialogs.
        modifier = modifier
            .padding(horizontal = SvSpacing.screen)
            .widthIn(max = MAX_WIDTH),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.largeIncreased,
            color = SvElevation.Level1.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            // The dialog lies in a window of its own, outside every transition between screens, so
            // nothing in it may take part in one. What is in it takes the quiet colours of a card.
            CompositionLocalProvider(LocalSvSharedTransition provides null, LocalSvGround provides SvGround.Card) {
                DialogContent(title, text, content, confirmLabel, onConfirm, confirmEnabled, irreversible, dismissLabel, onDismiss)
            }
        }
    }
}

@Composable
private fun DialogContent(
    title: String?,
    text: String?,
    content: (@Composable ColumnScope.() -> Unit)?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    irreversible: Boolean,
    dismissLabel: String?,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ground = SvGround.Card
    Column(modifier = Modifier.padding(SvSpacing.xl), verticalArrangement = Arrangement.spacedBy(SvSpacing.lg)) {
        if (title != null) {
            Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        }
        if (text != null || content != null) {
            // Takes what room is left between the title and the buttons, and scrolls in it.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
            ) {
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
                content?.invoke(this)
            }
        }
        DialogButtons(
            dismiss = dismissLabel?.let { label ->
                {
                    DialogButton(
                        label,
                        onDismiss,
                        ButtonDefaults.buttonColors(containerColor = ground.quiet, contentColor = ground.onQuiet),
                    )
                }
            },
            confirm = {
                DialogButton(
                    confirmLabel,
                    onConfirm,
                    if (irreversible) {
                        ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    enabled = confirmEnabled,
                )
            },
        )
    }
}

/**
 * The buttons of a dialog side by side at equal width while each label fits on one line there, and
 * one above the other across the full width when one does not, as with large text, so that no label
 * is squeezed into a narrow pill. Stacked, the button that goes ahead is on top.
 */
@Composable
private fun DialogButtons(dismiss: (@Composable () -> Unit)?, confirm: @Composable () -> Unit) {
    Layout(
        content = {
            dismiss?.invoke()
            confirm()
        },
    ) { measurables, constraints ->
        val gap = SvSpacing.sm.roundToPx()
        val gaps = gap * (measurables.size - 1)
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } +
                gaps
        }
        val share = (width - gaps) / measurables.size
        if (measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= share }) {
            // One height for both, so that they line up whatever their labels.
            val height = measurables.maxOf { it.minIntrinsicHeight(share) }
            val placeables = measurables.map { it.measure(Constraints.fixed(share, height)) }
            layout(width, height) {
                placeables.forEachIndexed { index, placeable -> placeable.placeRelative(index * (share + gap), 0) }
            }
        } else {
            val placeables = measurables.reversed().map { it.measure(Constraints.fixedWidth(width)) }
            val height = placeables.sumOf { it.height } + gap * (placeables.size - 1)
            layout(width, height) {
                var top = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, top)
                    top += placeable.height + gap
                }
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit, colors: ButtonColors, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = BUTTON_HEIGHT),
        enabled = enabled,
        shape = SvShapes.pill,
        colors = colors,
    ) {
        Text(label, style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
    }
}

private val BUTTON_HEIGHT = 52.dp

/** As Material's dialogs, so that the card does not stretch across a tablet. */
private val MAX_WIDTH = 560.dp
