package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text has to stay readable on every background the palette pairs it with. The accessibility
 * guidelines ask for a contrast of 4.5:1 for text and 3:1 for the outline of a control.
 */
class ColorTokensTest {

    @Test
    fun `text is readable on its background in the light palette`() = LightColors.assertReadable()

    @Test
    fun `text is readable on its background in the dark palette`() = DarkColors.assertReadable()

    private fun ColorScheme.assertReadable() {
        val surfaces = listOf(
            "surface" to surface,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
        )
        val textPairs = buildList {
            add(Triple("onPrimary on primary", onPrimary, primary))
            add(Triple("onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer))
            add(Triple("onSecondary on secondary", onSecondary, secondary))
            add(Triple("onSecondaryContainer on secondaryContainer", onSecondaryContainer, secondaryContainer))
            add(Triple("onTertiary on tertiary", onTertiary, tertiary))
            add(Triple("onTertiaryContainer on tertiaryContainer", onTertiaryContainer, tertiaryContainer))
            add(Triple("onError on error", onError, error))
            add(Triple("onErrorContainer on errorContainer", onErrorContainer, errorContainer))
            add(Triple("onBackground on background", onBackground, background))
            add(Triple("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface))
            add(Triple("inversePrimary on inverseSurface", inversePrimary, inverseSurface))
            add(Triple("onPrimaryFixed on primaryFixed", onPrimaryFixed, primaryFixed))
            add(Triple("onPrimaryFixedVariant on primaryFixedDim", onPrimaryFixedVariant, primaryFixedDim))
            add(Triple("onSecondaryFixedVariant on secondaryFixedDim", onSecondaryFixedVariant, secondaryFixedDim))
            add(Triple("onTertiaryFixedVariant on tertiaryFixedDim", onTertiaryFixedVariant, tertiaryFixedDim))
            for ((name, background) in surfaces) {
                add(Triple("onSurface on $name", onSurface, background))
                add(Triple("onSurfaceVariant on $name", onSurfaceVariant, background))
                // Coloured text and icons: links, the selected state, an error message.
                add(Triple("primary on $name", primary, background))
                add(Triple("error on $name", error, background))
            }
        }
        val tooFaint = textPairs
            .map { (name, text, background) -> name to contrast(text, background) }
            .filter { (_, ratio) -> ratio < TEXT_CONTRAST }
        assertTrue("Text below $TEXT_CONTRAST:1: $tooFaint", tooFaint.isEmpty())

        val faintOutlines = surfaces
            .map { (name, background) -> "outline on $name" to contrast(outline, background) }
            .filter { (_, ratio) -> ratio < OUTLINE_CONTRAST }
        assertTrue("Outline below $OUTLINE_CONTRAST:1: $faintOutlines", faintOutlines.isEmpty())
    }

    /** The contrast ratio of the accessibility guidelines: 1 for equal colours, 21 for black on white. */
    private fun contrast(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

private const val TEXT_CONTRAST = 4.5f
private const val OUTLINE_CONTRAST = 3f
