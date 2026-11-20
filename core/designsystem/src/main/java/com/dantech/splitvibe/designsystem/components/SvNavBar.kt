package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.designsystem.tokens.elevation

/**
 * One destination of an [SvNavBar]: its [label], its [icon], and [count], how many things wait for
 * the user there, shown on the icon when it is more than 0.
 */
@Immutable
class SvNavItem(val label: String, val icon: ImageVector, val count: Int = 0)

/**
 * The floating bar of the main screens of the app: a dark pill over the end of the content with the
 * [items], half of them on each side of a round lime button in the middle that starts the main task.
 * Give it four items.
 *
 * A screen reader reads each item as a tab with its label, says which one is [selected], and adds an
 * item's count to its label, such as "Requests, 2". The middle button shows only [centerIcon] and
 * reads as [centerLabel], such as "Split a bill"; [centerModifier] goes on it, such as a surface that
 * grows into the screen it opens.
 *
 * When a label does not fit on one line, as with text at twice its size, every item shows its icon
 * only, with a lime mark under the icon of the selected item, and the labels stay the names that a
 * screen reader reads.
 *
 * The bar floats clear of the edges of the screen and of the system navigation bar. Put it in the
 * `bottomBar` of the `Scaffold` of a main screen, as [SvBottomBar] is put, and give the list of the
 * screen the padding that the `Scaffold` passes as `Modifier.padding`, not as `contentPadding`: the
 * list then ends above the bar, and nothing scrolls under it. A row half hidden behind the bar would
 * leave only a thin strip of itself to tap.
 */
@Composable
fun SvNavBar(
    items: List<SvNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    centerLabel: String,
    centerIcon: ImageVector,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
    centerModifier: Modifier = Modifier,
) {
    val palette = SvTheme.palette
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = BAR_MARGIN, end = BAR_MARGIN, top = SvSpacing.sm, bottom = BAR_MARGIN),
    ) {
        // Every item, and the button in the middle, takes an equal share of the bar.
        val share = (maxWidth - BAR_PADDING * 2) / (items.size + 1)
        val iconsOnly = labelsOverflow(items.map { it.label }, share)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .elevation(SvElevation.Level3, BarShape)
                .selectableGroup(),
            shape = BarShape,
            color = palette.bar,
            contentColor = palette.onBar,
            // Only the dark bar shows its edge; in the light theme the outline is the bar's own colour.
            border = BorderStroke(BAR_OUTLINE_WIDTH, palette.barOutline),
        ) {
            CompositionLocalProvider(LocalSvGround provides SvGround.Bar) {
                Row(
                    modifier = Modifier
                        .heightIn(min = BAR_HEIGHT)
                        .padding(horizontal = BAR_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tab: @Composable RowScope.(Int) -> Unit = { index ->
                        NavTab(items[index], index == selected, iconsOnly, onClick = { onSelect(index) }, modifier = Modifier.weight(1f))
                    }
                    val half = items.size / 2
                    for (index in 0 until half) tab(index)
                    CenterButton(centerLabel, centerIcon, onCenterClick, Modifier.weight(1f), centerModifier)
                    for (index in half until items.size) tab(index)
                }
            }
        }
    }
}

/** Whether any of [labels] is wider than [share] on one line, in the bold style of the selected item. */
@Composable
private fun labelsOverflow(labels: List<String>, share: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmallEmphasized
    val room = with(LocalDensity.current) { share.toPx() }
    return remember(labels, style, room, measurer) {
        labels.any { label -> measurer.measure(label, style, softWrap = false, maxLines = 1).size.width > room }
    }
}

@Composable
private fun NavTab(item: SvNavItem, selected: Boolean, iconsOnly: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val palette = SvTheme.palette
    val color = if (selected) SvGround.Bar.active else palette.onBarQuiet
    val name = if (item.count > 0) "${item.label}, ${item.count}" else item.label
    Column(
        modifier = modifier
            .heightIn(min = ITEM_HEIGHT)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(bounded = false, radius = ITEM_HEIGHT / 2),
            )
            // One name for the whole tab, with its count, whether or not its label shows.
            .clearAndSetSemantics { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(ICON_SIZE), tint = color)
            if (item.count > 0) {
                Count(
                    item.count,
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = COUNT_OFFSET_X, y = COUNT_OFFSET_Y),
                )
            }
        }
        Spacer(Modifier.height(SvSpacing.xs))
        if (iconsOnly) {
            // Without its bold label, the selected item would differ from the others by colour alone,
            // which not everyone can tell apart, so a mark under its icon shows it too. Every item
            // keeps the room for the mark, so that the icons stay in line.
            Box(
                Modifier
                    .size(SELECTED_MARK_WIDTH, SELECTED_MARK_HEIGHT)
                    .then(if (selected) Modifier.background(color, SvShapes.pill) else Modifier),
            )
        } else {
            Text(
                item.label,
                color = color,
                style = if (selected) MaterialTheme.typography.labelSmallEmphasized else MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The number on an item's icon. */
@Composable
private fun Count(count: Int, modifier: Modifier) {
    val palette = SvTheme.palette
    // The number sits in a small fixed shape on the icon, so like the initials of an avatar it keeps
    // its size when the text grows; the name of the tab says the count in full.
    val size = with(LocalDensity.current) { COUNT_TEXT_SIZE.toSp() }
    Box(
        modifier = modifier
            .heightIn(min = COUNT_SIZE)
            .widthIn(min = COUNT_SIZE)
            .background(palette.accent, SvShapes.pill)
            .padding(horizontal = SvSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            color = palette.onAccent,
            style = MaterialTheme.typography.labelSmallEmphasized.copy(fontSize = size, lineHeight = size),
        )
    }
}

@Composable
private fun CenterButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, buttonModifier: Modifier) {
    val palette = SvTheme.palette
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            modifier = buttonModifier.size(CENTER_SIZE),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(CENTER_ICON_SIZE))
        }
    }
}

private val BAR_HEIGHT = 72.dp
private val BAR_MARGIN = 16.dp
private val BAR_PADDING = 6.dp
private val BarShape = RoundedCornerShape(BAR_HEIGHT / 2)
private val BAR_OUTLINE_WIDTH = 1.dp
private val ITEM_HEIGHT = 56.dp
private val ICON_SIZE = 24.dp
private val CENTER_SIZE = 56.dp
private val CENTER_ICON_SIZE = 28.dp
private val COUNT_SIZE = 18.dp
private val COUNT_TEXT_SIZE = 11.dp
private val COUNT_OFFSET_X = 10.dp
private val COUNT_OFFSET_Y = (-6).dp
private val SELECTED_MARK_WIDTH = 16.dp
private val SELECTED_MARK_HEIGHT = 4.dp
