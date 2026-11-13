package com.example.splitbill.domain

import com.example.splitbill.data.contacts.Contact
import java.text.Normalizer
import java.util.Locale

/**
 * Resolves a name the user typed in their instruction ("Minh", "emma w") to one of their contacts.
 *
 * This runs on the device, so the contact list never has to be sent to the model: the model only
 * reports which names appear in the user's own instruction.
 *
 * Because the result decides who is asked for money, a contact is only returned as [Match.Found]
 * when the typed name is that person's full name, handle or given name and nobody else fits as
 * well. A surname or middle name alone ("Nguyễn", "Đức") is a hint, not an identification.
 */
object NameMatcher {

    sealed interface Match {
        data class Found(val contact: Contact) : Match
        /** Not sure enough to choose: the user has to pick one of [candidates]. */
        data class Ambiguous(val candidates: List<Contact>) : Match
        data object None : Match
    }

    fun match(mention: String, contacts: List<Contact>): Match {
        val wanted = tokens(mention)
        if (wanted.isEmpty()) return Match.None

        val scored = contacts
            .map { it to score(wanted, it) }
            .filter { (_, score) -> score > 0 }
        val best = scored.maxOfOrNull { (_, score) -> score } ?: return Match.None
        val winners = scored.filter { (_, score) -> score == best }.map { (contact, _) -> contact }
        return if (best >= GIVEN_NAME && winners.size == 1) Match.Found(winners.single()) else Match.Ambiguous(winners)
    }

    private fun score(wanted: List<String>, contact: Contact): Int {
        val name = tokens(contact.name)
        val givenName = tokens(contact.givenName)
        val handle = normalize(contact.handle).replace(" ", "")
        return when {
            wanted == name || wanted.joinToString("") == handle -> EXACT
            !name.containsAll(wanted) -> 0
            givenName.isNotEmpty() && wanted.containsAll(givenName) -> GIVEN_NAME
            else -> OTHER_NAME_PART
        }
    }

    private fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }

    /** Lower-cases, removes diacritics (so "Hà" matches "ha") and turns punctuation into spaces. */
    internal fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim().removePrefix("@"), Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
    }

    private const val EXACT = 3
    private const val GIVEN_NAME = 2
    private const val OTHER_NAME_PART = 1
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{Nd}]+")
}
