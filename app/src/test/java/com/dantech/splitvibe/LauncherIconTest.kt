package com.dantech.splitvibe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
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
 * violet. The small icon of a notice is the same mark in one colour.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

    @Test
    fun `the icon is in the colours of the app's mark`() {
        lateinit var palette: SvPalette
        compose.setContent { SplitVibeTheme(darkTheme = false) { palette = SvTheme.palette } }
        compose.waitForIdle()
        val picture = playPicture(icon.background, icon.foreground)

        assertEquals("The ground", hex(palette.brand.toArgb()), hex(picture.colourAt(GROUND)))
        assertEquals("The white half", hex(palette.onBrand.toArgb()), hex(picture.colourAt(WHITE_HALF)))
        assertEquals("The lime half", hex(palette.accent.toArgb()), hex(picture.colourAt(LIME_HALF)))
    }

    @Test
    fun `Android 13 and later can draw it in the colours of the wallpaper`() {
        val monochrome = icon.monochrome
        assertNotNull("The icon has no monochrome layer for themed icons", monochrome)
        // A themed icon keeps only the shape of this layer: both halves, with the ground and the cut between them clear.
        val shape = playPicture(monochrome!!)

        assertShape("The monochrome layer", { shape.colourAt(it) }, filled = listOf(WHITE_HALF, LIME_HALF), clear = listOf(GROUND, CUT))
    }

    @Test
    fun `the small icon of a notice is the mark in one colour`() {
        val notice = Bitmap.createBitmap(NOTICE_SIZE, NOTICE_SIZE, Bitmap.Config.ARGB_8888)
        val drawable = context.getDrawable(R.drawable.ic_notification)!!
        drawable.setBounds(0, 0, NOTICE_SIZE, NOTICE_SIZE)
        drawable.draw(Canvas(notice))
        val scale = NOTICE_SIZE / NOTICE_GRID

        assertShape(
            "The notice's icon",
            { (x, y) -> notice.getPixel(x * scale, y * scale) },
            filled = listOf(NOTICE_LEFT_HALF, NOTICE_RIGHT_HALF),
            clear = listOf(NOTICE_CUT, NOTICE_CORNER),
        )
    }

    @Test
    fun `the picture for the Play listing is the icon, 512 pixels square`() {
        playPicture(icon.background, icon.foreground).captureRoboImage(File("src/test/screenshots/store-icon-512.png"))
    }

    /**
     * [layers] as the Play listing takes the icon: square, since Play rounds the corners itself, and of
     * the 108 units of the layers only the middle [VISIBLE], the most of them a launcher shows,
     * [PLAY_SIZE] pixels across.
     */
    private fun playPicture(vararg layers: Drawable): Bitmap {
        val picture = Bitmap.createBitmap(PLAY_SIZE, PLAY_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        val bleed = PLAY_SIZE * EDGE / VISIBLE
        layers.forEach { layer ->
            layer.setBounds(-bleed, -bleed, PLAY_SIZE + bleed, PLAY_SIZE + bleed)
            layer.draw(canvas)
        }
        return picture
    }

    /** The colour of the picture at [point], in the units of the layers. */
    private fun Bitmap.colourAt(point: Pair<Int, Int>): Int =
        getPixel((point.first - EDGE) * PLAY_SIZE / VISIBLE, (point.second - EDGE) * PLAY_SIZE / VISIBLE)

    /** Fails unless the shape that [colourAt] reads is opaque at each of [filled] and clear at each of [clear]. */
    private fun assertShape(what: String, colourAt: (Pair<Int, Int>) -> Int, filled: List<Pair<Int, Int>>, clear: List<Pair<Int, Int>>) {
        filled.forEach { point -> assertEquals("$what at $point", OPAQUE, Color.alpha(colourAt(point))) }
        clear.forEach { point -> assertEquals("$what at $point", CLEAR, Color.alpha(colourAt(point))) }
    }

    private fun hex(colour: Int): String = "#%08X".format(colour)

    private companion object {
        const val PLAY_SIZE = 512

        /**
         * Of the 108 units of an adaptive icon's layers, a launcher shows at most the middle 72, and its
         * mask always keeps the circle of 66 in the middle; the 18 on each side are for its effects.
         */
        const val VISIBLE = 72
        const val EDGE = 18

        /** Points well inside the ground, each half and the cut between them, in the units of the layers. */
        val GROUND = 22 to 22
        val WHITE_HALF = 40 to 52
        val LIME_HALF = 68 to 57
        val CUT = 54 to 54

        /** The notice's icon is drawn in a grid of 24, here ten pixels to a unit. */
        const val NOTICE_GRID = 24
        const val NOTICE_SIZE = 240

        /** Points well inside each half, in the cut between them and in a corner, in the notice's grid. */
        val NOTICE_LEFT_HALF = 5 to 11
        val NOTICE_RIGHT_HALF = 19 to 13
        val NOTICE_CUT = 12 to 12
        val NOTICE_CORNER = 1 to 1

        const val OPAQUE = 255
        const val CLEAR = 0
    }
}
