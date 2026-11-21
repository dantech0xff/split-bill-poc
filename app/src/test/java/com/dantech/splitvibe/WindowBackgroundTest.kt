package com.dantech.splitvibe

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.motion.SvMotion
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What lies behind the screens of the app. The window is what a cold start shows before the app
 * draws, and what the splash screen of Android 12 and later takes its colour from; the background
 * colour of the theme is what the list of recent apps shows for the app until it has a picture of
 * it; the layer the screens lie in shows between two screens while one fades out and the next fades
 * in. All three are the page of the theme the phone is in, so none of them ever shows the colours of
 * the platform's theme, which are not the page's in either theme.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-night-xhdpi")
class WindowBackgroundTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `in the dark theme the window behind the app is the colour of the page`() = assertTheWindowIsThePage(dark = true)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun `in the light theme the window behind the app is the colour of the page`() = assertTheWindowIsThePage(dark = false)

    @Test
    fun `halfway through a change of screen the page shows between the screens, not the window behind them`() {
        // The window of the platform's light theme, at its whitest, so that any of it that shows through is plain to see.
        compose.activityRule.scenario.onActivity { it.window.setBackgroundDrawable(ColorDrawable(Color.WHITE)) }
        compose.onNodeWithText("Recent bills").assertExists()
        val page = blankPage()

        compose.mainClock.autoAdvance = false
        compose.onNode(tab("Groups")).performClick()
        compose.mainClock.advanceTimeBy(HALF_A_SCREEN_CHANGE_MS)
        val between = blankPage()
        compose.mainClock.autoAdvance = true

        assertSameColour("Halfway from Home to Groups", page, between)
    }

    /**
     * Fails unless the window behind the app and the background colour of its theme are the colour of
     * the page, which is that of the [dark] or the light theme.
     */
    private fun assertTheWindowIsThePage(dark: Boolean) {
        val page = blankPage()
        // A page of the other theme would mean that the test does not run in the theme it is about.
        assertEquals("The page ${hex(page)} is not of the ${if (dark) "dark" else "light"} theme", dark, luminance(page) < MIDDLE_GREY)

        var window: Int? = null
        var background: Int? = null
        compose.activityRule.scenario.onActivity { activity ->
            window = (activity.window.decorView.background as? ColorDrawable)?.color
            val value = TypedValue()
            if (activity.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) background = value.data
        }

        assertEquals("The window is ${window?.let(::hex)}, the page ${hex(page)}", page, window)
        assertEquals("The background colour of the theme is ${background?.let(::hex)}, the page ${hex(page)}", page, background)
    }

    /**
     * The colour of the screen where the page shows on Home and on Groups alike: below what both show
     * at the top and above the navigation bar.
     */
    private fun blankPage(): Int {
        val frame: Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val y = frame.height * BLANK_HEIGHT_PERCENT / 100
        val colours = (1..BLANK_POINTS).map { index -> frame.getPixel(frame.width * index / (BLANK_POINTS + 1), y) }.distinct()
        assertEquals("The row at $y is not all page: ${colours.map(::hex)}", 1, colours.size)
        return colours.single()
    }

    private fun assertSameColour(what: String, expected: Int, actual: Int) {
        val apart = listOf(Color::red, Color::green, Color::blue).maxOf { channel -> abs(channel(expected) - channel(actual)) }
        assertTrue("$what the screen is ${hex(actual)} where the page is ${hex(expected)}", apart <= ROUNDING)
    }

    private fun luminance(colour: Int): Float = Color.valueOf(colour).luminance()

    private fun hex(colour: Int): String = "#%08X".format(colour)

    private fun tab(name: String) = hasContentDescription(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private companion object {
        /** Halfway through the fade from one screen to the next. */
        val HALF_A_SCREEN_CHANGE_MS = (SvMotion.screenChange<Float>() as TweenSpec<*>).durationMillis / 2L

        /** How far down the screen the row of page lies, below the content of both screens and above the bar. */
        const val BLANK_HEIGHT_PERCENT = 76
        const val BLANK_POINTS = 5

        /** The luminance between the dark page and the light one. */
        const val MIDDLE_GREY = 0.5f

        /** How far apart two colours may be, in a channel, from the rounding of blending one into the other. */
        const val ROUNDING = 2
    }
}
