package com.dantech.splitvibe.ui.claim

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.domain.SplitPlan
import kotlinx.coroutines.flow.StateFlow

/**
 * The bill open on the split screen, as the screen of its link works with it. The split screen keeps
 * the only copy that is written, so the link's screen changes the bill through it.
 */
interface BillOnScreen {
    /** The bill as it is on screen, or null when none is open. */
    val billOnScreen: StateFlow<SavedBill?>

    /** Saves whatever change waits to be saved, and returns the bill as it is stored, with its revision. */
    suspend fun storedBill(): SavedBill?

    /** Remembers [link] as the link of the bill on screen, or forgets its link with null. */
    fun linkToShare(link: ShareLink?)

    /**
     * Changes what the bill on screen keeps of its link [shareId], as [transform] says, on the copy
     * the split screen holds now; nothing when the bill has another link or none.
     */
    fun updateLink(shareId: String, transform: (ShareLink) -> ShareLink)

    /** Splits the bill on screen as [plan] says, which is what people picked on its link. */
    fun applyPlan(plan: SplitPlan)

    /** Marks the split as finished. */
    fun finish()
}
