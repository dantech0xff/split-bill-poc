package com.dantech.splitvibe.data.bills

import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.PersonShare
import com.dantech.splitvibe.domain.SplitCalculator
import com.dantech.splitvibe.domain.SplitPlan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    /** This bill with its shares worked out again from its inputs, as they will be saved. */
    fun withSharesWorkedOut(): SavedBill = copy(shares = plan.result(bill).shares, calcVersion = SplitCalculator.VERSION)

    companion object {
        /** Raised when the saved shape changes in a way older copies need converting for. */
        const val SCHEMA_VERSION = 1
    }
}

/** Another writer saved this bill since it was read, so this copy would overwrite their change. */
class BillConflictException(val billId: String) : Exception("The bill $billId was changed elsewhere")
