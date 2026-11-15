package com.dantech.splitvibe.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.groups.GroupSummary
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.common.ProxyErrors
import com.dantech.splitvibe.ui.common.ProxyWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The user's groups, and making a new one. */
class GroupsViewModel(
    private val repository: GroupRepository,
    account: StateFlow<Account>,
    private val settings: StateFlow<AppSettings>,
    private val saveName: (String) -> Unit,
) : ViewModel() {

    val groups: StateFlow<List<GroupSummary>?> = repository.groups.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Groups need a sign-in: they are shared with other people through the proxy. */
    val signedIn: StateFlow<Boolean> = account.map { it.session != null }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        account.value.session != null,
    )

    private val work = MutableStateFlow(ProxyWork())
    val status: StateFlow<ProxyWork> = work.asStateFlow()

    val myName: String get() = settings.value.myName

    /** The currency a new group counts in unless the user picks another. */
    val localCurrency: String get() = settings.value.localCurrency

    /** The group just made, which the screen opens once. */
    private val made = MutableStateFlow<String?>(null)
    val created: StateFlow<String?> = made.asStateFlow()

    init {
        // Also once someone signs in with the screen already open.
        signedIn.filter { it }.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        if (!signedIn.value) return
        run { repository.refreshGroups() }
    }

    fun create(name: String, currencyCode: String, myName: String) {
        val currency = BillCurrency.resolve(currencyCode, null, emptyList())
        run {
            saveName(myName)
            made.value = repository.create(name, currency, myName).group.id
        }
    }

    fun openedCreated() {
        made.value = null
    }

    fun dismissMessage() = work.update { it.copy(message = null) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            work.update { it.copy(working = true) }
            try {
                block()
                work.update { ProxyWork() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                work.update { ProxyWork(message = ProxyErrors.messageOf(e)) }
            }
        }
    }
}
