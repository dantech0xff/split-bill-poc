package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import kotlin.math.min

/**
 * The frame of a ticket, for the card that shows a whole split at once, such as the one shared as a
 * picture: a violet card with white text, cut into [content] above and a [stub] below by a dashed
 * line, with a round notch at each end of the line. The notches show what lies behind the ticket.
 * What is in it takes the colours that read on violet by itself, as in [SvHero].
 */
@Composable
fun SvTicket(stub: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = SvTheme.palette
    val corner = MaterialTheme.shapes.largeIncreased.topStart
    val dash = palette.onBrand.copy(alpha = DASH_ALPHA)
    CompositionLocalProvider(LocalSvGround provides SvGround.Brand, LocalContentColor provides palette.onBrand) {
        Column(modifier = modifier.fillMaxWidth()) {
            // Each part carries half of each notch, so that the notches are real gaps in the ticket.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.brand, TicketPartShape(corner, notchAtTop = false))
                    .padding(start = EDGE_PADDING, end = EDGE_PADDING, top = EDGE_PADDING, bottom = SvSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.xs),
                content = content,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.brand, TicketPartShape(corner, notchAtTop = true))
                    .drawBehind {
                        // The dashed line runs along the edge where the two parts meet, between the notches.
                        val inset = EDGE_PADDING.toPx()
                        val length = DASH_LENGTH.toPx()
                        drawLine(
                            dash,
                            start = Offset(inset, 0f),
                            end = Offset(size.width - inset, 0f),
                            strokeWidth = DASH_WIDTH.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(length, length)),
                        )
                    }
                    .padding(start = EDGE_PADDING, end = EDGE_PADDING, top = SvSpacing.md, bottom = SvSpacing.lg),
                content = stub,
            )
        }
    }
}

/**
 * One part of a ticket: round outer corners, and at the edge it shares with the other part a notch
 * at each end, a quarter of a circle cut into each corner.
 */
private class TicketPartShape(private val corner: CornerSize, private val notchAtTop: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val notch = min(with(density) { NOTCH_RADIUS.toPx() }, min(w / 2, h))
        // The round corners give way to the notch on a part too short for both.
        val round = min(corner.toPx(size, density), min(w / 2, (h - notch).coerceAtLeast(0f)))
        val path = Path()
        if (notchAtTop) {
            path.moveTo(notch, 0f)
            path.lineTo(w - notch, 0f)
            path.arcTo(Rect(Offset(w, 0f), notch), startAngleDegrees = 180f, sweepAngleDegrees = -90f, forceMoveTo = false)
            path.lineTo(w, h - round)
            path.arcTo(Rect(w - 2 * round, h - 2 * round, w, h), startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false)
            path.lineTo(round, h)
            path.arcTo(Rect(0f, h - 2 * round, 2 * round, h), startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false)
            path.lineTo(0f, notch)
            path.arcTo(Rect(Offset(0f, 0f), notch), startAngleDegrees = 90f, sweepAngleDegrees = -90f, forceMoveTo = false)
        } else {
            path.moveTo(0f, round)
            path.arcTo(Rect(0f, 0f, 2 * round, 2 * round), startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)
            path.lineTo(w - round, 0f)
            path.arcTo(Rect(w - 2 * round, 0f, w, 2 * round), startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false)
            path.lineTo(w, h - notch)
            path.arcTo(Rect(Offset(w, h), notch), startAngleDegrees = 270f, sweepAngleDegrees = -90f, forceMoveTo = false)
            path.lineTo(notch, h)
            path.arcTo(Rect(Offset(0f, h), notch), startAngleDegrees = 0f, sweepAngleDegrees = -90f, forceMoveTo = false)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is TicketPartShape && other.corner == corner && other.notchAtTop == notchAtTop

    override fun hashCode(): Int = 31 * corner.hashCode() + notchAtTop.hashCode()
}

private val EDGE_PADDING = 20.dp

/** Half the size of a notch, as on the approved designs. */
private val NOTCH_RADIUS = 12.dp
private val DASH_WIDTH = 2.dp
private val DASH_LENGTH = 6.dp
private const val DASH_ALPHA = 0.5f
