package com.dantech.splitvibe.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.catalog.CatalogScreen
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the catalog of design system components and compares it with the reference images. A
 * change to a token or a component shows up here even when no screen uses it yet.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Tall enough for the whole catalog at once, with the last section clear of the bottom bar and the floating button.
@Config(sdk = [34], qualifiers = "w412dp-h2240dp-xhdpi")
class ComponentCatalogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every component in the light theme`() = capture("catalog-light", darkTheme = false)

    @Test
    fun `every component in the dark theme`() = capture("catalog-dark", darkTheme = true)

    private fun capture(name: String, darkTheme: Boolean) {
        // The catalog shows endless animations. Its clock is moved by hand to a fixed moment, so the
        // picture is the same on every run.
        compose.mainClock.autoAdvance = false
        compose.setContent { SplitVibeTheme(darkTheme = darkTheme) { CatalogScreen() } }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }
}

private const val SETTLE_MS = 2_000L
