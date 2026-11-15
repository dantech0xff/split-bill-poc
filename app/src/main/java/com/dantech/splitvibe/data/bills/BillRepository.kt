package com.dantech.splitvibe.data.bills

import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.domain.NameMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

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

    suspend fun get(id: String): SavedBill? = bills.get(id)?.let { json.decodeFromString(SavedBill.serializer(), it.document) }

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
            worked.copy(plan = worked.plan.copy(participants = people.remember(worked.plan.participants)))
        } else {
            worked
        }
        if (!bills.write(next.toEntity(), expectedRevision = bill.revision)) throw BillConflictException(bill.id)
        // A bill read again from new photos no longer needs the old ones.
        previous?.let { pages.delete(it.pages - next.pages.toSet()) }
        return next
    }

    /** Deletes the bill and the photos of its pages. */
    suspend fun delete(id: String) {
        val stored = get(id)
        bills.delete(id)
        stored?.pages?.let { pages.delete(it) }
    }

    private fun SavedBill.toEntity(): BillEntity {
        val names = plan.participants.map { if (it.isMe) "You" else it.name }
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
            document = json.encodeToString(SavedBill.serializer(), this),
        )
    }

    private fun likePattern(query: String): String = NameMatcher.normalize(query)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
