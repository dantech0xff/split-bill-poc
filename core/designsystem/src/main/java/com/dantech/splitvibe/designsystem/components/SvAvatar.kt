package com.dantech.splitvibe.designsystem.components

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvTheme

/**
 * A coloured circle with the person's initials. The same name always gets the same colour, so a
 * person can be recognised by it across screens. Decorative: the name is always shown next to it.
 */
@Composable
fun SvAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val palette = SvTheme.palette
    val color = palette.person[Math.floorMod(name.hashCode(), palette.person.size)]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // The initials follow the size of the circle, not the user's text size: larger text would
        // not fit in it, and the full name is always written next to the avatar anyway.
        val initialsSize = with(LocalDensity.current) { (size * 0.36f).toSp() }
        Text(
            text = initials(name),
            color = palette.onPerson,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = initialsSize, lineHeight = initialsSize),
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
