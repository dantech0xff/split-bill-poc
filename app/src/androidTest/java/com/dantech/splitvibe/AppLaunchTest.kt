package com.dantech.splitvibe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test
    fun appStartsAndOpensEachScreen() {
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
        compose.onNodeWithText("Contacts").assertIsDisplayed()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()
    }

    @Test
    fun openScreensComeBackAfterTheActivityIsRecreated() {
        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("New split").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Contacts").assertIsDisplayed()
    }
}
