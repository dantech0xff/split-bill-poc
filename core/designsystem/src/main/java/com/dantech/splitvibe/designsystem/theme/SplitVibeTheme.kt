package com.dantech.splitvibe.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.motion.rememberReducedMotion
import com.dantech.splitvibe.designsystem.tokens.DarkColors
import com.dantech.splitvibe.designsystem.tokens.LightColors
import com.dantech.splitvibe.designsystem.tokens.SvTypography

/** Whether the dark palette is in use. The elevation tokens need it: dark surfaces carry no shadow. */
internal val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * The look of Split Vibe: Material 3 Expressive with the violet palette and the springy
 * "expressive" motion. Every screen is wrapped in this once, at the top.
 */
@Composable
fun SplitVibeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme, LocalReducedMotion provides rememberReducedMotion()) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            motionScheme = MotionScheme.expressive(),
            typography = SvTypography,
            content = content,
        )
    }
}
