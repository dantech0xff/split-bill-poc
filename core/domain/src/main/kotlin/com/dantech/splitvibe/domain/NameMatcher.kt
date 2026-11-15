package com.dantech.splitvibe.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Finds the people the user has split bills with before by the names they type, on the device.
 *
 * Only an exact name, ignoring case, accents and punctuation, identifies someone again: "Minh" is
 * the remembered "Minh", but not "Trần Quang Minh", who may well be someone else. Partial matches
 * are only offered as suggestions while the user types.
 */
object NameMatcher {

    /** The remembered person called exactly [name], the first of them if several are; [people] come most recent first. */
    fun find(name: String, people: List<Person>): Person? {
        val wanted = tokens(name)
        if (wanted.isEmpty()) return null
        return people.firstOrNull { tokens(it.name) == wanted }
    }

    /**
     * People whose name fits what the user has typed so far: every word typed starts one of the
     * words of their name. Exact names come first, then the order of [people].
     */
    fun suggest(typed: String, people: List<Person>, limit: Int = 5): List<Person> {
        val wanted = tokens(typed)
        if (wanted.isEmpty()) return people.take(limit)
        return people
            .filter { person ->
                val name = tokens(person.name)
                wanted.all { word -> name.any { it.startsWith(word) } }
            }
            .sortedByDescending { tokens(it.name) == wanted }
            .take(limit)
    }

    private fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /** Lower-cases, removes diacritics (so "Hà" matches "ha") and turns punctuation into spaces. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim().removePrefix("@"), Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
    }

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{Nd}]+")
}
