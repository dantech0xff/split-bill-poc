package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme
import com.dantech.splitvibe.designsystem.tokens.ShadowColor
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface

/**
 * A card that lies flat on the page: white in the light theme, the surface of its [elevation] in
 * the dark theme. Its content is laid out in a column.
 *
 * Pass [color] for a card that carries meaning through colour, such as the accent container.
 * Without it the card takes the surface colour of its level. With [outlined] the card has an outline
 * in the colour of text, for the one card that waits for the user's answer, such as the changes the
 * AI proposes, or for the chosen one of a few cards to choose from, such as the plan to buy.
 */
@Composable
fun SvCard(
    modifier: Modifier = Modifier,
    elevation: SvElevation = SvElevation.Level1,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = elevation.surface,
    outlined: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The surface of a level is a ground for ordinary text. Material would look its colour up among
    // the roles, and in the dark palette one level has the colour of the role for quieter text.
    val contentColor = if (color == elevation.surface) MaterialTheme.colorScheme.onSurface else contentColorFor(color)
    Surface(
        modifier = modifier.elevation(elevation, shape),
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = if (outlined) BorderStroke(OUTLINE_WIDTH, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        CompositionLocalProvider(LocalSvGround provides SvGround.Card) {
            Column(content = content)
        }
    }
}

/**
 * A panel set into a card or a sheet in the colour of the page, such as someone a sheet suggests, or
 * the item of a bill opened to change it: it stands apart from the rest of the card without a line
 * around it. What is in it lies on the colours of the page, so a field or a quiet button in it is
 * white in the light theme. Its content is laid out in a column.
 */
@Composable
fun SvInset(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(SvSpacing.md),
    ) {
        CompositionLocalProvider(LocalSvGround provides SvGround.Page, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * What a component sits on. A quiet part of a component, such as a secondary button or a choice
 * that is not chosen, takes a colour that stands out from what is behind it, so that a screen never
 * has to pick one: white on the page, a light grey on a card or a sheet, a lighter shade of the bar
 * on a floating bar, and a darker shade of a violet or lime block. The main action is lime on a
 * floating bar and on a violet block, where violet would be hard to see, and ink on a lime block.
 */
internal enum class SvGround { Page, Card, Bar, Brand, Accent }

/**
 * Set by the surfaces of the design system: [SvCard], the rows of a group, a notice, a sheet and a
 * dialog set [SvGround.Card]; [SvBottomBar] and [SvNavBar] set [SvGround.Bar]; [SvHero], [SvTile]
 * and [SvTicket] set the ground of their own colour; [SvInset] sets the page again inside a card.
 * Everything else lies on the page.
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
        SvGround.Brand -> SvTheme.palette.brand
        SvGround.Accent -> SvTheme.palette.accent
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

            // A shade of the block, in the colour of shadows, as the tags and buttons on the approved
            // violet blocks are drawn. Opaque for the same reason as on the bar.
            SvGround.Brand, SvGround.Accent -> ShadowColor.copy(alpha = BLOCK_QUIET_ALPHA).compositeOver(color)
        }
    }

/** Text and icons on [quiet], and on the ground itself. */
internal val SvGround.onQuiet: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        SvGround.Page, SvGround.Card -> MaterialTheme.colorScheme.onSurface
        SvGround.Bar -> SvTheme.palette.onBar
        SvGround.Brand -> SvTheme.palette.onBrand
        SvGround.Accent -> SvTheme.palette.onAccent
    }

/**
 * The colour of what is on, filled or chosen on this ground, and of its main action: violet on the
 * page and on a card, lime on the dark bar and on a violet block, and ink on a lime block.
 */
internal val SvGround.active: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        SvGround.Page, SvGround.Card -> MaterialTheme.colorScheme.primary
        SvGround.Bar, SvGround.Brand -> SvTheme.palette.accent
        SvGround.Accent -> SvTheme.palette.onAccent
    }

/** Text and icons on [active]. */
internal val SvGround.onActive: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        SvGround.Page, SvGround.Card -> MaterialTheme.colorScheme.onPrimary
        SvGround.Bar, SvGround.Brand -> SvTheme.palette.onAccent
        SvGround.Accent -> SvTheme.palette.accent
    }

/** The outline of a card that waits for an answer, as on the approved designs. */
private val OUTLINE_WIDTH = 2.dp

/** How much of the bar's text colour lightens a quiet control on the bar, as on the approved designs. */
private const val BAR_QUIET_ALPHA = 0.11f

/** How much shadow darkens a quiet control on a violet or lime block, as on the approved designs. */
private const val BLOCK_QUIET_ALPHA = 0.24f
