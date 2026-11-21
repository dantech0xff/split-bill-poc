package com.dantech.splitvibe.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.common.ProxyErrors
import com.dantech.splitvibe.ui.common.ProxyWork
import com.dantech.splitvibe.ui.groups.ShareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The payment requests the user sent and those sent to them. The payer says when they paid; only the
 * requester confirms it, and only then does it count, also in a group.
 */
class RequestsViewModel(private val repository: RequestRepository, private val groups: GroupRepository, account: StateFlow<Account>) :
    ViewModel() {

    val requests: StateFlow<List<RequestView>?> = repository.requests.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Who asked, by request id, for requests made in a group: the requester's name as the group
     * knows them, which only they can change, rather than the name they typed on the request.
     */
    val creditorNames: StateFlow<Map<String, String>> = repository.requests
        .map { list -> groups.creditorNamesOf(list.orEmpty()) }
        // Naming them reads and decodes the groups the phone keeps, after every change to what it
        // keeps: no work for the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val signedIn: StateFlow<Boolean> = account.map { it.session != null }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        account.value.session != null,
    )

    private val work = MutableStateFlow(ProxyWork())
    val status: StateFlow<ProxyWork> = work.asStateFlow()

    private val toShare = MutableStateFlow<ShareText?>(null)
    val share: StateFlow<ShareText?> = toShare.asStateFlow()

    init {
        // Also once someone signs in with the screen already open.
        signedIn.filter { it }.onEach { refresh() }.launchIn(viewModelScope)
    }

    fun refresh() {
        if (signedIn.value) run { repository.refresh() }
    }

    fun reportPaid(id: String) = run { repository.reportPaid(id) }

    fun confirmPaid(id: String) = run { repository.confirmPaid(id) }

    fun setReminders(id: String, everyDays: Int) = run { repository.setReminders(id, everyDays) }

    fun delete(id: String) = run { repository.delete(id) }

    /** A reminder to send by hand, for a payer without the app or one who switched notices off. */
    fun remind(request: RequestView) = run {
        val paying = request.paymentMethod?.let { PaymentRequestBuilder.build(it, request.amountMinor, request.bill, request.title) }
        val text = RequestMessages.reminder(
            request.payerName,
            request.amountMinor,
            request.bill,
            request.title,
            paying,
            repository.linkOf(request.id),
        )
        toShare.value = ShareText(text, paying)
    }

    fun shared() {
        toShare.value = null
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

/** Where opening a request link stands. */
data class RequestLinkState(
    val request: RequestView? = null,
    /** Who asked, as their group knows them, for a request made in a group the user has open. */
    val creditorName: String? = null,
    val accepted: Boolean = false,
    val working: Boolean = false,
    val message: UiText? = null,
)

/** Opens a request link: shows what is asked and how to pay, and takes the request on so the payer can say they paid. */
class RequestLinkViewModel(
    private val token: String,
    private val repository: RequestRepository,
    private val groups: GroupRepository,
    private val account: StateFlow<Account>,
) : ViewModel() {

    private val mutableState = MutableStateFlow(RequestLinkState())
    val state: StateFlow<RequestLinkState> = mutableState.asStateFlow()

    /** Request links need a sign-in; one opened before it loads as soon as someone signs in. */
    val signedIn: StateFlow<Boolean> = account.map { it.session != null }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        account.value.session != null,
    )

    init {
        signedIn.filter { it }.onEach { load() }.launchIn(viewModelScope)
    }

    fun load() = run {
        val request = repository.preview(token)
        mutableState.update { it.copy(request = request, creditorName = groups.creditorNameOf(request)) }
    }

    fun accept() = run { mutableState.update { it.copy(request = repository.accept(token), accepted = true) } }

    fun reportPaid() = run {
        val id = checkNotNull(mutableState.value.request).id
        if (!mutableState.value.accepted) repository.accept(token)
        mutableState.update { it.copy(request = repository.reportPaid(id), accepted = true) }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(working = true, message = null) }
            try {
                block()
                mutableState.update { it.copy(working = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A link someone else already took on answers 409, and shows them nothing of the request.
                val message = if ((e as? ProxyException)?.status == 409) TAKEN else ProxyErrors.messageOf(e)
                mutableState.update { it.copy(working = false, message = message) }
            }
        }
    }

    private companion object {
        val TAKEN = text(R.string.requests_taken)
    }
}
