package com.dantech.splitvibe.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The bills kept on the phone, newest first, narrowed to those matching what the user searches for. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val bills: BillRepository) : ViewModel() {

    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    /** Null until the first list has been read, so an empty list means there are no bills. */
    val listings: StateFlow<List<BillListing>?> = mutableQuery
        .flatMapLatest { bills.listings(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    fun onQueryChanged(text: String) {
        mutableQuery.value = text.take(MAX_QUERY_CHARS)
    }

    /** Deletes the bill and the photos of its pages. */
    fun delete(id: String) {
        viewModelScope.launch { bills.delete(id) }
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
        const val MAX_QUERY_CHARS = 80
    }
}
