package com.dantech.splitvibe.data.requests

import com.dantech.splitvibe.data.groups.CurrencyDto
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A payment request as the proxy shows it to its requester ("creditor") or to the person asked to pay ("debtor"). */
@Serializable
data class RequestView(
    val id: String,
    val role: String,
    val amountMinor: Long,
    val currency: CurrencyDto,
    /** The name the requester gave the person asked to pay. */
    val payerName: String,
    /** The name the requester wants the payer to see. */
    val creditorName: String? = null,
    /** What the request is for, such as the merchant of the bill. */
    val title: String? = null,
    /** The one payment method the request was sent with; empty once its requester deleted their account. */
    val method: JsonObject = JsonObject(emptyMap()),
    val groupId: String? = null,
    val toMemberId: String? = null,
    val billId: String? = null,
    val createdAt: Long,
    val reportedPaidAt: Long? = null,
    val confirmedPaidAt: Long? = null,
    val remindEveryDays: Int = 0,
    /** Someone with the app took the request on, so they can say they paid and get reminders. */
    val debtorBound: Boolean = false,
) {
    val isMine: Boolean get() = role == CREDITOR
    val bill: BillCurrency get() = currency.toBillCurrency()

    /** The method, or null when there is none or this version of the app cannot read it. */
    val paymentMethod: PaymentMethod? get() = runCatching {
        ProxyCache.json.decodeFromJsonElement(PaymentMethod.serializer(), method)
    }.getOrNull()

    val status: RequestStatus get() = when {
        confirmedPaidAt != null -> RequestStatus.PAID
        reportedPaidAt != null -> RequestStatus.REPORTED_PAID
        else -> RequestStatus.WAITING
    }

    companion object {
        const val CREDITOR = "creditor"
        const val DEBTOR = "debtor"
        val REMINDER_DAYS = listOf(0, 1, 3, 7)
    }
}

enum class RequestStatus {
    /** Nobody has said it was paid yet. */
    WAITING,

    /** The payer said they paid; it counts once the requester confirms. */
    REPORTED_PAID,

    /** The requester confirmed the money arrived. */
    PAID,
}

/** A request the user just made, with the link that lets its payer take it on in the app. */
@Serializable
data class CreatedRequest(val request: RequestView, val url: String)
