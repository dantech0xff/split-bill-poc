package com.dantech.splitvibe.domain

import com.dantech.splitvibe.data.contacts.Contact
import com.dantech.splitvibe.domain.NameMatcher.Match
import org.junit.Assert.assertEquals
import org.junit.Test

class NameMatcherTest {

    private val minh = Contact("1", "Trần Quang Minh", "Minh", "minh.tran")
    private val khoa = Contact("2", "Đỗ Minh Khoa", "Khoa", "khoa.do")
    private val ha = Contact("3", "Phạm Thu Hà", "Hà", "ha.pham")
    private val an = Contact("4", "Nguyễn Hoàng An", "An", "an.nguyen")
    private val huy = Contact("5", "Vũ Đức Huy", "Huy", "huy.vu")
    private val emmaW = Contact("6", "Emma Wilson", "Emma", "emma.w")
    private val emmaS = Contact("7", "Emma Schneider", "Emma", "emma.schneider")
    private val contacts = listOf(minh, khoa, ha, an, huy, emmaW, emmaS)

    @Test
    fun `given name identifies a person even when it is someone else's middle name`() {
        // "Minh" is Trần Quang Minh's given name and only a middle name of Đỗ Minh Khoa.
        assertEquals(Match.Found(minh), NameMatcher.match("Minh", contacts))
    }

    @Test
    fun `diacritics and case do not matter`() {
        assertEquals(Match.Found(ha), NameMatcher.match("ha", contacts))
        assertEquals(Match.Found(ha), NameMatcher.match("PHẠM THU HÀ", contacts))
        assertEquals(Match.Found(khoa), NameMatcher.match("do minh khoa", contacts))
    }

    @Test
    fun `surname or middle name alone is only a suggestion`() {
        // A friend called Nguyên who is not a contact must not turn into Nguyễn Hoàng An.
        assertEquals(Match.Ambiguous(listOf(an)), NameMatcher.match("Nguyên", contacts))
        assertEquals(Match.Ambiguous(listOf(huy)), NameMatcher.match("Đức", contacts))
        assertEquals(Match.Ambiguous(listOf(emmaW)), NameMatcher.match("Wilson", contacts))
    }

    @Test
    fun `given name with a surname narrows it down`() {
        assertEquals(Match.Found(minh), NameMatcher.match("Minh Trần", contacts))
        assertEquals(Match.Found(emmaS), NameMatcher.match("Emma Schneider", contacts))
    }

    @Test
    fun `two contacts with the same given name are ambiguous`() {
        assertEquals(Match.Ambiguous(listOf(emmaW, emmaS)), NameMatcher.match("Emma", contacts))
    }

    @Test
    fun `handle resolves an otherwise ambiguous name`() {
        assertEquals(Match.Found(emmaW), NameMatcher.match("@emma.w", contacts))
        assertEquals(Match.Found(emmaW), NameMatcher.match("emma w", contacts))
    }

    @Test
    fun `unknown or empty names do not match`() {
        assertEquals(Match.None, NameMatcher.match("Zoe", contacts))
        assertEquals(Match.None, NameMatcher.match("  ", contacts))
        assertEquals(Match.None, NameMatcher.match("Minh Zoe", contacts))
    }
}
