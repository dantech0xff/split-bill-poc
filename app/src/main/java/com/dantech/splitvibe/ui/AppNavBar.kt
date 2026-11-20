package com.dantech.splitvibe.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvNavBar
import com.dantech.splitvibe.designsystem.components.SvNavIcons
import com.dantech.splitvibe.designsystem.components.SvNavItem
import com.dantech.splitvibe.designsystem.motion.sharedSurface
import com.dantech.splitvibe.ui.requests.RequestTotals
import com.dantech.splitvibe.ui.requests.waitingCount

/**
 * The navigation bar of the four main screens, Home, Groups, Requests and Insights, with the button
 * that starts a split in the middle. Each main screen puts it in the `bottomBar` of its scaffold; the
 * other screens have none.
 *
 * [selected] is the main screen it is on. The Requests tab counts the requests in [requests] that
 * wait for the user. The middle button reads as "Split a bill" and grows into the split screen that
 * [onSplitBill] opens.
 */
@Composable
fun AppNavBar(selected: NavKey, requests: RequestTotals, onSelect: (NavKey) -> Unit, onSplitBill: () -> Unit) {
    SvNavBar(
        items = listOf(
            SvNavItem(stringResource(R.string.nav_home), SvNavIcons.Home),
            SvNavItem(stringResource(R.string.groups_title), SvNavIcons.Groups),
            SvNavItem(stringResource(R.string.requests_title), SvNavIcons.Requests, count = requests.waitingCount),
            SvNavItem(stringResource(R.string.home_insights), SvNavIcons.Insights),
        ),
        selected = MAIN_SCREENS.indexOf(selected),
        onSelect = { index -> onSelect(MAIN_SCREENS[index]) },
        centerLabel = stringResource(R.string.home_split_a_bill),
        centerIcon = SvNavIcons.Split,
        onCenterClick = onSplitBill,
        // One bar for the four screens: while one of them gives way to another, the bar stays in place.
        modifier = Modifier.sharedSurface(BAR_SURFACE),
        centerModifier = Modifier.sharedSurface(SPLIT_SURFACE, resize = true),
    )
}

/** The navigation bar carries this name on every main screen, so it is one bar across them. */
private const val BAR_SURFACE = "bottom-bar"
