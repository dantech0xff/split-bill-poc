package com.dantech.splitvibe.recurring

import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.Recurrence
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Makes the drafts of the recurring bills that are due: one for every occurrence up to today that
 * none was made for yet, so also the occurrences missed while the phone was off or the app was not
 * opened. A paused recurring bill makes none.
 *
 * Each draft has the id [Recurrence.draftId] gives its occurrence and is only written when no bill
 * has that id, in one transaction with the check that its recurring bill still makes it and with the
 * mark that it did ([RecurringBillRepository.makeDraft]). A run that is repeated, or two runs at
 * once, therefore make each draft once, and only the run that made a draft tells the user of it,
 * through [notify]: once for all the drafts a recurring bill made in the run, earliest first, however
 * many periods it caught up. A run is never stopped half way, such as when the user leaves the app
 * during it, as a draft it made would then never get its notice.
 */
class RecurringDrafts(
    private val recurring: RecurringBillRepository,
    private val bills: BillRepository,
    private val notify: (RecurringBill, List<SavedBill>) -> Unit,
    private val clock: Clock,
) {
    /** Makes every draft that is due and returns the ones this run made, earliest first for each recurring bill. */
    suspend fun makeDue(): List<SavedBill> = withContext(NonCancellable) {
        val today = LocalDate.now(clock)
        val made = mutableListOf<SavedBill>()
        for (template in recurring.active()) {
            val drafts = template.due(today).mapNotNull { day ->
                recurring.makeDraft(template.id, day) { bills.createIfAbsent(template.draft(day, clock.millis())) }
            }
            if (drafts.isEmpty()) continue
            made += drafts
            try {
                notify(template, drafts)
            } catch (e: RuntimeException) {
                // Some phones' notice services throw. The drafts are made, and so are the next
                // recurring bill's: only this notice is missing, and the drafts are in the bills.
            }
        }
        made
    }

    /** Whether a recurring bill is there to make drafts, which the daily work is only needed for. */
    suspend fun anyActive(): Boolean = recurring.active().isNotEmpty()
}
