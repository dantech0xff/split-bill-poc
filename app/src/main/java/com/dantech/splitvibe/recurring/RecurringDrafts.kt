package com.dantech.splitvibe.recurring

import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.Recurrence
import java.time.Clock
import java.time.LocalDate

/**
 * Makes the drafts of the recurring bills that are due: one for every occurrence up to today that
 * none was made for yet, so also the occurrences missed while the phone was off or the app was not
 * opened. A paused recurring bill makes none.
 *
 * Each draft has the id [Recurrence.draftId] gives its occurrence and is only written when no bill
 * has that id, in one transaction. A run that is repeated, or two runs at once, therefore make each
 * draft once, and only the run that made a draft tells the user of it, through [notify]: once for
 * all the drafts a recurring bill made in the run, earliest first, however many periods it caught up.
 */
class RecurringDrafts(
    private val recurring: RecurringBillRepository,
    private val bills: BillRepository,
    private val notify: (RecurringBill, List<SavedBill>) -> Unit,
    private val clock: Clock,
) {
    /** Makes every draft that is due and returns the ones this run made, earliest first for each recurring bill. */
    suspend fun makeDue(): List<SavedBill> {
        val today = LocalDate.now(clock)
        val made = mutableListOf<SavedBill>()
        for (template in recurring.active()) {
            val due = template.due(today)
            val drafts = due.mapNotNull { day -> bills.createIfAbsent(template.draft(day, clock.millis())) }
            due.lastOrNull()?.let { recurring.madeThrough(template.id, it) }
            if (drafts.isEmpty()) continue
            made += drafts
            try {
                notify(template, drafts)
            } catch (e: RuntimeException) {
                // Some phones' notice services throw. The drafts are made, and so are the next
                // recurring bill's: only this notice is missing, and the drafts are in the bills.
            }
        }
        return made
    }

    /** Whether a recurring bill is there to make drafts, which the daily work is only needed for. */
    suspend fun anyActive(): Boolean = recurring.active().isNotEmpty()
}
