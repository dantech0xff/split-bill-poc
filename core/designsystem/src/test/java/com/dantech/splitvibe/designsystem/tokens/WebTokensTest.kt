package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The web pages that people open from a link use the app's colours, type, corners, spacing and
 * shadows, as CSS variables in `web/src/tokens.css`. This test writes that file from the tokens
 * here, and fails when the file and the tokens differ. After changing a token, write the file again:
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
        // Android draws the type in Roboto; a browser uses the system's own sans-serif face.
        appendLine("  --sv-font-family: system-ui, -apple-system, \"Segoe UI\", Roboto, sans-serif;")
        colors(LightColors).forEach { (name, color) -> appendLine("  --sv-$name: ${hex(color)};") }
        typeScale().forEach { (name, style) ->
            appendLine("  --sv-type-$name-size: ${px(style.fontSize.value)};")
            appendLine("  --sv-type-$name-line-height: ${px(style.lineHeight.value)};")
            appendLine("  --sv-type-$name-weight: ${style.fontWeight?.weight ?: NORMAL_WEIGHT};")
            appendLine("  --sv-type-$name-tracking: ${px(style.letterSpacing.value)};")
        }
        shapes().forEach { (name, corner) -> appendLine("  --sv-shape-$name: ${px(corner)};") }
        spacing().forEach { (name, space) -> appendLine("  --sv-space-$name: ${px(space.value)};") }
        val tint = LightColors.onPrimaryFixed
        SvElevation.entries.forEachIndexed { index, level ->
            appendLine("  --sv-elevation-${index + 1}: ${shadow(level.ambient, tint)}, ${shadow(level.key, tint)};")
            appendLine("  --sv-elevation-${index + 1}-surface: var(--sv-surface-container-lowest);")
        }
        appendLine("}")
        appendLine()
        // In the dark theme shadows are invisible, so a raised surface is a lighter one instead.
        appendLine("@media (prefers-color-scheme: dark) {")
        appendLine("  :root {")
        colors(DarkColors).forEach { (name, color) -> appendLine("    --sv-$name: ${hex(color)};") }
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

    private fun typeScale(): List<Pair<String, TextStyle>> = with(SvTypography) {
        listOf(
            "display-large" to displayLarge,
            "display-medium" to displayMedium,
            "display-small" to displaySmall,
            "headline-large" to headlineLarge,
            "headline-medium" to headlineMedium,
            "headline-small" to headlineSmall,
            "title-large" to titleLarge,
            "title-medium" to titleMedium,
            "title-small" to titleSmall,
            "body-large" to bodyLarge,
            "body-medium" to bodyMedium,
            "body-small" to bodySmall,
            "label-large" to labelLarge,
            "label-medium" to labelMedium,
            "label-small" to labelSmall,
        )
    }

    /** The corners of Material's shape scale, which the app uses as it is, and the app's own. */
    private fun shapes(): List<Pair<String, Float>> = with(Shapes()) {
        listOf(
            "extra-small" to corner(extraSmall),
            "small" to corner(small),
            "medium" to corner(medium),
            "large" to corner(large),
            "extra-large" to corner(extraLarge),
            "group-outer" to SvShapes.groupOuterCorner.value,
            "group-inner" to SvShapes.groupInnerCorner.value,
        )
    }

    private fun spacing(): List<Pair<String, Dp>> = listOf(
        "xs" to SvSpacing.xs,
        "sm" to SvSpacing.sm,
        "md" to SvSpacing.md,
        "lg" to SvSpacing.lg,
        "xl" to SvSpacing.xl,
        "xxl" to SvSpacing.xxl,
    )

    /** A corner in dp, read at a density of 1 on a shape large enough that no corner is cut short. */
    private fun corner(shape: CornerBasedShape): Float = shape.topStart.toPx(Size(LARGE_SHAPE, LARGE_SHAPE), Density(1f))

    private fun shadow(layer: ShadowLayer, tint: Color): String {
        val argb = tint.toArgb()
        val rgb = "${(argb shr 16) and 0xFF} ${(argb shr 8) and 0xFF} ${argb and 0xFF}"
        return "0 ${px(layer.offsetY.value)} ${px(layer.radius.value)} rgb($rgb / ${number(layer.alpha)})"
    }

    private fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    private fun px(value: Float): String = if (value == 0f) "0" else "${number(value)}px"

    private fun number(value: Float): String = value.toBigDecimal().stripTrailingZeros().toPlainString()

    private companion object {
        /** From `core/designsystem`, where the module's tests run. */
        const val TOKENS_FILE = "../../web/src/tokens.css"
        const val NORMAL_WEIGHT = 400
        const val LARGE_SHAPE = 1000f
    }
}
