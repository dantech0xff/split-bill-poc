package com.dantech.splitvibe.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable

/**
 * The top bar of a main screen: a large title that shrinks into a normal bar as the content
 * scrolls. Connect [scrollBehavior] to the scrolling content with `Modifier.nestedScroll`.
 */
@Composable
fun SvLargeTopBar(title: String, scrollBehavior: TopAppBarScrollBehavior, actions: @Composable RowScope.() -> Unit = {}) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}
