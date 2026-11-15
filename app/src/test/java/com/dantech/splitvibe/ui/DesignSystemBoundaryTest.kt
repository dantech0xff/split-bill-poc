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
        val files = SOURCE_DIRECTORIES.flatMap { directory -> File(directory).walkTopDown().filter { it.extension == "kt" }.toList() }
        // A wrong working directory would otherwise make the test pass without looking at anything.
        assertTrue("Only ${files.size} source files found in $SOURCE_DIRECTORIES", files.size >= MIN_SOURCE_FILES)

        val violations = files.flatMap { file ->
            file.readLines().flatMapIndexed { index, line ->
                violationsIn(line, file.name).map { what -> "${file.path}:${index + 1} sets $what" }
            }
        }

        assertTrue(violations.joinToString("\n", prefix = "Move these into :core:designsystem:\n"), violations.isEmpty())
    }

    @Test
    fun `the rule recognises what it forbids and leaves alone what it allows`() {
        val forbiddenSamples = mapOf(
            "MaterialTheme(colorScheme = colors) {" to "its own theme",
            "MaterialExpressiveTheme {" to "its own theme",
            "SplitVibeTheme(darkTheme = true) {" to "its own theme",
            "@OptIn(ExperimentalMaterial3ExpressiveApi::class)" to "an experimental expressive API",
            "val accent = Color(0xFF554ACC)" to "a colour of its own",
            "val accent = Color(0.33f, 0.29f, 0.8f)" to "a colour of its own",
            "val accent = Color(red = 85, green = 74, blue = 204)" to "a colour of its own",
            "Text(name, color = colorResource(R.color.accent))" to "a colour of its own",
            "Text(name, color = Color.White)" to "a colour of its own",
            "Modifier.shadow(4.dp, shape)" to "a shadow of its own",
            "Modifier.dropShadow(shape, shadow)" to "a shadow of its own",
            "Surface(tonalElevation = 3.dp) {" to "a shadow of its own",
            "FloatingActionButton(onClick = onAdd) {" to "a shadow of its own",
            "ElevatedCard(modifier = Modifier.fillMaxWidth()) {" to "a shadow of its own",
            "elevation = CardDefaults.cardElevation(2.dp)," to "a shadow of its own",
            "DropdownMenu(expanded = open, onDismissRequest = {}) {" to "a shadow of its own",
            "enter = fadeIn(tween(300))," to "an animation timing of its own",
            "animationSpec = spring(dampingRatio = 0.6f)" to "an animation timing of its own",
            "val alpha by animateFloatAsState(if (open) 1f else 0f)" to "an animation of its own",
            "AnimatedVisibility(visible = open) {" to "an animation of its own",
            "Modifier.animateContentSize()" to "an animation of its own",
            "Crossfade(targetState = step) {" to "an animation of its own",
            "val scale = remember { Animatable(0f) }" to "an animation of its own",
            "delay(300)" to "a delay of its own",
        )
        forbiddenSamples.forEach { (line, what) -> assertEquals(line, listOf(what), violationsIn(line, "SomeScreen.kt")) }

        val allowedSamples = listOf(
            "color = MaterialTheme.colorScheme.primary,",
            "style = MaterialTheme.typography.amountLarge,",
            "colors = ListItemDefaults.colors(containerColor = Color.Transparent),",
            "SvCard(elevation = SvElevation.Level2) {",
            "Modifier.elevation(SvElevation.Level3, shape)",
            "SvPrimaryFab(text = \"Split a bill\", icon = Icons.Default.Add, onClick = onSplitBill)",
            "transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },",
            "val fade = SvMotion.screenChange<Float>()",
            "DropdownMenuItem(text = { Text(option.label) }, onClick = {})",
        )
        allowedSamples.forEach { line -> assertEquals(line, emptyList<String>(), violationsIn(line, "SomeScreen.kt")) }

        assertEquals(emptyList<String>(), violationsIn("SplitVibeTheme {", "MainActivity.kt"))
        assertEquals(emptyList<String>(), violationsIn("delay(PING_MS)", "ShareRepository.kt"))
    }

    private fun violationsIn(line: String, fileName: String): List<String> = FORBIDDEN
        .filter { rule -> fileName !in rule.allowedIn && rule.pattern.containsMatchIn(line) }
        .map { it.what }
}

/** What a screen may not decide itself, and the files that are the one place where it is decided. */
private class Rule(val what: String, val pattern: Regex, val allowedIn: Set<String> = emptySet())

private val FORBIDDEN = listOf(
    // The activities apply the app's theme once; nothing below them does.
    Rule("its own theme", Regex("""\b(MaterialTheme|MaterialExpressiveTheme)\s*[({]""")),
    Rule("its own theme", Regex("""\bSplitVibeTheme\s*[({]"""), allowedIn = setOf("MainActivity.kt", "CatalogActivity.kt")),
    Rule("an experimental expressive API", Regex("""ExperimentalMaterial3ExpressiveApi""")),
    Rule(
        "a colour of its own",
        Regex(
            """\bColor\(\s*(red\s*=|\d)|\bcolorResource\(|\bColor\.(White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|DarkGray|LightGray)\b""",
        ),
    ),
    Rule(
        "a shadow of its own",
        Regex(
            """\b(dropShadow|innerShadow)\s*\(|\.shadow\(|\b(shadow|tonal)Elevation\b|\b\w*FloatingActionButton\s*\(|\bElevated\w+\s*\(""" +
                """|\b\w+Defaults\.\w*[eE]levation\(|\bDropdownMenu\s*\(""",
        ),
    ),
    Rule("an animation timing of its own", Regex("""\b(tween|spring|keyframes|repeatable|infiniteRepeatable)\s*\(""")),
    Rule(
        "an animation of its own",
        Regex(
            """\b(AnimatedVisibility|AnimatedContent|Crossfade|Animatable|updateTransition|rememberInfiniteTransition)\s*\(""" +
                """|\banimate\w*AsState\s*\(|\banimateContentSize\s*\(""",
        ),
    ),
    // A live connection waits between attempts and pings to stay open: the network's timing, not motion's.
    Rule("a delay of its own", Regex("""\bdelay\s*\("""), allowedIn = setOf("ShareRepository.kt")),
)

/** The app's screens, and the catalog that shows the components in debug builds. */
private val SOURCE_DIRECTORIES = listOf("src/main/java", "src/debug/java")
private const val MIN_SOURCE_FILES = 20
