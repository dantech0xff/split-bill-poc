package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The web pages that people open from a link use the app's design: its colours and those of its
 * palette, its type, the corners of its shapes, its spacing and its shadows, as CSS variables in
 * `web/src/tokens.css`. Of the type, the palette and the shapes, the file holds only what
 * `web/src/app.css` uses; the colours of Material's roles are all there.
 * This test writes that file from the tokens here, and fails when the file and the tokens differ.
 * After changing a token, write the file again:
 *
 * ./gradlew :core:designsystem:testDebugUnitTest -PwebTokens=write
 */
class WebTokensTest {

    @Test
    fun `the web pages use the app's tokens`() {
        val file = File(TOKENS_FILE)
        val css = webTokensCss()
        if (System.getProperty("sv.webTokens") == "write") {
            file.parentFile?.mkdirs()
            file.writeText(css)
            return
        }
        val written = if (file.exists()) file.readText() else ""
        assertEquals("$TOKENS_FILE is out of date. Write it again with -PwebTokens=write", css, written)
    }

    private fun webTokensCss(): String = buildString {
        appendLine("/* Written from the app's design system by WebTokensTest in core/designsystem. Do not edit by hand. */")
        appendLine(":root {")
        appendLine("  color-scheme: light dark;")
        // The two families the app ships, which the pages load from files of their own; until a file
        // has loaded, a browser shows the text in the face that follows.
        appendLine("  --sv-font-display: $DISPLAY_FONT;")
        appendLine("  --sv-font-text: $TEXT_FONT;")
        colors(LightColors).forEach { (name, color) -> appendLine("  --sv-$name: ${hex(color)};") }
        palette(LightPalette).forEach { (name, color) -> appendLine("  --sv-$name: ${hex(color)};") }
        typeScale().forEach { (name, style) ->
            appendLine("  --sv-type-$name-family: var(--sv-font-${familyOf(name, style.fontFamily)});")
            appendLine("  --sv-type-$name-size: ${px(style.fontSize.value)};")
            appendLine("  --sv-type-$name-line-height: ${px(style.lineHeight.value)};")
            appendLine("  --sv-type-$name-weight: ${style.fontWeight?.weight ?: NORMAL_WEIGHT};")
            appendLine("  --sv-type-$name-tracking: ${length(style.letterSpacing)};")
            style.fontFeatureSettings?.let { appendLine("  --sv-type-$name-features: \"$it\";") }
        }
        shapes().forEach { (name, corner) -> appendLine("  --sv-shape-$name: ${px(corner)};") }
        spacing().forEach { (name, space) -> appendLine("  --sv-space-$name: ${px(space.value)};") }
        SvElevation.entries.forEachIndexed { index, level ->
            val shadows = level.layers.joinToString(", ") { shadow(it, ShadowColor) }.ifEmpty { "none" }
            appendLine("  --sv-elevation-${index + 1}: $shadows;")
            appendLine("  --sv-elevation-${index + 1}-surface: var(--sv-surface-container-lowest);")
        }
        appendLine("}")
        appendLine()
        // In the dark theme shadows are invisible, so a raised surface is a lighter one instead.
        appendLine("@media (prefers-color-scheme: dark) {")
        appendLine("  :root {")
        colors(DarkColors).forEach { (name, color) -> appendLine("    --sv-$name: ${hex(color)};") }
        palette(DarkPalette).forEach { (name, color) -> appendLine("    --sv-$name: ${hex(color)};") }
        val darkSurfaces = listOf("surface-container-low", "surface-container", "surface-container-high", "surface-container-highest")
        SvElevation.entries.forEachIndexed { index, _ ->
            appendLine("    --sv-elevation-${index + 1}: none;")
            appendLine("    --sv-elevation-${index + 1}-surface: var(--sv-${darkSurfaces[index]});")
        }
        appendLine("  }")
        appendLine("}")
    }

    private fun colors(scheme: ColorScheme): List<Pair<String, Color>> = with(scheme) {
        listOf(
            "primary" to primary,
            "on-primary" to onPrimary,
            "primary-container" to primaryContainer,
            "on-primary-container" to onPrimaryContainer,
            "inverse-primary" to inversePrimary,
            "secondary" to secondary,
            "on-secondary" to onSecondary,
            "secondary-container" to secondaryContainer,
            "on-secondary-container" to onSecondaryContainer,
            "tertiary" to tertiary,
            "on-tertiary" to onTertiary,
            "tertiary-container" to tertiaryContainer,
            "on-tertiary-container" to onTertiaryContainer,
            "background" to background,
            "on-background" to onBackground,
            "surface" to surface,
            "on-surface" to onSurface,
            "surface-variant" to surfaceVariant,
            "on-surface-variant" to onSurfaceVariant,
            "surface-tint" to surfaceTint,
            "inverse-surface" to inverseSurface,
            "inverse-on-surface" to inverseOnSurface,
            "error" to error,
            "on-error" to onError,
            "error-container" to errorContainer,
            "on-error-container" to onErrorContainer,
            "outline" to outline,
            "outline-variant" to outlineVariant,
            "scrim" to scrim,
            "surface-bright" to surfaceBright,
            "surface-dim" to surfaceDim,
            "surface-container-lowest" to surfaceContainerLowest,
            "surface-container-low" to surfaceContainerLow,
            "surface-container" to surfaceContainer,
            "surface-container-high" to surfaceContainerHigh,
            "surface-container-highest" to surfaceContainerHighest,
            "primary-fixed" to primaryFixed,
            "primary-fixed-dim" to primaryFixedDim,
            "on-primary-fixed" to onPrimaryFixed,
            "on-primary-fixed-variant" to onPrimaryFixedVariant,
            "secondary-fixed" to secondaryFixed,
            "secondary-fixed-dim" to secondaryFixedDim,
            "on-secondary-fixed" to onSecondaryFixed,
            "on-secondary-fixed-variant" to onSecondaryFixedVariant,
            "tertiary-fixed" to tertiaryFixed,
            "tertiary-fixed-dim" to tertiaryFixedDim,
            "on-tertiary-fixed" to onTertiaryFixed,
            "on-tertiary-fixed-variant" to onTertiaryFixedVariant,
        )
    }

    /**
     * The colours of [SvPalette] the pages use: the violet block of a page's head, the lime of its
     * tags and ticks, the ink of a closed bill's banner, the yellow behind a notice's icon, and the
     * colours of people, in the order a person's colour is picked from.
     */
    private fun palette(palette: SvPalette): List<Pair<String, Color>> = with(palette) {
        listOf(
            "brand" to brand,
            "on-brand" to onBrand,
            "accent" to accent,
            "on-accent" to onAccent,
            "bar" to bar,
            "on-bar" to onBar,
            "notice" to notice,
            "on-person" to onPerson,
        ) + person.mapIndexed { index, color -> "person-$index" to color }
    }

    /** The styles of the app's type scale that the pages use, those of the classes their markup carries among them. */
    private fun typeScale(): List<Pair<String, TextStyle>> = with(SvTypography) {
        listOf(
            "display-small" to displaySmall,
            "headline-medium" to headlineMedium,
            "headline-small" to headlineSmall,
            "title-large" to titleLarge,
            "title-medium" to titleMedium,
            "title-medium-emphasized" to titleMediumEmphasized,
            "title-small" to titleSmall,
            "body-large" to bodyLarge,
            "body-large-emphasized" to bodyLargeEmphasized,
            "body-medium" to bodyMedium,
            "body-medium-emphasized" to bodyMediumEmphasized,
            "body-small" to bodySmall,
            "body-small-emphasized" to bodySmallEmphasized,
            "label-medium" to labelMedium,
            "amount-hero" to amountHero,
            "amount-medium" to amountMedium,
            "amount-small" to amountSmall,
        )
    }

    /**
     * Which of the two families of the pages sets the style [name] in [family]: `display` for
     * Bricolage Grotesque, of which the pages have the cut for text sizes only, and `text` for Be
     * Vietnam Pro.
     */
    private fun familyOf(name: String, family: FontFamily?): String = when (family) {
        DisplayFamily, DisplayHeroFamily -> "display"
        TextFamily -> "text"
        else -> error("$name is set in a family the web pages do not load: $family")
    }

    /** The corners of the app's shape scale, from its smallest step to its largest. */
    private fun shapes(): List<Pair<String, Float>> = with(SvShapeScale) {
        listOf(
            "extra-small" to corner(extraSmall),
            "small" to corner(small),
            "medium" to corner(medium),
            "large" to corner(large),
            "extra-large" to corner(extraLarge),
        )
    }

    private fun spacing(): List<Pair<String, Dp>> = listOf(
        "xs" to SvSpacing.xs,
        "sm" to SvSpacing.sm,
        "md" to SvSpacing.md,
        "lg" to SvSpacing.lg,
        "xl" to SvSpacing.xl,
        "xxl" to SvSpacing.xxl,
        "screen" to SvSpacing.screen,
    )

    /** A corner in dp, read at a density of 1 on a shape large enough that no corner is cut short. */
    private fun corner(shape: CornerBasedShape): Float = shape.topStart.toPx(Size(LARGE_SHAPE, LARGE_SHAPE), Density(1f))

    private fun shadow(layer: ShadowLayer, tint: Color): String {
        val argb = tint.toArgb()
        val rgb = "${(argb shr 16) and 0xFF} ${(argb shr 8) and 0xFF} ${argb and 0xFF}"
        return "0 ${px(layer.offsetY.value)} ${px(layer.radius.value)} rgb($rgb / ${number(layer.alpha)})"
    }

    private fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    /** A length of type, such as the tracking of a style: in em when the style gives em, else in px, as sp are at a density of 1. */
    private fun length(value: TextUnit): String = when {
        value.value == 0f -> "0"
        value.isEm -> "${number(value.value)}em"
        else -> px(value.value)
    }

    private fun px(value: Float): String = if (value == 0f) "0" else "${number(value)}px"

    private fun number(value: Float): String = value.toBigDecimal().stripTrailingZeros().toPlainString()

    private companion object {
        /** From `core/designsystem`, where the module's tests run. */
        const val TOKENS_FILE = "../../web/src/tokens.css"
        const val NORMAL_WEIGHT = 400
        const val LARGE_SHAPE = 1000f

        /** Bricolage Grotesque, then, until its file has loaded, a heavy face of the system's of about its width. */
        const val DISPLAY_FONT = "\"Bricolage Grotesque\", \"Arial Black\", system-ui, sans-serif"

        /** Be Vietnam Pro, then, until its file has loaded, the system's own face. */
        const val TEXT_FONT = "\"Be Vietnam Pro\", system-ui, sans-serif"
    }
}
