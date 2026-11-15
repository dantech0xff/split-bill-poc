package com.dantech.splitvibe.domain

import kotlinx.serialization.Serializable

/** Id of the app's own user among a bill's participants. */
const val ME_ID = "me"

/**
 * Someone taking part in one bill: sharing it, paying for it, or both. [id] belongs to the bill
 * ([ME_ID] for the app's user, then "p1", "p2", and so on); every split, payment and share refers to
 * it. [personId] links the participant to a remembered [Person], which only serves suggestions.
 * The app's user has no stored name; the screen calls them "You".
 */
@Serializable
data class Participant(val id: String, val name: String, val personId: String? = null) {
    val isMe: Boolean get() = id == ME_ID

    companion object {
        val ME = Participant(ME_ID, "")

        /** The next free id after [existing], such as "p3" after "p1" and "p2". */
        fun nextId(existing: Collection<Participant>): String {
            val highest = existing.mapNotNull { if (it.id.startsWith("p")) it.id.drop(1).toIntOrNull() else null }.maxOrNull() ?: 0
            return "p${highest + 1}"
        }
    }
}

/**
 * Someone the user has split a bill with before, remembered on the phone to suggest their name
 * again. A person never decides an amount: bills refer only to their own [Participant]s.
 */
@Serializable
data class Person(val id: String, val name: String)
