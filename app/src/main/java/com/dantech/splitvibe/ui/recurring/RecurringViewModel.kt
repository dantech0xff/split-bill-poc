package com.dantech.splitvibe.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A recurring bill as its list shows it, with the day its next draft is made. */
data class RecurringRow(val bill: RecurringBill, val next: LocalDate)

/**
 * The recurring bills: making one from a bill, and pausing, resuming and deleting them. After a
 * recurring bill is made or resumed, [runDrafts] makes at once a draft that is due today.
 */
class RecurringViewModel(
    private val recurring: RecurringBillRepository,
    private val bills: BillRepository,
    private val runDrafts: () -> Unit,
    private val clock: Clock,
) : ViewModel() {

    /** Null until the list has been read. */
    val rows: StateFlow<List<RecurringRow>?> = recurring.all
        .map { list ->
            val today = LocalDate.now(clock)
            list.map { RecurringRow(it, it.next(today)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    /** Makes the bill [billId] come back on [schedule], called [name]; nothing when the bill is gone meanwhile. */
    fun repeat(billId: String, name: String, schedule: RecurrenceSchedule) {
        viewModelScope.launch {
            val saved = bills.get(billId) ?: return@launch
            recurring.create(saved, name, schedule)
            runDrafts()
        }
    }

    fun pause(id: String) {
        viewModelScope.launch { recurring.pause(id) }
    }

    fun resume(id: String) {
        viewModelScope.launch {
            recurring.resume(id)
            runDrafts()
        }
    }

    /** Deletes the recurring bill; the drafts it made stay with the other bills. */
    fun delete(id: String) {
        viewModelScope.launch { recurring.delete(id) }
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
