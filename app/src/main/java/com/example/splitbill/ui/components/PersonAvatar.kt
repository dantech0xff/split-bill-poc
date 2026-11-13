package com.example.splitbill.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AvatarColors = listOf(
    Color(0xFF0B6E4F),
    Color(0xFF2E5EAA),
    Color(0xFF8A5100),
    Color(0xFF7B3F8C),
    Color(0xFFB3413A),
    Color(0xFF3F6E7A),
)

/** A coloured circle with the person's initials. Decorative: the name is always shown next to it. */
@Composable
fun PersonAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = AvatarColors[Math.floorMod(name.hashCode(), AvatarColors.size)]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            style = if (size < 36.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
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
