package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing

/**
 * The top bar of a main screen, one that the navigation bar leads to: its large title, and at the
 * end what can be done on the screen as a whole, such as opening the settings. The title wraps
 * onto more lines rather than being cut off.
 *
 * It stays in place while the content scrolls under it, and keeps clear of the status bar by
 * [windowInsets]; pass `WindowInsets(0)` where something around it already does.
 */
@Composable
fun SvLargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopBarInsets,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BarRow(modifier = modifier, windowInsets = windowInsets, top = SvSpacing.md) {
        Text(
            title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.displaySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * The top bar of a screen opened from another one: a round back button that says [backLabel] to a
 * screen reader, the [title], and at the end an optional [action], one more thing to do with what
 * the screen shows, such as editing the bill. Pass an icon button or an `SvOverflowMenu` as the
 * action; the bar sets it on a round surface like the back button. The title wraps onto more lines
 * rather than being cut off.
 *
 * It stays in place while the content scrolls under it, and keeps clear of the status bar by
 * [windowInsets]; pass `WindowInsets(0)` where something around it already does.
 */
@Composable
fun SvTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopBarInsets,
    action: (@Composable () -> Unit)? = null,
) {
    val ground = LocalSvGround.current
    BarRow(modifier = modifier, windowInsets = windowInsets, top = SvSpacing.sm) {
        FilledIconButton(
            onClick = onBack,
            modifier = Modifier.size(ROUND_BUTTON_SIZE),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ground.quiet, contentColor = ground.onQuiet),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
        }
        Text(
            title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        if (action != null) {
            // The action brings its own touch target; the round surface is drawn behind it.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(ROUND_BUTTON_SIZE)
                        .background(ground.quiet, CircleShape),
                )
                CompositionLocalProvider(LocalContentColor provides ground.onQuiet, content = action)
            }
        }
    }
}

@Composable
private fun BarRow(modifier: Modifier, windowInsets: WindowInsets, top: Dp, content: @Composable RowScope.() -> Unit) {
    // The bar has the colour of the page, so what scrolls under it disappears at its lower edge.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(windowInsets)
            .heightIn(min = BAR_MIN_HEIGHT)
            .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = top, bottom = SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        content = content,
    )
}

/** The status bar and the sides of the screen, as Material's top bars keep clear of. */
private val TopBarInsets: WindowInsets
    @Composable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

private val ROUND_BUTTON_SIZE = 44.dp
private val BAR_MIN_HEIGHT = 64.dp
