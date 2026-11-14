package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.elevation

/**
 * The one main action of a screen, floating above the content. A screen has at most one.
 * The icon is decorative; [text] says what the button does.
 */
@Composable
fun SvPrimaryFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = FloatingActionButtonDefaults.extendedFabShape
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.elevation(SvElevation.Level3, shape),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // The shadow comes from the elevation token, so Material's own is switched off.
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        // Laid out by hand: the variant that takes an icon and a text hides the text from screen
        // readers and expects the icon to carry the label instead.
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(SvSpacing.sm))
        Text(text)
    }
}
