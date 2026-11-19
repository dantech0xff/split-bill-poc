package com.dantech.splitvibe.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.payments.PaymentPayload
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.data.requests.NewRequest
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.requestIdFor
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.payments.PaymentRequest
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.split.PersonRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What goes to one person: the message, and the payment code to send with it when the method has one. */
data class PreparedRequest(val text: UiText, val paying: PaymentRequest?, val tracked: Boolean)

/**
 * The step after a split where each person who owes something is asked for it, through the share
 * sheet. When the user is signed in, each request is also kept on the proxy, so its payer can say
 * they paid from the app and the user can confirm it.
 */
class RequestMoneyViewModel(
    private val paymentMethods: PaymentMethodRepository,
    private val requests: RequestRepository,
    private val account: StateFlow<Account>,
    private val settings: StateFlow<AppSettings>,
    private val saveName: (String) -> Unit,
) : ViewModel() {

    private val chosenId = MutableStateFlow<String?>(null)
    private val shared = MutableStateFlow<Set<String>>(emptySet())
    private val notice = MutableStateFlow<UiText?>(null)

    val methods: StateFlow<List<SavedPaymentMethod>?> = paymentMethods.all.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The method requests go with: the one picked here, else the main one. */
    val method: StateFlow<SavedPaymentMethod?> = combine(methods, chosenId) { list, id ->
        list?.firstOrNull { it.id == id } ?: list?.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The people a request was shared with in this visit to the step. */
    val sharedWith: StateFlow<Set<String>> = shared.asStateFlow()

    /** Something to say once, such as a request that could not be kept on the server. */
    val message: StateFlow<UiText?> = notice.asStateFlow()

    val signedIn: Boolean get() = account.value.session != null

    val myName: String get() = settings.value.myName

    fun choose(id: String) {
        chosenId.value = id
    }

    fun dismissMessage() {
        notice.value = null
    }

    /**
     * What to share with [person] for [saved]. When the user is signed in, the request is kept on the
     * proxy first and its link goes into the message; when that fails, the message goes without one.
     */
    suspend fun prepare(saved: SavedBill, person: PersonRequest, name: String): PreparedRequest {
        if (name.isNotBlank()) saveName(name)
        val bill = saved.bill
        val chosen = method.value
        val title = listOfNotNull(bill.merchant?.takeIf { it.isNotBlank() }, bill.date).joinToString(", ").ifEmpty { null }
        val paying = chosen?.let { PaymentRequestBuilder.build(it.method, person.amountMinor, bill.currency, bill.merchant) }
        val link = if (signedIn && chosen != null && name.isNotBlank()) track(saved, person, chosen, name, title, paying) else null
        val breakdown = person.share.takeIf { it.itemsMinor != 0L && it.chargesMinor != 0L }?.let {
            text(R.string.requests_message_breakdown, bill.format(it.itemsMinor), bill.format(it.chargesMinor))
        }
        val message = RequestMessages.request(
            name = person.participant.name.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: text(R.string.content_payer),
            amountMinor = person.amountMinor,
            currency = bill.currency,
            title = title,
            breakdown = breakdown,
            method = chosen?.method,
            paying = paying,
            link = link,
        )
        return PreparedRequest(message, paying, tracked = link != null)
    }

    /** Records that the request to [participantId] went out. */
    fun markShared(participantId: String) = shared.update { it + participantId }

    private suspend fun track(
        saved: SavedBill,
        person: PersonRequest,
        chosen: SavedPaymentMethod,
        name: String,
        title: String?,
        paying: PaymentRequest?,
    ): String? {
        val payer = person.participant.name.trim()
        val request = NewRequest(
            // Asking again for the same share with the same method is the same request; a changed
            // amount or a corrected account is a new one, which takes the old one's place.
            id = requestIdFor("bill/${saved.id}/${person.participant.id}", person.amountMinor, saved.bill.currency, chosen.method),
            amountMinor = person.amountMinor,
            currency = saved.bill.currency,
            payerName = payer,
            creditorName = name,
            title = title,
            method = chosen.method,
            billId = saved.id,
            payment = paying?.let(PaymentPayload::of),
        )
        return try {
            val created = requests.create(request)
            requests.withdrawReplaced(created.request) { it.billId == saved.id && it.groupId == null && it.payerName == payer }
            created.url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The message still goes, only without the link to say it was paid.
            notice.value = UNTRACKED
            null
        }
    }

    private companion object {
        val UNTRACKED = text(R.string.requests_untracked)
    }
}
