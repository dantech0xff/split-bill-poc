package com.dantech.splitvibe.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.requests.RequestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The [RequestTotals] of the whole app, for the Requests tab of the navigation bar and for the screens
 * that show what the requests add up to. One lives as long as the activity, so every screen reads the
 * same totals.
 *
 * Every sign-in empties the requests kept on the phone, and only the requests screen fetched them
 * before, so this fetches them when it starts with someone signed in, which is when the app opens,
 * and again after every sign-in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestOverviewViewModel(private val repository: RequestRepository, groups: GroupRepository, account: StateFlow<Account>) :
    ViewModel() {

    /** A new session is a new sign-in, or the one kept from before when the app opens. */
    private val session = account.map { it.session }.distinctUntilChanged()

    /**
     * Read afresh for each session: what the phone keeps is read again once a sign-in emptied it, so
     * the totals of a new account never show the requests another account left there. The requester
     * of a request made in a group is named as the group knows them, as the list of requests names
     * them.
     */
    val totals: StateFlow<RequestTotals> = session
        .flatMapLatest { current ->
            if (current == null) {
                flowOf(RequestTotals.SignedOut)
            } else {
                repository.requests.map { list ->
                    RequestTotals.of(signedIn = true, requests = list, creditorNames = groups.creditorNamesOf(list.orEmpty()))
                }
            }
        }
        // Naming the requesters reads the groups the phone keeps, after every change to what it keeps:
        // no work for the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, RequestTotals.of(account.value.session != null, requests = null))

    init {
        // Only for the latest session: a fetch still waiting for an account that has left is let go.
        viewModelScope.launch { session.collectLatest { current -> if (current != null) fetch() } }
    }

    /**
     * Fetches the requests, quietly: without a connection, or when the proxy fails, the totals stay
     * what the phone kept, or unknown, and nothing is said. The requests screen fetches them again
     * when it opens, and says there what went wrong.
     */
    private suspend fun fetch() {
        try {
            repository.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to say here; see above.
        }
    }
}
