package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.keepSizeDuringTransition
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.elevation

/**
 * The main action of a screen as a large pill across the full width: violet on the page, lime in
 * [SvBottomBar]. It is fully round, so it can turn into a round surface such as [SvSuccessMark];
 * its label then keeps its size and is cut off by the shrinking edge instead of wrapping onto more
 * lines.
 */
@Composable
fun SvPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MAIN_ACTION_HEIGHT),
        shape = SvShapes.pill,
        colors = mainActionColors(),
    ) {
        Text(
            text,
            modifier = Modifier.keepSizeDuringTransition(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The bar at the bottom of a screen that holds its main action: a dark pill that floats above the
 * content, clear of the edges of the screen and of the system navigation bar.
 *
 * Put it in the `bottomBar` of a `Scaffold`, or last in a column below the part that scrolls: the
 * content then ends above it, so it never hides the end of a list, nor a field the keyboard pushed
 * up. What is in it takes the colours of the bar by itself: [SvPrimaryButton] and [SvSplitButton]
 * turn lime, [SvQuietButton], [SvWorkingRow] and [SvLoadingIndicator] take the bar's own shades,
 * and plain text is drawn in the bar's text colour.
 */
@Composable
fun SvBottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = SvTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm, bottom = SvSpacing.lg),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .elevation(SvElevation.Level3, BarShape),
            shape = BarShape,
            color = palette.bar,
            contentColor = palette.onBar,
            // Only the dark bar shows its edge; in the light theme the outline is the bar's own colour.
            border = BorderStroke(BAR_OUTLINE_WIDTH, palette.barOutline),
        ) {
            CompositionLocalProvider(LocalSvGround provides SvGround.Bar) {
                Column(modifier = Modifier.padding(BAR_PADDING), content = content)
            }
        }
    }
}

/**
 * The colours of the main action where it sits: violet on the page and on a card, lime on the
 * floating bar and on a violet block, ink on a lime block.
 */
@Composable
internal fun mainActionColors(): ButtonColors {
    val ground = LocalSvGround.current
    if (ground == SvGround.Page || ground == SvGround.Card) return ButtonDefaults.buttonColors()
    return ButtonDefaults.buttonColors(
        containerColor = ground.active,
        contentColor = ground.onActive,
        // A main action that cannot be used yet steps back to the quiet shade of what it sits on,
        // and its label stays readable.
        disabledContainerColor = ground.quiet,
        disabledContentColor = if (ground == SvGround.Bar) SvTheme.palette.onBarQuiet else ground.onQuiet.copy(alpha = QUIET_LABEL_ALPHA),
    )
}

/** How far the label of a main action that cannot be used yet fades on a coloured block. */
private const val QUIET_LABEL_ALPHA = 0.7f

/** How tall the main action of a screen is, and every other button on the floating bar. */
internal val MAIN_ACTION_HEIGHT = 56.dp

/** The space between the edge of the floating bar and what is in it. */
private val BAR_PADDING = 8.dp

/** Round at the ends while the bar holds one row; a taller bar keeps the same corners. */
private val BarShape = RoundedCornerShape(MAIN_ACTION_HEIGHT / 2 + BAR_PADDING)

private val BAR_OUTLINE_WIDTH = 1.dp
