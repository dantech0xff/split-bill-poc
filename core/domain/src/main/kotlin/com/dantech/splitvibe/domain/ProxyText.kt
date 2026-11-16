package com.dantech.splitvibe.domain

// Text as the proxy counts and takes it. The proxy checks every name, label and sentence it is sent
// by these rules, so whatever goes to it is shaped by them first: bill links, payment requests and
// changes of a split by a sentence alike.

/** The length of this text as the proxy counts it: in code points, so an emoji counts once. */
fun String.codePointLength(): Int = codePointCount(0, length)

/** This text cut to at most [count] code points, so no emoji is cut in half. */
fun String.takeCodePoints(count: Int): String = if (codePointLength() <= count) this else substring(0, offsetByCodePoints(0, count))

/**
 * This text without the invisible formatting characters (Unicode's category Cf) that the proxy
 * takes out of every name and label before it checks and stores them. The zero-width joiner and
 * non-joiner stay, as they do on the proxy: emoji sequences and some scripts need them.
 */
fun String.withoutFormatting(): String {
    val kept = StringBuilder(length)
    codePoints().forEach { point -> if (!isTakenOut(point)) kept.appendCodePoint(point) }
    return kept.toString()
}

/** Whether this text shows nothing at all: it holds only spaces and joiners, if anything. */
fun String.showsNothing(): Boolean = codePoints().allMatch { Character.isWhitespace(it) || isJoiner(it) }

/**
 * This text as the proxy takes a name or a label: on one line, with the control characters it
 * refuses, line breaks among them, as spaces and without the formatting characters it takes out;
 * each run of spaces as one, trimmed, and at most [max] code points. Text that would show nothing
 * is empty, which is how the proxy counts it.
 */
fun String.oneLine(max: Int): String {
    val kept = StringBuilder(length)
    codePoints().forEach { point ->
        when {
            Character.isISOControl(point) -> kept.append(' ')
            isTakenOut(point) -> Unit
            else -> kept.appendCodePoint(point)
        }
    }
    return kept.replace(SPACES, " ").trim().takeCodePoints(max).trim().takeUnless { it.showsNothing() }.orEmpty()
}

private fun isTakenOut(point: Int): Boolean = Character.getType(point) == Character.FORMAT.toInt() && !isJoiner(point)

private fun isJoiner(point: Int): Boolean = point == ZERO_WIDTH_NON_JOINER || point == ZERO_WIDTH_JOINER

private val SPACES = Regex(" {2,}")
private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
