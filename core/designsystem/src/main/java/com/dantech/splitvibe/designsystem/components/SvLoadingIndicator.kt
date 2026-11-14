package com.dantech.splitvibe.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion

/**
 * Shows that work is running and its length is unknown: a shape that keeps changing form.
 * With animations switched off in the system it is the same shape standing still.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SvLoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalReducedMotion.current) {
        LoadingIndicator(progress = { 0f }, modifier = modifier)
    } else {
        LoadingIndicator(modifier = modifier)
    }
}
