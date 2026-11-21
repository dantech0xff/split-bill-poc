package com.dantech.splitvibe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.SvPalette
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher icon, which the splash screen of Android 12 and later shows too and which the Play
 * listing shows as a picture of 512 pixels: the app's mark, a circle split in two, white and lime, on
 * violet.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {

    @get:Rule
    val compose = createComposeRule()

    private val icon = ApplicationProvider.getApplicationContext<Context>().getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

    @Test
    fun `the icon is in the colours of the app's mark`() {
        lateinit var palette: SvPalette
        compose.setContent { SplitVibeTheme(darkTheme = false) { palette = SvTheme.palette } }
        compose.waitForIdle()
        val picture = playPicture()

        assertEquals("The ground", hex(palette.brand.toArgb()), hex(picture.colourAt(GROUND)))
        assertEquals("The white half", hex(palette.onBrand.toArgb()), hex(picture.colourAt(WHITE_HALF)))
        assertEquals("The lime half", hex(palette.accent.toArgb()), hex(picture.colourAt(LIME_HALF)))
    }

    @Test
    fun `Android 13 and later can draw it in the colours of the wallpaper`() {
        assertNotNull("The icon has no monochrome layer for themed icons", icon.monochrome)
    }

    @Test
    fun `the picture for the Play listing is the icon, 512 pixels square`() {
        playPicture().captureRoboImage(File("src/test/screenshots/store-icon-512.png"))
    }

    /**
     * The icon as the Play listing takes it: square, since Play rounds the corners itself, and of the
     * 108 units of the layers only the middle [VISIBLE], which every launcher shows, [PLAY_SIZE] pixels
     * across.
     */
    private fun playPicture(): Bitmap {
        val picture = Bitmap.createBitmap(PLAY_SIZE, PLAY_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        val bleed = PLAY_SIZE * EDGE / VISIBLE
        listOf(icon.background, icon.foreground).forEach { layer ->
            layer.setBounds(-bleed, -bleed, PLAY_SIZE + bleed, PLAY_SIZE + bleed)
            layer.draw(canvas)
        }
        return picture
    }

    /** The colour of the picture at [point], in the units of the layers. */
    private fun Bitmap.colourAt(point: Pair<Int, Int>): Int =
        getPixel((point.first - EDGE) * PLAY_SIZE / VISIBLE, (point.second - EDGE) * PLAY_SIZE / VISIBLE)

    private fun hex(colour: Int): String = "#%08X".format(colour)

    private companion object {
        const val PLAY_SIZE = 512

        /** Of the 108 units of an adaptive icon's layers, the middle 72 show; the 18 on each side are for the launcher's effects. */
        const val VISIBLE = 72
        const val EDGE = 18

        /** Points well inside the ground and each half, in the units of the layers. */
        val GROUND = 22 to 22
        val WHITE_HALF = 40 to 52
        val LIME_HALF = 68 to 57
    }
}
