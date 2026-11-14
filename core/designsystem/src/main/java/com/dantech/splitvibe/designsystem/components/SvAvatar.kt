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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.theme.LocalDarkTheme

/**
 * A coloured circle with the person's initials. The same name always gets the same colour, so a
 * person can be recognised by it across screens. Decorative: the name is always shown next to it.
 */
@Composable
fun SvAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val person = PersonColors[Math.floorMod(name.hashCode(), PersonColors.size)]
    val dark = LocalDarkTheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) person.dark else person.light)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = if (dark) person.onDark else Color.White,
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

private class PersonColor(val light: Color, val dark: Color, val onDark: Color)

// Six hues spread around the colour wheel, leaving out the violet of the brand. White initials on
// the light colours and the listed text on the dark ones both have a contrast above 6:1.
private val PersonColors = listOf(
    PersonColor(light = Color(0xFFB12938), dark = Color(0xFFFFB3B0), onDark = Color(0xFF680016)),
    PersonColor(light = Color(0xFF835500), dark = Color(0xFFFEB958), onDark = Color(0xFF442B00)),
    PersonColor(light = Color(0xFF006D36), dark = Color(0xFF64DE8C), onDark = Color(0xFF003919)),
    PersonColor(light = Color(0xFF00696F), dark = Color(0xFF00DBE7), onDark = Color(0xFF00373A)),
    PersonColor(light = Color(0xFF006493), dark = Color(0xFF90CDFF), onDark = Color(0xFF00344E)),
    PersonColor(light = Color(0xFFA32C81), dark = Color(0xFFFFADE1), onDark = Color(0xFF610049)),
)
