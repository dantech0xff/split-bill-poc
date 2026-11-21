package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
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

    @Test
    fun `the colours of Split Vibe's own palette are readable in the light theme`() = LightPalette.assertReadable(LightColors)

    @Test
    fun `the colours of Split Vibe's own palette are readable in the dark theme`() = DarkPalette.assertReadable(DarkColors)

    @Test
    fun `the ring of a choice of one that is not chosen stands out from the page and from a card or sheet, in both themes`() {
        // A card and a sheet lie on the surface of the first level: white in the light theme.
        val rings = listOf(
            Triple("light page", LightPalette.radioRing, LightColors.background),
            Triple("light card", LightPalette.radioRing, LightColors.surfaceContainerLowest),
            Triple("dark page", DarkPalette.radioRing, DarkColors.background),
            Triple("dark card", DarkPalette.radioRing, DarkColors.surfaceContainerLow),
        )
        val faint = rings.map { (name, ring, ground) -> name to contrast(ring, ground) }.filter { (_, ratio) -> ratio < OUTLINE_CONTRAST }
        assertTrue("Ring below $OUTLINE_CONTRAST:1: $faint", faint.isEmpty())
    }

    @Test
    fun `the bar of money coming back is the boards' green in both themes`() {
        assertEquals("light", BOARDS_GREEN, LightPalette.moneyInBar)
        assertEquals("dark", BOARDS_GREEN, DarkPalette.moneyInBar)
    }

    private fun ColorScheme.surfaces() = listOf(
        "surface" to surface,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    private fun ColorScheme.assertReadable() {
        val surfaces = surfaces()
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
            add(Triple("onSurfaceVariant on surfaceVariant", onSurfaceVariant, surfaceVariant))
            add(Triple("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface))
            add(Triple("inversePrimary on inverseSurface", inversePrimary, inverseSurface))
            add(Triple("onPrimaryFixed on primaryFixed", onPrimaryFixed, primaryFixed))
            add(Triple("onPrimaryFixedVariant on primaryFixedDim", onPrimaryFixedVariant, primaryFixedDim))
            add(Triple("onSecondaryFixed on secondaryFixed", onSecondaryFixed, secondaryFixed))
            add(Triple("onSecondaryFixedVariant on secondaryFixedDim", onSecondaryFixedVariant, secondaryFixedDim))
            add(Triple("onTertiaryFixed on tertiaryFixed", onTertiaryFixed, tertiaryFixed))
            add(Triple("onTertiaryFixedVariant on tertiaryFixedDim", onTertiaryFixedVariant, tertiaryFixedDim))
            for ((name, background) in surfaces) {
                add(Triple("onSurface on $name", onSurface, background))
                add(Triple("onSurfaceVariant on $name", onSurfaceVariant, background))
                // Coloured text and icons: links, the selected state, an error message.
                add(Triple("primary on $name", primary, background))
                add(Triple("error on $name", error, background))
            }
        }
        assertAllReadable(textPairs)

        val faintOutlines = surfaces
            .map { (name, background) -> "outline on $name" to contrast(outline, background) }
            .filter { (_, ratio) -> ratio < OUTLINE_CONTRAST }
        assertTrue("Outline below $OUTLINE_CONTRAST:1: $faintOutlines", faintOutlines.isEmpty())
    }

    // barOutline is left out on purpose: it is a thin decorative edge from the approved dark design,
    // and the bar is recognisable without it by its own colour, text and icons. moneyInBar is left out
    // too: the bar only draws an amount that the words beside it say, in moneyIn, which is checked.
    private fun SvPalette.assertReadable(scheme: ColorScheme) {
        val textPairs = buildList {
            add(Triple("onBrand on brand", onBrand, brand))
            add(Triple("onAccent on accent", onAccent, accent))
            add(Triple("onBar on bar", onBar, bar))
            add(Triple("onBarQuiet on bar", onBarQuiet, bar))
            add(Triple("onPerson on notice", onPerson, notice))
            person.forEachIndexed { index, color -> add(Triple("onPerson on person $index", onPerson, color)) }
            category.forEachIndexed { index, color -> add(Triple("onPerson on category $index", onPerson, color)) }
            for ((name, background) in scheme.surfaces()) {
                add(Triple("moneyIn on $name", moneyIn, background))
                add(Triple("moneyOut on $name", moneyOut, background))
            }
        }
        assertAllReadable(textPairs)
    }

    private fun assertAllReadable(pairs: List<Triple<String, Color, Color>>) {
        val tooFaint = pairs
            .map { (name, text, background) -> name to contrast(text, background) }
            .filter { (_, ratio) -> ratio < TEXT_CONTRAST }
        assertTrue("Text below $TEXT_CONTRAST:1: $tooFaint", tooFaint.isEmpty())
    }

    /** The contrast ratio of the accessibility guidelines: 1 for equal colours, 21 for black on white. */
    private fun contrast(a: Color, b: Color): Float {
        val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

private const val TEXT_CONTRAST = 4.5f

/** The green of the bars of what comes back on the approved boards, light and dark alike. */
private val BOARDS_GREEN = Color(0xFF17B383)
private const val OUTLINE_CONTRAST = 3f
