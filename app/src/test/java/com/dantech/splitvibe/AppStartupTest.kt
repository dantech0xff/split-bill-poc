package com.dantech.splitvibe

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the real application class and the real activity, as the launcher would, and moves
 * between the screens. It fails if the manifest, the dependency module and the navigation
 * no longer fit together.
 */
@RunWith(AndroidJUnit4::class)
class AppStartupTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app starts on the home screen and moves between screens`() {
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
        compose.onNodeWithText("Contacts").assertIsDisplayed()

        compose.onNodeWithText("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Contacts").assertIsDisplayed()
    }

    @Test
    fun `a second start in the same process works`() {
        // Each test gets a new application object; the dependency container must cope with that.
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
    }
}
