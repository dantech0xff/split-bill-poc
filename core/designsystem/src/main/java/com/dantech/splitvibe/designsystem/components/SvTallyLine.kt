package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountSmall

/**
 * The line below the parts of something that says whether they add up, such as "Adds up to the bill
 * total" below what everyone pays, with the total as [amount] at its end. While they add up
 * ([balanced]) a tick in a round mark stands before [text]; while they do not, such as "₫5,000 of
 * ₫902,664 still to give out", there is no tick and the text is in the colour of errors. The mark is
 * decorative: the text says it all.
 */
@Composable
fun SvTallyLine(text: String, balanced: Boolean, modifier: Modifier = Modifier, amount: String? = null) {
    val colors = MaterialTheme.colorScheme
    val color = if (balanced) colors.onSurface else colors.error
    Row(
        modifier = modifier.heightIn(min = LINE_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (balanced) {
            Box(
                modifier = Modifier
                    .size(MARK_SIZE)
                    .background(colors.inverseSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(TICK_SIZE), tint = tickColor())
            }
        }
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = color)
        if (amount != null) Text(amount, style = MaterialTheme.typography.amountSmall, color = color)
    }
}

private val LINE_HEIGHT = 32.dp
private val MARK_SIZE = 22.dp
private val TICK_SIZE = 15.dp
