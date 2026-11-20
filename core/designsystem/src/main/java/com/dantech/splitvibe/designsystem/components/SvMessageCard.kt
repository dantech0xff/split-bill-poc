package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A notice inside the page: a card with a yellow tile that marks something the user should know,
 * or with [isError] a red tile for something that went wrong. [action] adds one button below the
 * text that lets the user act on it. The tile is decorative; the text says it all.
 *
 * On the page and on the floating bar it has the colour of a card, white in the light theme. Inside
 * a card, a sheet or a dialog, which have that colour themselves, it takes their quiet shade instead,
 * a light grey in the light theme, so that it still stands out from them.
 */
@Composable
fun SvMessageCard(text: String, isError: Boolean, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val palette = SvTheme.palette
    val ground = LocalSvGround.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (ground == SvGround.Card) ground.quiet else SvElevation.Level1.surface,
        contentColor = colors.onSurface,
    ) {
        CompositionLocalProvider(LocalSvGround provides SvGround.Card) {
            Row(
                modifier = Modifier.padding(start = SvSpacing.lg, end = SvSpacing.lg, top = SvSpacing.lg, bottom = SvSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(TILE_SIZE)
                        .background(if (isError) colors.error else palette.notice, SvShapes.tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isError) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(ICON_SIZE),
                        tint = if (isError) colors.onError else palette.onPerson,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    if (action != null) {
                        // Moved back by its own padding, so its label starts where the text above starts.
                        TextButton(
                            onClick = onAction,
                            modifier = Modifier.offset(x = -ACTION_PADDING),
                            contentPadding = PaddingValues(horizontal = ACTION_PADDING, vertical = SvSpacing.sm),
                        ) {
                            Text(action, style = MaterialTheme.typography.labelLargeEmphasized)
                        }
                    }
                }
            }
        }
    }
}

private val TILE_SIZE = 36.dp
private val ICON_SIZE = 20.dp
private val ACTION_PADDING = SvSpacing.md
