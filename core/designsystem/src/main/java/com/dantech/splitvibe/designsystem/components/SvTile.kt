package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.amountLarge

/** The colours an [SvTile] comes in. */
enum class SvTileKind {
    /** Lime with ink text, such as what was spent this month. */
    Accent,

    /** Violet with white text, such as what friends owe the user. */
    Brand,

    /**
     * Ink with white text, such as what the user owes. In the dark theme, where ink would not stand
     * out from the page, it is the surface of a card.
     */
    Ink,
}

/**
 * A small block that gives one figure at a glance, usually two side by side below the hero of a
 * screen, such as what was spent this month or what a group owes the user: its [label] at the top
 * and its [value] at the bottom. [note] is a short line just above the value, in the tile's accent,
 * such as "You get back"; [caption] is a line below it, such as "Your share of 6 bills". [leading]
 * goes before the label, such as the group's avatar, and [trailing] at the end of its line, such as
 * a small chart; both are decorative.
 *
 * It is 96dp tall with a label and a value, and 124dp with a note or a caption too; put two side by
 * side with `Modifier.weight(1f)`. Larger text makes a tile as tall as what it holds, and the value
 * shrinks to stay on one line before it goes on to the next. With [onClick] the whole tile is one
 * button that reads all of its text, and [onClickLabel] says what a tap does.
 */
@Composable
fun SvTile(
    label: String,
    value: String,
    kind: SvTileKind,
    modifier: Modifier = Modifier,
    note: String? = null,
    caption: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val ground = kind.ground
    val typography = MaterialTheme.typography
    val action = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    Column(
        modifier = modifier
            .heightIn(min = if (note != null || caption != null) TALL_TILE_HEIGHT else SHORT_TILE_HEIGHT)
            .clip(MaterialTheme.shapes.large)
            .background(ground.color)
            .then(action)
            .padding(SvSpacing.lg),
    ) {
        CompositionLocalProvider(LocalSvGround provides ground, LocalContentColor provides ground.onQuiet) {
            Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                val labelStyle = typography.smallLabel
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = labelStyle,
                    autoSize = WholeWords(min = MIN_LABEL_SIZE, max = labelStyle.fontSize),
                )
                trailing?.invoke()
            }
            // The figure sits at the bottom of the tile, however tall the tile is.
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(SvSpacing.sm))
            if (note != null) Text(note, style = typography.labelMedium, color = ground.active)
            // The line height follows the size the value shrinks to.
            Text(
                value,
                style = typography.amountLarge.copy(lineHeight = VALUE_LINE_HEIGHT),
                autoSize = OneLineThenWrap(min = MIN_VALUE_SIZE, max = MAX_VALUE_SIZE),
            )
            if (caption != null) Text(caption, style = typography.bodySmall)
        }
    }
}

/** The colours an [SvTag] comes in. */
enum class SvTagKind {
    /** A quiet shade of what it sits on, for a plain state, such as "Draft" or "Paused". */
    Quiet,

    /** Lime, for what is good news or wants attention, such as "Paid and confirmed" or "2 requests". */
    Accent,

    /** The colour of the text around it, for a role, such as "Owner". */
    Strong,
}

/**
 * A short label in a small pill that marks the state or the role of something, such as "Draft",
 * "Owner" or "2 requests". It is text, not a button, and a screen reader reads it as text. [icon] is
 * decorative.
 */
@Composable
fun SvTag(text: String, modifier: Modifier = Modifier, kind: SvTagKind = SvTagKind.Quiet, icon: ImageVector? = null) {
    val ground = LocalSvGround.current
    val palette = SvTheme.palette
    val (container, content) = when (kind) {
        SvTagKind.Quiet -> ground.quiet to ground.onQuiet

        // A lime tag would not show on a lime block, so there it turns ink with lime text.
        SvTagKind.Accent -> if (ground == SvGround.Accent) palette.onAccent to palette.accent else palette.accent to palette.onAccent

        SvTagKind.Strong -> ground.onQuiet to ground.color
    }
    Row(
        modifier = modifier
            .heightIn(min = TAG_HEIGHT)
            .background(container, SvShapes.pill)
            .padding(if (icon == null) TAG_PADDING else TAG_PADDING_WITH_ICON),
        horizontalArrangement = Arrangement.spacedBy(TAG_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(TAG_ICON_SIZE), tint = content)
        Text(text, style = MaterialTheme.typography.labelMediumEmphasized, color = content)
    }
}

/**
 * An icon on a tile of colour that tells at a glance what something is, such as the category of a
 * bill at the start of its row. Give it one of the bright colours of the palette, a category's, a
 * person's or the notice colour: the icon is ink on all of them, in both themes. The corners grow
 * with [size], so a small and a large tile have the same shape. Decorative: say the same in text
 * beside it.
 */
@Composable
fun SvIconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = ICON_TILE_SIZE) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, IconTileShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * ICON_SCALE), tint = SvTheme.palette.onPerson)
    }
}

/**
 * The icons of what a bill was spent on, as the approved designs draw them: 24dp, in lines 2dp wide
 * with round ends. Show one on an [SvIconTile] in the colour of its category from
 * `SvTheme.palette.category`.
 */
object SvCategoryIcons {
    /** A fork and a knife. */
    val FoodAndDrink: ImageVector = lineIcon("FoodAndDrink", "M7 3v7a2 2 0 0 0 4 0V3M9 12v9M17 3c-1.7 1.5-2.5 3.6-2.5 6.5V13H17m0-10v18")

    /** A basket. */
    val Groceries: ImageVector = lineIcon("Groceries", "M5 10h14l-1.2 8.2a1 1 0 0 1-1 .8H7.2a1 1 0 0 1-1-.8L5 10zM9 10l3-5 3 5M3.5 10h17")

    /** A car. */
    val Transport: ImageVector =
        lineIcon("Transport", "M5 16h14M6.5 16v2.5M17.5 16v2.5M4 16v-3.5l2-5.5h12l2 5.5V16M7.5 12.5h.01M16.5 12.5h.01")

    /** A bed. */
    val Lodging: ImageVector = lineIcon("Lodging", "M3 18V8m0 7h18m0 3v-5a3 3 0 0 0-3-3h-7v5" + circle(7f, 11.5f, 1.5f))

    /** A ticket. */
    val Entertainment: ImageVector = lineIcon("Entertainment", "M4 7h16v3.5a1.5 1.5 0 0 0 0 3V17H4v-3.5a1.5 1.5 0 0 0 0-3V7zM14 7v10")

    /** A shopping bag. */
    val Shopping: ImageVector = lineIcon("Shopping", "M6 8h12l1 12H5L6 8zM9 8V6.5a3 3 0 0 1 6 0V8")

    /** A bolt of lightning. */
    val Utilities: ImageVector = lineIcon("Utilities", "M13 3 5 13.5h6L10 21l8-10.5h-6L13 3z")

    /** A key. */
    val Rent: ImageVector = lineIcon("Rent", circle(8f, 15f, 4f) + "M11 12l8-8M16 7l2.5 2.5M14 9l2 2")

    /** Three dots. */
    val Other: ImageVector = icon("Other") {
        addPath(pathData = addPathNodes(circle(5.5f, 12f, 2f) + circle(12f, 12f, 2f) + circle(18.5f, 12f, 2f)), fill = IconInk)
    }
}

/** The small bold label at the top of a tile and above a field. */
internal val Typography.smallLabel: TextStyle
    get() = bodySmallEmphasized.copy(fontWeight = FontWeight.Bold)

/**
 * Text at [max], or a little smaller, down to [min], where it would otherwise break a word in two,
 * as a long word such as "November" does in a narrow tile with large text. A word that is wider than
 * the line even at [min] is broken there, as text always is.
 */
private data class WholeWords(val min: TextUnit, val max: TextUnit) : TextAutoSize {
    init {
        require(min.isSp && max.isSp && min.value <= max.value) { "Sizes must be in sp, and min ($min) at most max ($max)" }
    }

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        fun keepsWordsWhole(size: Float): Boolean = performLayout(constraints, text, size.sp).let { layout ->
            (0 until layout.lineCount - 1).none { line -> text.text.breaksWordAt(layout.getLineEnd(line)) }
        }
        // A label is short, so trying each step from the largest size is cheap enough.
        var size = max.value
        while (size > min.value && !keepsWordsWhole(size)) size -= LABEL_SIZE_STEP
        return size.coerceAtLeast(min.value).sp
    }
}

/** Whether a line that ends at [offset] cuts a word, rather than ending at a space or a hyphen. */
private fun String.breaksWordAt(offset: Int): Boolean =
    offset in 1 until length && this[offset - 1].isLetterOrDigit() && this[offset].isLetterOrDigit()

private val SvTileKind.ground: SvGround
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        SvTileKind.Accent -> SvGround.Accent

        SvTileKind.Brand -> SvGround.Brand

        // Ink is the colour of the floating bar in the light theme, with the same text and accent.
        SvTileKind.Ink -> if (LocalDarkTheme.current) SvGround.Card else SvGround.Bar
    }

private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
    name = "SvCategoryIcons.$name",
    defaultWidth = ICON_SIZE,
    defaultHeight = ICON_SIZE,
    viewportWidth = VIEWPORT,
    viewportHeight = VIEWPORT,
).apply(build).build()

private fun lineIcon(name: String, pathData: String): ImageVector = icon(name) {
    addPath(
        pathData = addPathNodes(pathData),
        stroke = IconInk,
        strokeLineWidth = LINE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** A circle of radius [r] around ([cx], [cy]) as path data: two half circles. */
private fun circle(cx: Float, cy: Float, r: Float): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

/** Any opaque colour: an icon takes its colour from the tint it is drawn with. */
private val IconInk = SolidColor(Color.Black)
private val ICON_SIZE = 24.dp
private const val VIEWPORT = 24f
private const val LINE_WIDTH = 2f

private val SHORT_TILE_HEIGHT = 96.dp
private val TALL_TILE_HEIGHT = 124.dp
private val MIN_VALUE_SIZE = 14.sp
private val MAX_VALUE_SIZE = 24.sp

/**
 * The smallest size a label shrinks to so as not to break a word. Text in sp grows with the size the
 * user chose for text, so at twice the usual size this still draws as large as 18sp.
 */
private val MIN_LABEL_SIZE = 9.sp
private const val LABEL_SIZE_STEP = 0.5f
private val VALUE_LINE_HEIGHT = (28f / 24f).em

private val TAG_HEIGHT = 24.dp
private val TAG_PADDING = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
private val TAG_PADDING_WITH_ICON = PaddingValues(start = 8.dp, end = 10.dp, top = 2.dp, bottom = 2.dp)
private val TAG_ICON_GAP = 6.dp
private val TAG_ICON_SIZE = 14.dp

private val ICON_TILE_SIZE = 44.dp

/** At the default size of 44dp this is the corner of `SvShapes.tile`. */
private val IconTileShape = RoundedCornerShape(percent = 32)
private const val ICON_SCALE = 0.5f
