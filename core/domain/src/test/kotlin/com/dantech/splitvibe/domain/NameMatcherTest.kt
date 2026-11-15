package com.dantech.splitvibe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameMatcherTest {

    private val minh = Person("person-1", "Minh")
    private val minhTran = Person("person-2", "Trần Quang Minh")
    private val ha = Person("person-3", "Phạm Thu Hà")
    private val emmaW = Person("person-4", "Emma Wilson")
    private val emmaS = Person("person-5", "Emma Schneider")
    private val people = listOf(minh, minhTran, ha, emmaW, emmaS)

    @Test
    fun `only the exact name finds someone again, ignoring case, accents and punctuation`() {
        assertEquals(minh, NameMatcher.find("minh", people))
        assertEquals(minhTran, NameMatcher.find("tran quang minh", people))
        assertEquals(ha, NameMatcher.find("  Phạm  thu-hà ", people))
        assertEquals(emmaW, NameMatcher.find("@Emma Wilson", people))
    }

    @Test
    fun `part of a name is not enough to say who someone is`() {
        assertNull(NameMatcher.find("Emma", people))
        assertNull(NameMatcher.find("Quang", people))
        assertNull(NameMatcher.find("", people))
    }

    @Test
    fun `the most recent person wins when two have the same name`() {
        val newer = Person("person-9", "Minh")

        assertEquals(newer, NameMatcher.find("Minh", listOf(newer) + people))
    }

    @Test
    fun `suggestions follow what has been typed so far, exact names first`() {
        assertEquals(listOf(emmaW, emmaS), NameMatcher.suggest("em", people))
        assertEquals(listOf(minh, minhTran), NameMatcher.suggest("Minh", people))
        assertEquals(listOf(minhTran), NameMatcher.suggest("tran mi", people))
        assertEquals(listOf(ha), NameMatcher.suggest("ha", people))
        assertEquals(people.take(2), NameMatcher.suggest("", people, limit = 2))
    }

    @Test
    fun `normalize strips accents and the Vietnamese d`() {
        assertEquals("do duc huy", NameMatcher.normalize("Đỗ Đức Huy"))
    }
}
