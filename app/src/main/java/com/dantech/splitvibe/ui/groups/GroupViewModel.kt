package com.dantech.splitvibe.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.requests.NewRequest
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.data.requests.requestIdFor
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.Transfer
import com.dantech.splitvibe.domain.payments.PaymentRequest
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.ui.common.ProxyErrors
import com.dantech.splitvibe.ui.common.ProxyWork
import com.dantech.splitvibe.ui.requests.RequestMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something to hand to the share sheet: a text, and a payment code to send with it when there is one. */
data class ShareText(val text: String, val paying: PaymentRequest? = null)

/**
 * One group: its balances and the transfers that settle it, its members and bills, and what the
 * user may change in it. The proxy checks every change; the screen shows the group as the proxy
 * answered.
 */
class GroupViewModel(
    private val groupId: String,
    private val repository: GroupRepository,
    private val requests: RequestRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    val group: StateFlow<GroupState?> = repository.group(groupId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val work = MutableStateFlow(ProxyWork())
    val status: StateFlow<ProxyWork> = work.asStateFlow()

    /** What to share next, such as an invite link; the screen hands it to the share sheet once. */
    private val toShare = MutableStateFlow<ShareText?>(null)
    val share: StateFlow<ShareText?> = toShare.asStateFlow()

    /** Set once the user left the group, so the screen closes. */
    private val gone = MutableStateFlow(false)
    val left: StateFlow<Boolean> = gone.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = run { repository.refresh(groupId) }

    fun invite() = run {
        val state = group.value
        val invite = repository.invite(groupId)
        val name = state?.group?.name ?: "our group"
        toShare.value = ShareText("Join \"$name\" on Split Vibe, where we keep our shared bills: ${invite.url}")
    }

    fun revokeInvites() = run { repository.revokeInvites(groupId) }

    fun rename(name: String) = run { repository.rename(groupId, name) }

    fun addPlaceholder(name: String) = run { repository.addPlaceholder(groupId, name) }

    fun renameMember(memberId: String, name: String) = run { repository.renameMember(groupId, memberId, name) }

    /** The owner takes [memberId] out of the group; they stay as a kept place with what they paid and owe. */
    fun removeMember(memberId: String) = run { repository.removeMember(groupId, memberId) }

    fun approve(memberId: String) = run { repository.approveClaim(groupId, memberId) }

    fun reject(memberId: String) = run { repository.rejectClaim(groupId, memberId) }

    fun deleteBill(billId: String) = run { repository.deleteBill(groupId, billId) }

    /**
     * The user's open requests in this group, by the member they ask: once one of them is paid, it is
     * confirmed rather than recorded a second time by hand.
     */
    val openRequests: StateFlow<Map<String, RequestView>> = requests.requests
        .map { list ->
            list.orEmpty()
                .filter { it.isMine && it.groupId == groupId && it.toMemberId != null && it.status != RequestStatus.PAID }
                .associateBy { checkNotNull(it.toMemberId) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Records that [transfer] reached the user, under [settlementId], made once when the user chose
     * to record it, so a retry or a second tap records it only once. When the user had asked that
     * member for money in this group, that request is confirmed instead, which records the payment.
     */
    fun markReceived(transfer: Transfer, settlementId: String) = run {
        val open = openRequests.value[transfer.fromMemberId]
        if (open != null) {
            requests.confirmPaid(open.id)
            repository.refresh(groupId)
        } else {
            repository.recordPaidBack(groupId, transfer.fromMemberId, transfer.amountMinor, settlementId)
        }
    }

    /**
     * Asks the member of [transfer] for it with the user's main payment method. The request is a
     * group request: once the user confirms the money arrived, the group records it as paid back.
     */
    fun askFor(transfer: Transfer) = run {
        val state = checkNotNull(group.value)
        val method = paymentMethods.all.first().firstOrNull()?.method
        val payer = state.member(transfer.fromMemberId)?.name ?: "there"
        val myName = settings.value.myName.ifBlank { state.member(state.myMemberId)?.name.orEmpty() }
        val created = method?.let {
            val request = NewRequest(
                // The same debt asked for again is the same request; once the amount owed has changed,
                // a new one takes the place of the old one.
                id = requestIdFor(
                    "group/$groupId/${transfer.fromMemberId}/${transfer.toMemberId}",
                    transfer.amountMinor,
                    state.currency,
                    it,
                ),
                amountMinor = transfer.amountMinor,
                currency = state.currency,
                payerName = payer,
                creditorName = myName.ifBlank { "Your friend" },
                title = state.group.name,
                method = it,
                groupId = groupId,
                toMemberId = transfer.fromMemberId,
            )
            requests.create(request).also { made ->
                requests.withdrawReplaced(made.request) { old -> old.groupId == groupId && old.toMemberId == transfer.fromMemberId }
            }
        }
        val paying = method?.let { PaymentRequestBuilder.build(it, transfer.amountMinor, state.currency, state.group.name) }
        toShare.value = ShareText(
            RequestMessages.request(
                name = payer,
                amountMinor = transfer.amountMinor,
                currency = state.currency,
                title = state.group.name,
                breakdown = null,
                method = method,
                paying = paying,
                link = created?.url,
            ),
            paying,
        )
    }

    /** Leaves the group: what the user paid and owes stays in it under their name. */
    fun leave() = run {
        repository.leave(groupId)
        gone.value = true
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
