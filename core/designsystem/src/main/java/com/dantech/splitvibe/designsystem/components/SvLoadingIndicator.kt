package com.dantech.splitvibe.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.tokens.SvTheme

/**
 * Shows that work is running and its length is unknown: a shape that keeps changing form, violet
 * on the page and lime on [SvBottomBar]. With animations switched off in the system it is the same
 * shape standing still.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SvLoadingIndicator(modifier: Modifier = Modifier) {
    // Lime is what is active on the floating bar, as the selected tab of the navigation bar is;
    // violet would hardly show on the dark bar.
    val color = if (LocalSvGround.current == SvGround.Bar) SvTheme.palette.accent else MaterialTheme.colorScheme.primary
    if (LocalReducedMotion.current) {
        LoadingIndicator(progress = { 0f }, modifier = modifier, color = color)
    } else {
        LoadingIndicator(modifier = modifier, color = color)
    }
}
