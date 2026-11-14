package com.dantech.splitvibe.designsystem.motion

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Lets a row of a lazy list fade in when it is added, fade out when it is removed, and slide to
 * its new place when the rows above it change, so the eye can follow what moved. Give the row a
 * key of its own in the list, and put this on the row's outermost element.
 */
@Composable
fun LazyItemScope.rowMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
)
