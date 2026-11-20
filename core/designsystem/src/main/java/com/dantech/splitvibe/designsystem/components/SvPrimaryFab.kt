package com.dantech.splitvibe.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.designsystem.motion.LocalSvSharedTransition
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvShapes
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.elevation

/**
 * The one main action of a screen, floating above the content: a violet pill with an icon. A
 * screen has at most one. The icon is decorative; [text] says what the button does.
 */
@Composable
fun SvPrimaryFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = SvShapes.pill
    // While the button grows into another screen it is measured again at every size, and its
    // shadow would have to be blurred again at every size. So it rests flat while it moves, and
    // its shadow fades back in once it is in place, which only changes the shadow's opacity.
    val moving = LocalSvSharedTransition.current?.scope?.isTransitionActive == true
    val strength = remember { Animatable(1f) }
    val fadeIn = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(moving) {
        if (moving) strength.snapTo(0f) else strength.animateTo(1f, fadeIn)
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = if (moving) modifier else modifier.elevation(SvElevation.Level3, shape, strength = { strength.value }),
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
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}
