package com.dantech.splitvibe.data.proxy

/** The length of this text as the proxy counts it: in code points, so an emoji counts once. */
internal fun String.codePointLength(): Int = codePointCount(0, length)

/** This text cut to at most [count] code points, so no emoji is cut in half. */
internal fun String.takeCodePoints(count: Int): String =
    if (codePointLength() <= count) this else substring(0, offsetByCodePoints(0, count))

/**
 * This text without the invisible formatting characters (Unicode's category Cf) that the proxy
 * takes out of every name and label before it checks and stores them. The zero-width joiner and
 * non-joiner stay, as they do on the proxy: emoji sequences and some scripts need them.
 */
internal fun String.withoutFormatting(): String {
    val kept = StringBuilder(length)
    codePoints().forEach { point -> if (!isTakenOut(point)) kept.appendCodePoint(point) }
    return kept.toString()
}

/** Whether this text shows nothing at all: it holds only spaces and joiners, if anything. */
internal fun String.showsNothing(): Boolean = codePoints().allMatch { Character.isWhitespace(it) || isJoiner(it) }

private fun isTakenOut(point: Int): Boolean = Character.getType(point) == Character.FORMAT.toInt() && !isJoiner(point)

private fun isJoiner(point: Int): Boolean = point == ZERO_WIDTH_NON_JOINER || point == ZERO_WIDTH_JOINER

private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D
