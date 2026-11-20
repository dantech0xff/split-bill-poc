package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme

/**
 * One of a few ways to do the same thing, as tiles of equal width side by side, such as the ways to
 * add the photo of a bill: [icon] above a short [text]. The [prominent] tile is the usual way, ink in
 * the light theme and lime in the dark one; the others take the quiet colour of what they sit on.
 *
 * A tile is 72dp tall and grows with larger text, whose label goes on to more lines rather than being
 * cut off; give tiles side by side `Modifier.weight(1f)`. A tile that cannot be used fades with the
 * rest of what cannot be used while work runs. A screen reader reads it as a button with its text;
 * [icon] is decorative.
 */
@Composable
fun SvActionTile(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
) {
    val ground = LocalSvGround.current
    val palette = SvTheme.palette
    val colors = MaterialTheme.colorScheme
    // Ink would not stand out from the dark page, so there the usual way is lime, as the main action is.
    val (container, content) = when {
        !prominent -> ground.quiet to ground.onQuiet
        LocalDarkTheme.current -> palette.accent to palette.onAccent
        else -> colors.inverseSurface to colors.inverseOnSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { role = Role.Button },
        enabled = enabled,
        shape = TileShape,
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = TILE_HEIGHT)
                .padding(horizontal = SvSpacing.sm, vertical = SvSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ICON_GAP, Alignment.CenterVertically),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

private val TILE_HEIGHT = 72.dp
private val TileShape = RoundedCornerShape(20.dp)
private val ICON_SIZE = 22.dp
private val ICON_GAP = 6.dp

/** As the approved design fades what cannot be used while a bill is read. */
private const val DISABLED_ALPHA = 0.4f
