package com.dantech.splitvibe.data.bills

import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.domain.NameMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

/**
 * The bills kept on the phone: drafts saved as the user works and the splits they finished. Each
 * save writes the whole bill as the next revision; see [SavedBill].
 */
class BillRepository(
    private val database: SplitVibeDatabase,
    private val people: PeopleRepository,
    private val pages: BillPages,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val bills = database.bills()

    /** Bills matching [query] (all of them when it is blank), newest first. */
    fun listings(query: String = ""): Flow<List<BillListing>> = bills.listings(likePattern(query))

    suspend fun get(id: String): SavedBill? = bills.get(id)?.let { storedJson.decodeFromString(SavedBill.serializer(), it.document) }

    /**
     * The finished bills, the latest saved first, for the statistics and exports. A bill this
     * version cannot read, such as one a newer version saved, is left out.
     */
    fun finished(): Flow<List<SavedBill>> = bills.finishedDocuments()
        .map { documents ->
            documents.mapNotNull { document ->
                try {
                    storedJson.decodeFromString(SavedBill.serializer(), document)
                } catch (e: SerializationException) {
                    null
                }
            }
        }
        .flowOn(Dispatchers.Default)

    /**
     * Saves [bill] as the revision after the one it was read as, with its shares worked out again,
     * and returns what was saved. The people on a finished split are remembered for next time; a
     * name on a draft may still be a typo.
     *
     * @throws BillConflictException when the stored bill is no longer the revision [bill] was read as.
     */
    suspend fun save(bill: SavedBill): SavedBill {
        val previous = if (bill.revision > 0) get(bill.id) else null
        val worked = bill.copy(revision = bill.revision + 1, updatedAt = clock()).withSharesWorkedOut()
        val next = if (worked.status == BillStatus.FINAL) {
            // People added from a link typed their own names, which are no suggestions for later bills.
            val strangers = worked.share?.added?.values.orEmpty().toSet()
            val remembered = people.remember(worked.plan.participants.filter { it.id !in strangers })
            val participants = worked.plan.participants.map { participant ->
                remembered.firstOrNull { it.id == participant.id }
                    ?: participant
            }
            worked.copy(plan = worked.plan.copy(participants = participants))
        } else {
            worked
        }
        if (!bills.write(next.toEntity(), expectedRevision = bill.revision)) throw BillConflictException(bill.id)
        // A bill read again from new photos no longer needs the old ones.
        previous?.let { pages.delete(it.pages - next.pages.toSet()) }
        return next
    }

    /**
     * Saves [bill] as a new bill, unless a bill with its id is kept already, and returns what was
     * saved, or null when one was. The check and the write are one transaction, so of two writers
     * that create the same bill at once exactly one does.
     */
    suspend fun createIfAbsent(bill: SavedBill): SavedBill? = try {
        save(bill.copy(revision = 0))
    } catch (e: BillConflictException) {
        null
    }

    /** Deletes the bill and the photos of its pages. */
    suspend fun delete(id: String) {
        val stored = get(id)
        bills.delete(id)
        stored?.pages?.let { pages.delete(it) }
    }

    private fun SavedBill.toEntity(): BillEntity {
        val names = plan.participants.map { if (it.isMe) YOU else it.name }
        return BillEntity(
            id = id,
            revision = revision,
            status = status.name,
            updatedAt = updatedAt,
            merchant = bill.merchant,
            date = bill.date,
            currency = bill.currency.code,
            fractionDigits = bill.currency.fractionDigits,
            totalMinor = bill.totalMinor,
            people = names.joinToString(", "),
            searchText = NameMatcher.normalize(
                listOfNotNull(bill.merchant, bill.date, instruction).plus(bill.items.map { it.name }).plus(names).joinToString(" "),
            ),
            document = storedJson.encodeToString(SavedBill.serializer(), this),
            category = bill.category.wireValue,
        )
    }

    private fun likePattern(query: String): String = NameMatcher.normalize(query)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
