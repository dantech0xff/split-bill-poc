package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every style of the type scale draws in one of the two bundled fonts: a style left out would fall
 * back to the phone's default font on the screens that use it. That the fonts really load is checked
 * on a rendered screen, by FontLoadingTest in the app.
 */
class TypographyTest {

    private val plain = with(SvTypography) {
        listOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall,
        )
    }

    private val emphasized = with(SvTypography) {
        listOf(
            displayLargeEmphasized,
            displayMediumEmphasized,
            displaySmallEmphasized,
            headlineLargeEmphasized,
            headlineMediumEmphasized,
            headlineSmallEmphasized,
            titleLargeEmphasized,
            titleMediumEmphasized,
            titleSmallEmphasized,
            bodyLargeEmphasized,
            bodyMediumEmphasized,
            bodySmallEmphasized,
            labelLargeEmphasized,
            labelMediumEmphasized,
            labelSmallEmphasized,
        ).mapIndexed { index, style -> "${plain[index].first}Emphasized" to style }
    }

    private val amounts = with(SvTypography) {
        listOf("amountHero" to amountHero, "amountLarge" to amountLarge, "amountMedium" to amountMedium, "amountSmall" to amountSmall)
    }

    private val bundled = setOf(DisplayFamily, DisplayHeroFamily, TextFamily)

    @Test
    fun `every style draws in a bundled font`() {
        val stray = (plain + emphasized + amounts).filter { (_, style) -> style.fontFamily !in bundled }.map { it.first }
        assertTrue("Styles that use another font, or the phone's default one: $stray", stray.isEmpty())
    }

    @Test
    fun `titles and amounts are set in Bricolage Grotesque, the rest in Be Vietnam Pro`() {
        val bricolage = setOf(DisplayFamily, DisplayHeroFamily)
        val titles = setOf("display", "headline", "titleLarge")
        val wrong = (plain + emphasized).filter { (name, style) ->
            val isTitle = titles.any { name.startsWith(it) }
            (style.fontFamily in bricolage) != isTitle
        }.map { it.first } + amounts.filter { (_, style) -> style.fontFamily !in bricolage }.map { it.first }
        assertTrue("Styles in the wrong font: $wrong", wrong.isEmpty())
    }

    @Test
    fun `only the largest sizes use the cut drawn for display sizes`() {
        val hero = (plain + emphasized + amounts).filter { (_, style) -> style.fontFamily == DisplayHeroFamily }.map { it.first }
        assertEquals(listOf("displayLarge", "displayLargeEmphasized", "amountHero"), hero)
    }

    @Test
    fun `amounts have digits of equal width`() {
        val proportional = amounts.filterNot { (_, style) -> style.fontFeatureSettings.orEmpty().contains("tnum") }.map { it.first }
        assertTrue("Amount styles without equal-width digits: $proportional", proportional.isEmpty())
    }

    @Test
    fun `an emphasized style keeps the size and line height of its plain style`() {
        val changed = plain.zip(emphasized).filterNot { (base, strong) -> base.second.sameSize(strong.second) }.map { it.second.first }
        assertTrue("Emphasized styles of another size: $changed", changed.isEmpty())
    }

    private fun TextStyle.sameSize(other: TextStyle) = fontSize == other.fontSize && lineHeight == other.lineHeight
}
