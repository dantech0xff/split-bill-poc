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
import androidx.compose.runtime.ReadOnlyComposable
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
    AvatarTile(name = name, size = size, letters = initials(name), modifier = modifier.clearAndSetSemantics { })
}

/**
 * The people of something, such as who shares an item, as their avatars overlapping in a row, the
 * first one at the back. Each tile shows the first letter of the name, as the part of a tile that
 * the next one leaves free has room for one letter only; below 28dp the tiles show only their
 * colour. Each tile has a ring 2dp wide in [ringColor], so that the tiles stay apart where they
 * overlap. Unless given, the ring has the colour of what the stack lies on, the page or a card, so
 * that it reads as a gap between the tiles; on a violet or lime block it is white, as the approved
 * designs draw it there. Decorative like [SvAvatar]: say who they are in text nearby.
 */
@Composable
fun SvAvatarStack(names: List<String>, modifier: Modifier = Modifier, size: Dp = 32.dp, ringColor: Color = defaultRingColor()) {
    val step = size - STACK_OVERLAP
    Box(modifier = modifier.clearAndSetSemantics { }) {
        names.forEachIndexed { index, name ->
            AvatarTile(
                name = name,
                size = size,
                letters = if (size >= MIN_SIZE_WITH_LETTER) initials(name).take(1) else null,
                modifier = Modifier.padding(start = step * index),
                ring = ringColor,
            )
        }
    }
}

/** The ring of a stack where it lies: white on a violet or lime block, and the colour of the ground elsewhere. */
@Composable
@ReadOnlyComposable
private fun defaultRingColor(): Color = when (val ground = LocalSvGround.current) {
    SvGround.Brand, SvGround.Accent -> Color.White
    SvGround.Page, SvGround.Card, SvGround.Bar -> ground.color
}

/** A tile in the colour of [name], with [letters] on it, or none. */
@Composable
private fun AvatarTile(name: String, size: Dp, letters: String?, modifier: Modifier, ring: Color? = null) {
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
        if (letters == null) return@Box
        // The letters follow the size of the tile, not the user's text size: larger text would
        // not fit in it, and the full name is always written next to the avatar anyway.
        val lettersSize = with(LocalDensity.current) { (size * INITIALS_SCALE).toSp() }
        Text(
            text = letters,
            color = palette.onPerson,
            style = MaterialTheme.typography.labelLargeEmphasized.copy(fontSize = lettersSize, lineHeight = lettersSize),
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

/** Below this size a letter no longer reads well, and a stack shows only the colours. */
private val MIN_SIZE_WITH_LETTER = 28.dp
