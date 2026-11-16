package com.dantech.splitvibe.data.bills

import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.PersonShare
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitPlan
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class BillStatus {
    /** Still being worked on. It is saved after every change, so nothing is lost if the app is closed. */
    @SerialName("draft")
    DRAFT,

    /** The user finished the split. Its saved shares are the ones that count. */
    @SerialName("final")
    FINAL,
}

/**
 * A bill as the phone keeps it: what the user read or typed (the bill, who takes part, how it is
 * split, who paid), the shares worked out from that, and where it came from.
 *
 * It is always written whole, as the revision after the one it was read as, so the inputs and the
 * shares can never disagree and two writers that both started from the same copy cannot both win.
 * The photos of the bill stay in the app's own storage and are never uploaded.
 */
@Serializable
data class SavedBill(
    val id: String,
    /** 0 until the bill is first saved, then one more on every save. */
    val revision: Int = 0,
    val status: BillStatus = BillStatus.DRAFT,
    val createdAt: Long,
    val updatedAt: Long,
    val bill: Bill,
    val plan: SplitPlan,
    /** The shares under [plan], as version [calcVersion] of the calculator worked them out; empty while the split is unfinished. */
    val shares: List<PersonShare> = emptyList(),
    val calcVersion: Int = SplitCalculator.VERSION,
    /** What the user asked for when the bill was read. */
    val instruction: String = "",
    /** The model's one-line summary of what it read. */
    val summary: String = "",
    /** The text sent to the model, shown for transparency; empty for a bill typed in by hand. */
    val ocrDocument: String = "",
    /** File names of the photos of the bill's pages, in the app's own storage. */
    val pages: List<String> = emptyList(),
    /** What the bill's currency is worth in the user's own, to show the bill in both; null when they are the same or no rate is known. */
    val exchangeRate: ExchangeRate? = null,
    /** The group this bill was added to, if any, as the group last accepted it. */
    val group: GroupLink? = null,
    /** The link that lets everyone at the table pick their items in a browser, while it is shared. */
    val share: ShareLink? = null,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    /**
     * This bill with its shares worked out again from its inputs, as they will be saved. A finished
     * bill that was changed into something that no longer adds up is a draft again, so the shares of
     * every finished bill add up to its total and so do its payments.
     */
    fun withSharesWorkedOut(): SavedBill = copy(
        shares = plan.result(bill).shares,
        calcVersion = SplitCalculator.VERSION,
        status = settledStatus(),
    )

    /** [status], unless the bill is finished but has a problem, which makes it a draft again. */
    fun settledStatus(): BillStatus = if (status == BillStatus.FINAL && plan.problems(bill).isNotEmpty()) BillStatus.DRAFT else status

    companion object {
        /** Raised when the saved shape changes in a way older copies need converting for. */
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Where a bill was added to a group: the group, the revision of the group's copy the proxy last
 * accepted, and which member each participant is. A change to the bill after that is sent to the
 * group again as the next revision, and refused when someone else changed the group's copy first.
 */
@Serializable
data class GroupLink(
    val groupId: String,
    val groupName: String,
    val revision: Long,
    val memberOf: Map<String, String>,
    /** The id of the group's copy, which is the bill's own id unless the group's copy was deleted and the bill added again. */
    val billId: String? = null,
)

/**
 * A bill link the proxy keeps for this bill: its id there and the address that people open, and
 * what the phone needs to keep the page right as the bill changes.
 */
@Serializable
data class ShareLink(
    val shareId: String,
    val url: String,
    /** When the link stops working; nothing is sent to it after that. */
    val expiresAt: Long = 0,
    /** When the proxy made it. An answer naming another time is a link made again, which is taken down. */
    val createdAt: Long = 0,
    /** What the page shows of the bill, as a fingerprint of the copy last sent; a save that changes none of it sends nothing. */
    val shown: String? = null,
    /** Whether the page shows what everyone owes, which a change to the bill takes off it again. */
    val settled: Boolean = false,
    /** The fingerprint of the payment method the page asks people to pay with; null when it asks for none. */
    val method: String? = null,
    /** Who each claim is on the bill, as the user said or the app suggested: a participant's id, or [NEW_PERSON]. */
    val matches: Map<String, String> = emptyMap(),
    /** The participants added to the bill for claims of people new to it, by claim id. */
    val added: Map<String, String> = emptyMap(),
) {
    companion object {
        const val NEW_PERSON = "new"
    }
}

/** Another writer saved this bill since it was read, so this copy would overwrite their change. */
class BillConflictException(val billId: String) : Exception("The bill $billId was changed elsewhere")

/**
 * How the phone keeps its bills as JSON, the recurring ones too. Reading one back skips what a newer
 * version added, and reads a choice this version does not know, such as a new category, as its
 * default, as the list of bills already shows it.
 */
internal val storedJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/** The day a bill's [date] names, written yyyy-mm-dd as bills keep it; null when it names none, such as "13/11/2026". */
fun dayOf(date: String?): LocalDate? = date?.let {
    try {
        LocalDate.parse(it.trim())
    } catch (e: DateTimeParseException) {
        null
    }
}
