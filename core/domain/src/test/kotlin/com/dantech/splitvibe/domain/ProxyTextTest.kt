package com.dantech.splitvibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyTextTest {

    private val family = "👩‍👧"
    private val smile = "😀"

    @Test
    fun `text is counted and cut in code points, so no emoji is cut in half`() {
        assertEquals(1, smile.codePointLength())
        assertEquals(3, family.codePointLength())
        assertEquals(smile.repeat(2), smile.repeat(3).takeCodePoints(2))
        assertEquals("abc", "abc".takeCodePoints(5))
    }

    @Test
    fun `invisible formatting characters but the joiners are taken out, as the proxy does`() {
        // A reversal of the text, a zero-width space, a word joiner, a byte order mark and a language tag.
        assertEquals("naz Bun cha", "‮naz​ Bun⁠ cha﻿󠀁".withoutFormatting())
        assertEquals("$family ‌", "$family ‌".withoutFormatting())
    }

    @Test
    fun `text of nothing but spaces and joiners shows nothing`() {
        listOf("", " ", "‍", " ‌‍ ").forEach { assertTrue("[$it]", it.showsNothing()) }
        listOf("a", family, "​").forEach { assertFalse("[$it]", it.showsNothing()) }
    }

    @Test
    fun `a name goes on one line, as the proxy takes it`() {
        assertEquals("Pho bo", "  Pho\n\tbo\u0007 ".oneLine(80))
        assertEquals("Tip <b>", "Tip\r\n<b>".oneLine(80))
        assertEquals("naz", "‮naz​".oneLine(80))
        assertEquals("$family Mum", "$family Mum".oneLine(80))
        // Cut between characters, and trimmed again after the cut.
        assertEquals(smile.repeat(60), smile.repeat(61).oneLine(60))
        assertEquals("ab", "ab cd".oneLine(3))
        // Nothing to show is no name at all.
        assertEquals("", "​⁠".oneLine(80))
        assertEquals("", "﻿‍".oneLine(80))
        assertEquals("", " \u0000 ".oneLine(80))
    }
}
