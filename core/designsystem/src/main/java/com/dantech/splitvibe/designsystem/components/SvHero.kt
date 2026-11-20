package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountHero

/** The colours an [SvHero] comes in. */
enum class SvHeroKind {
    /** Violet with white text: what a screen is about, such as the total of a bill. */
    Brand,

    /** Lime with ink text: good news, such as what the user gets back from a group. */
    Accent,

    /**
     * The colour of the floating bar: a block that stands apart from the violet ones, such as a bill
     * link that is closed for picking.
     */
    Bar,
}

/**
 * The large block at the top of a screen that carries what the screen is about, such as what
 * friends owe the user or the total of a bill: [label] on its first line, such as "Bill total", then
 * [amount], usually an [SvHeroAmount], then [content] below, such as who it is with or what can be
 * done about it. Each part is optional, so a block that explains something can hold only [content].
 *
 * Its colour comes from [kind]. What is in it takes the colours that read on it by itself: text in the
 * block's own text colour, and buttons, tags and indicators in the shades of the block, such as a
 * lime main action on violet. The circle in its corner is decorative.
 */
@Composable
fun SvHero(
    modifier: Modifier = Modifier,
    kind: SvHeroKind = SvHeroKind.Brand,
    label: (@Composable RowScope.() -> Unit)? = null,
    amount: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val ground = kind.ground
    val circle = ground.onQuiet.copy(alpha = kind.circleAlpha)
    val typography = MaterialTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(ground.color)
            .drawBehind {
                val radius = CIRCLE_RADIUS.toPx()
                val fromEnd = CIRCLE_CENTRE_FROM_END.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) size.width - fromEnd else fromEnd
                drawCircle(circle, radius = radius, center = Offset(x, CIRCLE_CENTRE_FROM_TOP.toPx()))
            }
            .padding(SvSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.xs),
    ) {
        CompositionLocalProvider(LocalSvGround provides ground, LocalContentColor provides ground.onQuiet) {
            if (label != null) {
                ProvideTextStyle(typography.bodyMediumEmphasized) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        content = label,
                    )
                }
            }
            amount?.invoke()
            if (content != null) {
                ProvideTextStyle(typography.bodyMedium) {
                    Column(
                        modifier = Modifier.padding(top = if (label != null || amount != null) SvSpacing.xs else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                        content = content,
                    )
                }
            }
        }
    }
}

/**
 * The amount an [SvHero] is about, in large type: [prefix], [number] and [suffix] as the amount is
 * written where the phone is set, such as "₫" and "651,671", or "902.664" and " ₫". The currency
 * symbol or code in the prefix or the suffix is smaller than the number. [spokenText] is what a
 * screen reader says for it, such as "651,671 dong".
 *
 * The amount shrinks from 56sp to fit on one line, down to 20sp. An amount still too long for one
 * line at that size goes on to a second line, so no part of it is ever cut off.
 */
@Composable
fun SvHeroAmount(prefix: String, number: String, suffix: String, spokenText: String, modifier: Modifier = Modifier) {
    val text = remember(prefix, number, suffix) {
        buildAnnotatedString {
            withStyle(CurrencyStyle) { append(prefix) }
            append(number)
            withStyle(CurrencyStyle) { append(suffix) }
        }
    }
    // The line height follows the size the amount shrinks to.
    val style = MaterialTheme.typography.amountHero.copy(lineHeight = HERO_LINE_HEIGHT)
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = spokenText },
        style = style,
        autoSize = OneLineThenWrap(min = MIN_AMOUNT_SIZE, max = MAX_AMOUNT_SIZE),
    )
}

/**
 * Shrinks text until it fits on one line, from [max] down to [min] in steps of 1sp. Text that does
 * not fit on one line even at [min] stays at that size and goes on to more lines, so nothing is cut
 * off: give the text no limit on its number of lines.
 */
internal data class OneLineThenWrap(val min: TextUnit, val max: TextUnit) : TextAutoSize {
    init {
        require(min.isSp && max.isSp && min.value <= max.value) { "Sizes must be in sp, and min ($min) at most max ($max)" }
    }

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val steps = (max.value - min.value).toInt()
        fun size(step: Int): TextUnit = (max.value - step).coerceAtLeast(min.value).sp
        fun fits(step: Int): Boolean = performLayout(constraints, text, size(step)).let {
            it.lineCount <= 1 && !it.didOverflowWidth && !it.didOverflowHeight
        }
        if (fits(0)) return max
        if (!fits(steps)) return min
        // Smaller text never needs more room, so the largest size that fits is found by halving the
        // range: the step at `tooLarge` is known not to fit, and the one at `fitting` to fit.
        var tooLarge = 0
        var fitting = steps
        while (fitting - tooLarge > 1) {
            val middle = (tooLarge + fitting) / 2
            if (fits(middle)) fitting = middle else tooLarge = middle
        }
        return size(fitting)
    }
}

private val SvHeroKind.ground: SvGround
    get() = when (this) {
        SvHeroKind.Brand -> SvGround.Brand
        SvHeroKind.Accent -> SvGround.Accent
        SvHeroKind.Bar -> SvGround.Bar
    }

/** How strongly the decorative circle shows, as on the approved designs. */
private val SvHeroKind.circleAlpha: Float
    get() = if (this == SvHeroKind.Brand) 0.1f else 0.08f

private val CIRCLE_RADIUS = 84.dp
private val CIRCLE_CENTRE_FROM_END = 44.dp
private val CIRCLE_CENTRE_FROM_TOP = 36.dp

/** The currency symbol or code beside the number, as on the approved designs: 30sp beside 56sp. */
private val CurrencyStyle = SpanStyle(fontSize = 0.54.em)
private val HERO_LINE_HEIGHT = (60f / 56f).em
private val MIN_AMOUNT_SIZE = 20.sp
private val MAX_AMOUNT_SIZE = 56.sp
