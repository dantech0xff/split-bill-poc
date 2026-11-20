package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvPalette
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme

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
 * The top bar of the app's home: the app's mark and its [name], and at the end what leads away from
 * the home as a whole, such as an [SvAccountButton]. The name wraps onto more lines rather than
 * being cut off, and a screen reader reads it as the title of the screen; the mark is decorative.
 *
 * It stays in place while the content scrolls under it, and keeps clear of the status bar by
 * [windowInsets]; pass `WindowInsets(0)` where something around it already does.
 */
@Composable
fun SvBrandTopBar(
    name: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopBarInsets,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BarRow(modifier = modifier, windowInsets = windowInsets, top = SvSpacing.md) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MARK_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Text(name, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * The button at the end of the home's top bar that leads to the user's account and the settings:
 * an ink tile with the first letter of [name], the name the user goes by, or the outline of a person
 * while they have given none. A screen reader reads it as [label], such as "Settings", whatever it
 * shows. Like an avatar's initials, the letter keeps the size of the tile when the text grows.
 */
@Composable
fun SvAccountButton(name: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        // Drawn at the size of the approved design, in a space a finger can hit.
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ACCOUNT_BUTTON_SIZE)
            .semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.inverseSurface, contentColor = colors.inverseOnSurface),
    ) {
        if (name.isBlank()) {
            Icon(AccountIcon, contentDescription = null, modifier = Modifier.size(ACCOUNT_ICON_SIZE))
        } else {
            val size = with(LocalDensity.current) { (ACCOUNT_BUTTON_SIZE * LETTER_SCALE).toSp() }
            Text(
                initials(name).take(1),
                // The button's label says what it does; the letter alone would only say whose it is.
                modifier = Modifier.clearAndSetSemantics { },
                style = MaterialTheme.typography.labelLargeEmphasized.copy(fontSize = size, lineHeight = size),
            )
        }
    }
}

/** The app's mark: a circle split in two, white and lime, on a violet tile. Decorative. */
@Composable
private fun BrandMark() {
    val palette = SvTheme.palette
    Box(
        modifier = Modifier
            .size(MARK_SIZE)
            .background(palette.brand, MaterialTheme.shapes.small)
            .drawBehind { drawBrandHalves(palette, iconSize = MARK_ICON_SIZE.toPx()) },
    )
}

/**
 * The app's mark without its tile, [size] across: a circle split in two, white and lime, for a violet
 * ground, such as the stub of a ticket that says the split was made with the app. Decorative.
 */
@Composable
fun SvBrandMark(modifier: Modifier = Modifier, size: Dp = BRAND_MARK_SIZE) {
    val palette = SvTheme.palette
    Box(
        modifier = modifier
            .size(size)
            // The mark fills the box, which is as wide as it is tall.
            .drawBehind { drawBrandHalves(palette, iconSize = this.size.width) },
    )
}

/** The two halves of the mark as the approved designs draw them, in a 24 by 24 grid [iconSize] across in the middle. */
private fun DrawScope.drawBrandHalves(palette: SvPalette, iconSize: Float) {
    val unit = iconSize / MARK_GRID
    val inset = (size.width - iconSize) / 2
    fun half(color: Color, centreX: Float, centreY: Float, startAngle: Float) {
        val radius = MARK_RADIUS * unit
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = HALF_TURN,
            useCenter = true,
            topLeft = Offset(inset + centreX * unit - radius, inset + centreY * unit - radius),
            size = Size(radius * 2, radius * 2),
        )
    }
    half(palette.onBrand, centreX = 11f, centreY = 11.5f, startAngle = QUARTER_TURN)
    half(palette.accent, centreX = 13f, centreY = 12.5f, startAngle = -QUARTER_TURN)
}

/** The outline of a person: a head and shoulders. */
private val AccountIcon: ImageVector = lineIcon("Account", circle(12f, 8f, 4f) + "M4 21c0-4.2 3.4-7 8-7s8 2.8 8 7", group = "SvTopBar")

/**
 * The top bar of a screen opened from another one: a round back button that says [backLabel] to a
 * screen reader, the [title], and at the end an optional [action], one more thing to do with what
 * the screen shows, such as editing the bill. Pass an icon button or an `SvOverflowMenu` as the
 * action; the bar sets it on a round surface like the back button. The title wraps onto more lines
 * rather than being cut off. Without a title the bar holds the back button alone, for a screen whose
 * title stands at the top of what it shows, such as the step that asks for the money.
 *
 * With [oneLineTitle] the title is smaller and keeps to one line, ending in an ellipsis when it is
 * longer, for a name the app did not choose and that can be long, such as a shop's name read from a
 * receipt. A screen reader still reads it whole.
 *
 * It stays in place while the content scrolls under it, and keeps clear of the status bar by
 * [windowInsets]; pass `WindowInsets(0)` where something around it already does.
 */
@Composable
fun SvTopBar(
    title: String?,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopBarInsets,
    oneLineTitle: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val ground = LocalSvGround.current
    BarRow(modifier = modifier, windowInsets = windowInsets, top = SvSpacing.sm) {
        FilledIconButton(
            onClick = onBack,
            // Drawn at the size of the approved design, in a space a finger can hit, as the action is.
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(ROUND_BUTTON_SIZE),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ground.quiet, contentColor = ground.onQuiet),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
        }
        if (title == null) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = if (oneLineTitle) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                overflow = if (oneLineTitle) TextOverflow.Ellipsis else TextOverflow.Clip,
                maxLines = if (oneLineTitle) 1 else Int.MAX_VALUE,
            )
        }
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

private val MARK_SIZE = 36.dp
private val MARK_ICON_SIZE = 24.dp
private val BRAND_MARK_SIZE = 20.dp
private val MARK_GAP = 10.dp
private const val MARK_GRID = 24f
private const val MARK_RADIUS = 9f
private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f

private val ACCOUNT_BUTTON_SIZE = 44.dp
private val ACCOUNT_ICON_SIZE = 22.dp

/** The letter's size for the tile, as an avatar's initials are sized: 16sp on the 44dp tile. */
private const val LETTER_SCALE = 0.36f
