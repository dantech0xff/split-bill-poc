package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme

/**
 * A secondary action beside or below the main one, such as "Sign out" or "Type it in by hand":
 * a quiet pill that stands out from what it sits on without competing with [SvPrimaryButton].
 * It is white on the page, light grey on a card or a sheet, and a lighter shade of the bar in
 * [SvBottomBar], where it is as tall as the main action. [icon] is decorative; [text] says what
 * the button does.
 */
@Composable
fun SvQuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val ground = LocalSvGround.current
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        colors = quietActionColors(),
        height = if (ground == SvGround.Bar) MAIN_ACTION_HEIGHT else QUIET_HEIGHT,
    )
}

/**
 * A small action that belongs to one row or card, such as "Send" beside the person it goes to:
 * an ink pill with lime text in the light theme and a lime pill with ink text in the dark one, so
 * it stands out among the quiet rows around it. [icon] is decorative; [text] says what it does.
 */
@Composable
fun SvSmallAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val palette = SvTheme.palette
    // An ink pill would not show where the ground is dark itself, in the dark theme, on the floating
    // bar and on a violet block, so there the pill turns lime and its text ink. A lime pill would not
    // show on a lime block, so there it stays ink in both themes.
    val lime = when (LocalSvGround.current) {
        SvGround.Page, SvGround.Card -> LocalDarkTheme.current
        SvGround.Bar, SvGround.Brand -> true
        SvGround.Accent -> false
    }
    val colors = if (lime) {
        ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
    } else {
        ButtonDefaults.buttonColors(containerColor = palette.onAccent, contentColor = palette.accent)
    }
    PillButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, icon = icon, colors = colors, height = SMALL_HEIGHT)
}

/**
 * An action that only needs a word, such as "See all" beside a title or "Restore purchases" below
 * a plan: underlined text in the colour of the text around it, with a touch target of its own.
 */
@Composable
fun SvTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TOUCH_TARGET),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalSvGround.current.onQuiet),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLargeEmphasized.copy(textDecoration = TextDecoration.Underline),
            textAlign = TextAlign.Center,
        )
    }
}

/** The colours of a quiet control where it sits, from [SvGround]. */
@Composable
private fun quietActionColors(): ButtonColors {
    val ground = LocalSvGround.current
    val content = ground.onQuiet
    return ButtonDefaults.buttonColors(
        containerColor = ground.quiet,
        contentColor = content,
        disabledContainerColor = ground.quiet,
        disabledContentColor = content.copy(alpha = DISABLED_CONTENT_ALPHA),
    )
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    colors: ButtonColors,
    height: Dp,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        enabled = enabled,
        shape = SvShapes.pill,
        colors = colors,
        contentPadding = PILL_PADDING,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            Spacer(Modifier.width(SvSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLargeEmphasized, textAlign = TextAlign.Center)
    }
}

/** The smallest size a finger can reliably hit. */
private val TOUCH_TARGET = 48.dp
private val QUIET_HEIGHT = TOUCH_TARGET

/** Drawn smaller than a finger needs; Material keeps its touch target at 48dp around it. */
private val SMALL_HEIGHT = 44.dp
private val ICON_SIZE = 18.dp
private val PILL_PADDING = PaddingValues(horizontal = 20.dp, vertical = SvSpacing.sm)

/** As Material dims the content of a control that cannot be used. */
private const val DISABLED_CONTENT_ALPHA = 0.38f
