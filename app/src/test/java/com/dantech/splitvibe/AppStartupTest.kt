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
import org.robolectric.annotation.Config

/**
 * Starts the real application class and the real activity, as the launcher would, and moves
 * between the screens. It fails if the manifest, the dependency module and the navigation
 * no longer fit together.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AppStartupTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app starts on the home screen and moves between screens`() {
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
        compose.onNodeWithText("Contacts").assertIsDisplayed()

        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        // The arrow in the top bar, as opposed to the system back button.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Contacts").assertIsDisplayed()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Local currency").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Contacts").assertIsDisplayed()
    }

    @Test
    fun `a second start in the same process works`() {
        // Each test gets a new application object; the dependency container must cope with that.
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
    }

    @Test
    fun `the open screens come back after the system recreates the activity`() {
        compose.onNodeWithText("Split a bill").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("New split").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Contacts").assertIsDisplayed()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Local currency").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Contacts").assertIsDisplayed()
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }
}
