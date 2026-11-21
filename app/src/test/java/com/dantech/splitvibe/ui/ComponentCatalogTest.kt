package com.dantech.splitvibe.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.catalog.CatalogScreen
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the catalog of design system components and compares it with the reference images. A
 * change to a token or a component shows up here even when no screen uses it yet.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Tall enough for the whole catalog at once, with the last section clear of the bottom bar and the
// floating button. The pictures are drawn at 1x: one this tall at 2x does not fit in the memory of the test.
@Config(sdk = [34], qualifiers = "w412dp-h12000dp-mdpi")
class ComponentCatalogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `every component in the light theme`() = capture("catalog-light", darkTheme = false)

    @Test
    fun `every component in the dark theme`() = capture("catalog-dark", darkTheme = true)

    /**
     * Text at twice its size must still fit in every component, Vietnamese marks and all. The catalog
     * is then so tall that this picture is drawn at 0.75x: the renderer leaves out the labels of
     * buttons that lie beyond the 16,384 pixels a surface it draws into can have.
     */
    @Test
    @Config(qualifiers = "w412dp-h19400dp-ldpi")
    fun `every component with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        capture("catalog-large-text", darkTheme = false)
    }

    private fun capture(name: String, darkTheme: Boolean) {
        // The catalog shows endless animations. Its clock is moved by hand to a fixed moment, so the
        // picture is the same on every run.
        compose.mainClock.autoAdvance = false
        compose.setContent { SplitVibeTheme(darkTheme = darkTheme) { CatalogScreen() } }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
        // The picture is as tall as the window, so a catalog that grew longer than the window would
        // lose its last sections without a word: its list must have nothing left to scroll to.
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertFalse("The catalog runs on below the bottom of the picture", list.value() < list.maxValue())
    }
}

private const val SETTLE_MS = 2_000L
