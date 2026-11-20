package com.dantech.splitvibe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.amountHero
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app draws its text in the two fonts it ships with. When a font file cannot be loaded, the text
 * falls back to the phone's default font without any error, and every screenshot then shows the
 * wrong font. Text is measured here with each bundled family and with the default font: the same
 * width would mean the bundled font was not used.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FontLoadingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each bundled font file draws, not the default font`() {
        // One style for each of the seven files: a family picks its file by weight.
        val widths = measure { measurer, type ->
            mapOf(
                "Bricolage Grotesque Bold" to type.amountSmall,
                "Bricolage Grotesque ExtraBold" to type.headlineSmall,
                "Bricolage Grotesque ExtraBold, display cut" to type.displayLarge,
                "Be Vietnam Pro Regular" to type.bodyLarge,
                "Be Vietnam Pro Medium" to type.bodyLarge.copy(fontWeight = FontWeight.Medium),
                "Be Vietnam Pro SemiBold" to type.labelLarge,
                "Be Vietnam Pro Bold" to type.titleMediumEmphasized,
            ).mapValues { (_, style) ->
                // Measured large, so that two fonts of similar width cannot round to the same number of pixels.
                val large = style.copy(fontSize = LARGE, lineHeight = LARGE)
                measurer.width(SAMPLE, large) to measurer.width(SAMPLE, large.copy(fontFamily = FontFamily.Default))
            }
        }
        val defaultLooking = widths.filterValues { (own, default) -> own == default }.keys
        assertTrue("Drawn with the default font: $defaultLooking (widths $widths)", defaultLooking.isEmpty())
    }

    @Test
    fun `amounts draw their digits at one width only thanks to the equal-width digits`() {
        // Bricolage Grotesque's plain digits differ in width, while the default font's do not. So the
        // uneven widths without the feature show that the bundled font is in use, and the even widths
        // with it show that the feature works.
        val widths = measure { measurer, type ->
            mapOf(
                "amountHero" to type.amountHero,
                "amountLarge" to type.amountLarge,
                "amountMedium" to type.amountMedium,
                "amountSmall" to type.amountSmall,
            ).mapValues { (_, style) ->
                val plain = style.copy(fontFeatureSettings = null)
                listOf(
                    measurer.width("1111", style),
                    measurer.width("8888", style),
                    measurer.width("1111", plain),
                    measurer.width("8888", plain),
                )
            }
        }
        widths.forEach { (name, measured) ->
            val (ones, eights, plainOnes, plainEights) = measured
            assertEquals("$name: 1111 and 8888 with equal-width digits", ones, eights)
            assertNotEquals("$name: 1111 and 8888 without them, which only the bundled font draws apart", plainOnes, plainEights)
        }
    }

    private fun <T> measure(block: (TextMeasurer, Typography) -> T): T {
        var result: T? = null
        compose.setContent {
            SplitVibeTheme(darkTheme = false) {
                val measurer = rememberTextMeasurer()
                result = block(measurer, MaterialTheme.typography)
            }
        }
        compose.waitForIdle()
        return checkNotNull(result)
    }

    private fun TextMeasurer.width(text: String, style: TextStyle): Int = measure(text, style).size.width

    private companion object {
        /** Capitals with stacked Vietnamese marks, digits with separators and the dong sign. */
        const val SAMPLE = "QUÁN ỐC BA MIỀN ₫1.111.111"
        val LARGE = 120.sp
    }
}
