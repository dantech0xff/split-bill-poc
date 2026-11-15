package com.dantech.splitvibe.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.ui.common.ProxyErrors
import com.dantech.splitvibe.ui.common.ProxyWork
import com.dantech.splitvibe.ui.groups.ShareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The payment requests the user sent and those sent to them. The payer says when they paid; only the
 * requester confirms it, and only then does it count, also in a group.
 */
class RequestsViewModel(private val repository: RequestRepository, account: StateFlow<Account>) : ViewModel() {

    val requests: StateFlow<List<RequestView>?> = repository.requests.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
        refresh()
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
    val accepted: Boolean = false,
    val working: Boolean = false,
    val message: String? = null,
)

/** Opens a request link: shows what is asked and how to pay, and takes the request on so the payer can say they paid. */
class RequestLinkViewModel(private val token: String, private val repository: RequestRepository, private val account: StateFlow<Account>) :
    ViewModel() {

    private val mutableState = MutableStateFlow(RequestLinkState())
    val state: StateFlow<RequestLinkState> = mutableState.asStateFlow()

    val signedIn: Boolean get() = account.value.session != null

    init {
        if (signedIn) run { mutableState.update { it.copy(request = repository.preview(token)) } }
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
        const val TAKEN = "Someone else already took this request on. Ask the person who sent it for a new link."
    }
}
