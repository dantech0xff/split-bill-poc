package com.dantech.splitvibe.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screens take their look from the design system and decide none of it themselves. That keeps the
 * app consistent, and a change in the Material library then has to be handled in one module only.
 */
class DesignSystemBoundaryTest {

    @Test
    fun `screens leave theme, colour, shadow and motion to the design system`() {
        val violations = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().flatMapIndexed { index, line ->
                    violationsIn(line).map { what -> "${file.path}:${index + 1} sets $what" }
                }
            }
            .toList()

        assertTrue(violations.joinToString("\n", prefix = "Move these into :core:designsystem:\n"), violations.isEmpty())
    }

    @Test
    fun `the rule recognises what it forbids and leaves alone what it allows`() {
        val forbiddenSamples = mapOf(
            "MaterialTheme(colorScheme = colors) {" to "its own theme",
            "MaterialExpressiveTheme {" to "its own theme",
            "@OptIn(ExperimentalMaterial3ExpressiveApi::class)" to "an experimental expressive API",
            "val accent = Color(0xFF554ACC)" to "a colour of its own",
            "Text(name, color = Color.White)" to "a colour of its own",
            "Modifier.shadow(4.dp, shape)" to "a shadow of its own",
            "Modifier.dropShadow(shape, shadow)" to "a shadow of its own",
            "Surface(tonalElevation = 3.dp) {" to "a shadow of its own",
            "animateFloatAsState(target, tween(300))" to "an animation timing of its own",
            "animationSpec = spring(dampingRatio = 0.6f)" to "an animation timing of its own",
        )
        forbiddenSamples.forEach { (line, what) -> assertEquals(line, listOf(what), violationsIn(line)) }

        val allowedSamples = listOf(
            "color = MaterialTheme.colorScheme.primary,",
            "style = MaterialTheme.typography.amountLarge,",
            "colors = ListItemDefaults.colors(containerColor = Color.Transparent),",
            "SvCard(elevation = SvElevation.Level2) {",
            "Modifier.elevation(SvElevation.Level3, shape)",
        )
        allowedSamples.forEach { line -> assertEquals(line, emptyList<String>(), violationsIn(line)) }
    }

    private fun violationsIn(line: String): List<String> = FORBIDDEN.filter { (_, pattern) ->
        pattern.containsMatchIn(line)
    }.map { it.first }
}

private val FORBIDDEN = listOf(
    "its own theme" to Regex("""\b(MaterialTheme|MaterialExpressiveTheme)\s*[({]"""),
    "an experimental expressive API" to Regex("""ExperimentalMaterial3ExpressiveApi"""),
    "a colour of its own" to Regex("""\bColor\(0x|\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b"""),
    "a shadow of its own" to Regex("""\b(dropShadow|innerShadow)\s*\(|\.shadow\(|\b(shadow|tonal)Elevation\b"""),
    "an animation timing of its own" to Regex("""\b(tween|spring|keyframes|repeatable|infiniteRepeatable)\s*\("""),
)
