package com.dantech.splitvibe

import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.e2e.tab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the app on a device the way the launcher does and opens each screen. A local test covers
 * the same ground on the build machine; this one proves it on the real runtime, where a wrong
 * application class or a missing dependency only shows up when the app starts.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /**
     * From here on every tap also checks the screen it lands on: touch targets large enough,
     * text with enough contrast, and a label on everything that can be tapped.
     */
    @Before
    fun checkAccessibilityOnEveryScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) compose.enableAccessibilityChecks()
    }

    @Test
    fun appStartsAndOpensEachScreen() {
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()
    }

    /**
     * Opens each of the four main screens from its tab of the navigation bar, found as a screen reader
     * finds it, by its role and its name, round from Home to Home again. A tap checks the screen it is
     * made on before it lands, so the round checks every main screen for accessibility.
     */
    @Test
    fun eachTabOfTheNavigationBarOpensItsMainScreen() {
        compose.onNodeWithText("Recent bills").assertIsDisplayed()

        for ((name, screen) in MAIN_SCREENS) {
            compose.onNode(tab(name)).performClick()

            compose.onNode(screen).assertIsDisplayed()
            compose.onNode(tab(name)).assertIsSelected()
        }
    }

    @Test
    fun openScreensComeBackAfterTheActivityIsRecreated() {
        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("New split").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
    }

    private companion object {
        /** Each tab of the navigation bar in the order the round visits them, with what shows that its screen is open. */
        val MAIN_SCREENS: List<Pair<String, SemanticsMatcher>> = listOf(
            "Groups" to (hasText("Groups") and isHeading()),
            "Requests" to (hasText("Requests") and isHeading()),
            "Insights" to (hasText("Insights") and isHeading()),
            "Home" to hasText("Recent bills"),
        )
    }
}
