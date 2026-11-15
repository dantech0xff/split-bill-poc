package com.dantech.splitvibe.data.people

import com.dantech.splitvibe.data.bills.PersonEntity
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Person
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The people the user has split bills with, most recent first, so their names can be suggested
 * again. Only names are kept, never a phone number or anything from the phone's contacts.
 */
class PeopleRepository(database: SplitVibeDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = database.people()

    val people: Flow<List<Person>> = dao.recent(LIMIT).map { rows -> rows.map { Person(it.id, it.name) } }

    /**
     * Remembers everyone on a bill but the app's user, and returns the participants linked to the
     * people they are, which is how a name typed again finds the same person.
     */
    suspend fun remember(participants: List<Participant>): List<Participant> = participants.map { participant ->
        val name = participant.name.trim()
        val normalized = NameMatcher.normalize(name)
        if (participant.isMe || normalized.isEmpty()) return@map participant
        val known = dao.byName(normalized)
        val person =
            PersonEntity(id = known?.id ?: UUID.randomUUID().toString(), name = name, normalizedName = normalized, lastUsedAt = clock())
        dao.upsert(person)
        participant.copy(personId = person.id)
    }

    suspend fun forget(personId: String) = dao.delete(personId)

    private companion object {
        const val LIMIT = 50
    }
}
