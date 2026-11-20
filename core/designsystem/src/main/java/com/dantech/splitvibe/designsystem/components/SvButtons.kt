package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
    val (container, content) = smallActionColors()
    val colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    PillButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, icon = icon, colors = colors, height = SMALL_HEIGHT)
}

/**
 * The small action as a round button that shows only its [icon], such as asking for the change
 * typed in a field, in the colours of [SvSmallAction]. [label] is what a screen reader says for it.
 * It is drawn 40dp across, in a space a finger can hit; one that cannot be used yet takes the quiet
 * colour of what it sits on.
 */
@Composable
fun SvSmallAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ground = LocalSvGround.current
    val (container, content) = smallActionColors()
    FilledIconButton(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(SMALL_ICON_ACTION_SIZE),
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = ground.quiet,
            disabledContentColor = ground.onQuiet.copy(alpha = DISABLED_CONTENT_ALPHA),
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(SMALL_ICON_SIZE))
    }
}

/**
 * An action beside a field that its [icon] says, such as removing the line the field belongs to: a
 * quiet square as tall as the box of a field, so that it lines up with the box at the bottom of a
 * row of fields. With [destructive] the icon is in the colour of errors. [label] is what a screen
 * reader says for it.
 */
@Composable
fun SvFieldButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    val ground = LocalSvGround.current
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(FIELD_BUTTON_SIZE),
        shape = MaterialTheme.shapes.medium,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = ground.quiet,
            contentColor = if (destructive) MaterialTheme.colorScheme.error else ground.onQuiet,
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(FIELD_BUTTON_ICON_SIZE))
    }
}

/**
 * The colours of a small action where it sits. An ink pill would not show where the ground is dark
 * itself, in the dark theme, on the floating bar and on a violet block, so there the pill turns lime
 * and its text ink. A lime pill would not show on a lime block, so there it stays ink in both themes.
 */
@Composable
private fun smallActionColors(): Pair<Color, Color> {
    val palette = SvTheme.palette
    val lime = when (LocalSvGround.current) {
        SvGround.Page, SvGround.Card -> LocalDarkTheme.current
        SvGround.Bar, SvGround.Brand -> true
        SvGround.Accent -> false
    }
    return if (lime) palette.accent to palette.onAccent else palette.onAccent to palette.accent
}

/**
 * A button that leads on to what it names, such as "You owe ₫150,000 to Dana" on a hero, which leads
 * to the requests: a quiet bar 48dp tall with round corners, its [text] at the start with [emphasis],
 * a part of it such as the amount, in bold, and a chevron at the end that points the way. It takes
 * the quiet shade of what it sits on, so on a violet block it stands out without competing with the
 * main action. The chevron is decorative: [text] says where the button leads.
 */
@Composable
fun SvLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, emphasis: String? = null) {
    val ground = LocalSvGround.current
    val shown = remember(text, emphasis) { emphasized(text, emphasis) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH_TARGET)
            .clip(MaterialTheme.shapes.medium)
            .background(ground.quiet)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shown, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = ground.onQuiet)
        Icon(LinkChevron, contentDescription = null, modifier = Modifier.size(LINK_CHEVRON_SIZE), tint = ground.onQuiet)
    }
}

/** [text] with its first [emphasis] in bold; as it is when [emphasis] is not in it. */
private fun emphasized(text: String, emphasis: String?): AnnotatedString = buildAnnotatedString {
    append(text)
    if (emphasis.isNullOrEmpty()) return@buildAnnotatedString
    val start = text.indexOf(emphasis)
    if (start >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + emphasis.length)
}

/** A chevron that points to the end of the line, the way a button leads; it turns round where text runs from right to left. */
private val LinkChevron: ImageVector = ImageVector.Builder(
    name = "SvButtons.LinkChevron",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,
).apply {
    addPath(
        pathData = addPathNodes("m9 6 6 6-6 6"),
        stroke = IconInk,
        strokeLineWidth = LINK_CHEVRON_LINE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

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
private val SMALL_ICON_ACTION_SIZE = 40.dp
private val SMALL_ICON_SIZE = 20.dp

/** As tall as the box of a field of one line. */
private val FIELD_BUTTON_SIZE = 52.dp

private val LINK_CHEVRON_SIZE = 20.dp
private const val LINK_CHEVRON_LINE = 2.4f
private val FIELD_BUTTON_ICON_SIZE = 20.dp
private val PILL_PADDING = PaddingValues(horizontal = 20.dp, vertical = SvSpacing.sm)

/** As Material dims the content of a control that cannot be used. */
private const val DISABLED_CONTENT_ALPHA = 0.38f
