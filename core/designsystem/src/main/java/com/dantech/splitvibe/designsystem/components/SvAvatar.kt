package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvTheme

/**
 * A coloured tile with rounded corners and the person's initials. The same name always gets the
 * same colour, so a person can be recognised by it across screens. Decorative: the name is always
 * shown next to it.
 */
@Composable
fun SvAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    AvatarTile(name = name, size = size, showInitials = true, modifier = modifier.clearAndSetSemantics { })
}

/**
 * The people of something, such as who shares an item, as their avatars overlapping in a row, the
 * first one at the back. Below 28dp the tiles show only their colour, as two letters no longer
 * read well there. Each tile has a thin ring in [ringColor], which must be the colour behind the
 * stack, so the tiles stay apart where they overlap; the default is the colour of the page or card
 * the stack sits on. Decorative like [SvAvatar]: say who they are in text nearby.
 */
@Composable
fun SvAvatarStack(names: List<String>, modifier: Modifier = Modifier, size: Dp = 32.dp, ringColor: Color = LocalSvGround.current.color) {
    val step = size - STACK_OVERLAP
    Box(modifier = modifier.clearAndSetSemantics { }) {
        names.forEachIndexed { index, name ->
            AvatarTile(
                name = name,
                size = size,
                showInitials = size >= MIN_SIZE_WITH_INITIALS,
                modifier = Modifier.padding(start = step * index),
                ring = ringColor,
            )
        }
    }
}

@Composable
private fun AvatarTile(name: String, size: Dp, showInitials: Boolean, modifier: Modifier, ring: Color? = null) {
    val palette = SvTheme.palette
    val color = palette.person[Math.floorMod(name.hashCode(), palette.person.size)]
    // The corners grow with the tile, so a small and a large avatar have the same shape.
    val shape = RoundedCornerShape(percent = CORNER_PERCENT)
    Box(
        modifier = modifier
            .size(size)
            .background(color, shape)
            .then(if (ring != null) Modifier.border(RING_WIDTH, ring, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!showInitials) return@Box
        // The initials follow the size of the tile, not the user's text size: larger text would
        // not fit in it, and the full name is always written next to the avatar anyway.
        val initialsSize = with(LocalDensity.current) { (size * INITIALS_SCALE).toSp() }
        Text(
            text = initials(name),
            color = palette.onPerson,
            style = MaterialTheme.typography.labelLargeEmphasized.copy(fontSize = initialsSize, lineHeight = initialsSize),
        )
    }
}

/** "Trần Quang Minh" becomes "TM", "Emma" becomes "E". */
internal fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (words.size) {
        0 -> "?"
        1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/** At the default size of 40dp this is the corner of `SvShapes.tile`. */
private const val CORNER_PERCENT = 36
private const val INITIALS_SCALE = 0.38f
private val STACK_OVERLAP = 8.dp
private val RING_WIDTH = 2.dp

/** Below this size two letters no longer read well, and a stack shows only the colours. */
private val MIN_SIZE_WITH_INITIALS = 28.dp
