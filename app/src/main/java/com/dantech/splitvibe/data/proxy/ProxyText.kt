package com.dantech.splitvibe.data.proxy

/** The length of this text as the proxy counts it: in code points, so an emoji counts once. */
internal fun String.codePointLength(): Int = codePointCount(0, length)

/** This text cut to at most [count] code points, so no emoji is cut in half. */
internal fun String.takeCodePoints(count: Int): String =
    if (codePointLength() <= count) this else substring(0, offsetByCodePoints(0, count))

/**
 * This text without the invisible formatting characters (Unicode's category Cf) that the proxy
 * takes out of every name and label before it checks and stores them.
 */
internal fun String.withoutFormatting(): String {
    val kept = StringBuilder(length)
    codePoints().forEach { point -> if (Character.getType(point) != Character.FORMAT.toInt()) kept.appendCodePoint(point) }
    return kept.toString()
}
