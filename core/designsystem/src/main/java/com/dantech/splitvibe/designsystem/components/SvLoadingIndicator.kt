package com.dantech.splitvibe.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shows that work is running and its length is unknown: a shape that keeps changing form. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SvLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingIndicator(modifier = modifier)
}
