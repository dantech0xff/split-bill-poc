package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A card that lies flat on the page: white in the light theme, the surface of its [elevation] in
 * the dark theme. Its content is laid out in a column.
 *
 * Pass [color] for a card that carries meaning through colour, such as the accent container.
 * Without it the card takes the surface colour of its level.
 */
@Composable
fun SvCard(
    modifier: Modifier = Modifier,
    elevation: SvElevation = SvElevation.Level1,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = elevation.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The surface of a level is a ground for ordinary text. Material would look its colour up among
    // the roles, and in the dark palette one level has the colour of the role for quieter text.
    val contentColor = if (color == elevation.surface) MaterialTheme.colorScheme.onSurface else contentColorFor(color)
    Surface(modifier = modifier.elevation(elevation, shape), shape = shape, color = color, contentColor = contentColor) {
        CompositionLocalProvider(LocalSvGround provides SvGround.Card) {
            Column(content = content)
        }
    }
}

/**
 * What a component sits on. A quiet part of a component, such as a secondary button or a choice
 * that is not chosen, takes a colour that stands out from what is behind it, so that a screen never
 * has to pick one: white on the page, a light grey on a card or a sheet, and a lighter shade of the
 * bar on a floating bar. The main action is lime on a floating bar, where violet would be hard to see.
 */
internal enum class SvGround { Page, Card, Bar }

/**
 * Set by the surfaces of the design system: [SvCard], the rows of a group, a notice and a sheet set
 * [SvGround.Card], and [SvBottomBar] sets [SvGround.Bar]. Everything else lies on the page.
 */
internal val LocalSvGround = staticCompositionLocalOf { SvGround.Page }

/** The colour of the ground itself. */
internal val SvGround.color: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        SvGround.Page -> MaterialTheme.colorScheme.surface
        SvGround.Card -> SvElevation.Level1.surface
        SvGround.Bar -> SvTheme.palette.bar
    }

/** The colour of a quiet control on this ground. */
internal val SvGround.quiet: Color
    @Composable
    @ReadOnlyComposable
    get() {
        val colors = MaterialTheme.colorScheme
        return when (this) {
            // The colour of a card: white on the light page, a lighter surface on the dark one.
            SvGround.Page -> SvElevation.Level1.surface

            // A card in the dark theme is already the first step up from the page, so a control on it
            // takes the step after the next one; the next one alone would hardly show.
            SvGround.Card -> if (LocalDarkTheme.current) colors.surfaceContainerHigh else colors.surfaceContainer

            // Opaque, so that a ripple or a shadow on it does not mix with the bar a second time.
            SvGround.Bar -> SvTheme.palette.onBar.copy(alpha = BAR_QUIET_ALPHA).compositeOver(SvTheme.palette.bar)
        }
    }

/** Text and icons on [quiet]. */
internal val SvGround.onQuiet: Color
    @Composable
    @ReadOnlyComposable
    get() = if (this == SvGround.Bar) SvTheme.palette.onBar else MaterialTheme.colorScheme.onSurface

/** How much of the bar's text colour lightens a quiet control on the bar, as on the approved designs. */
private const val BAR_QUIET_ALPHA = 0.11f
