package com.dantech.splitvibe.data.requests

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.groups.CurrencyDto
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** What a new request is: who is asked for how much, and how they can pay. */
data class NewRequest(
    /** Made by the app from what the request is for, so asking twice for the same thing makes one request. */
    val id: String,
    val amountMinor: Long,
    val currency: BillCurrency,
    val payerName: String,
    val creditorName: String,
    val title: String?,
    val method: PaymentMethod,
    val groupId: String? = null,
    val toMemberId: String? = null,
    val billId: String? = null,
    val remindEveryDays: Int = 0,
)

/**
 * Payment requests kept on the proxy, so that a payer with the app can say they paid, the requester
 * can confirm it, and reminders go out. The app never moves money: it only tracks who said what.
 */
class RequestRepository(private val api: ProxyApi, private val cache: ProxyCache, private val account: AccountStore) {

    /** The user's requests, as the proxy last listed them, newest first; null before they were ever fetched. */
    val requests: Flow<List<RequestView>?> = cache.watch(REQUESTS, REQUEST_LIST)

    suspend fun refresh() {
        val body = call("GET", "v1/requests") ?: return
        cache.put(REQUESTS, REQUEST_LIST, ProxyCache.json.decodeFromJsonElement(RequestList.serializer(), body).requests)
    }

    /**
     * Creates [request] and returns the link to send with it. The proxy keeps only a hash of the
     * link, so the link is kept on the phone too, to send again with a reminder.
     */
    suspend fun create(request: NewRequest): CreatedRequest {
        linkOf(request.id)?.let { url -> cachedRequest(request.id)?.let { return CreatedRequest(it, url) } }
        val body = buildJsonObject {
            put("id", request.id)
            put("amountMinor", request.amountMinor)
            put("currency", ProxyCache.json.encodeToJsonElement(CurrencyDto.of(request.currency)))
            put("payerName", request.payerName.trim().take(NAME_LENGTH))
            put("creditorName", request.creditorName.trim().take(NAME_LENGTH))
            request.title?.trim()?.take(TITLE_LENGTH)?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
            put("method", ProxyCache.json.encodeToJsonElement(PaymentMethod.serializer(), request.method))
            request.groupId?.let { put("groupId", it) }
            request.toMemberId?.let { put("toMemberId", it) }
            request.billId?.let { put("billId", it) }
            put("remindEveryDays", request.remindEveryDays)
        }
        val created = ProxyCache.json.decodeFromJsonElement(CreatedRequest.serializer(), checkNotNull(call("POST", "v1/requests", body)))
        cache.put(linkKey(created.request.id), String.serializer(), created.url)
        keep(created.request)
        return created
    }

    /** The link sent with a request the user made on this phone, to send again with a reminder. */
    suspend fun linkOf(id: String): String? = cache.get(linkKey(id), String.serializer())

    /** What a request link shows before the person takes it on. */
    suspend fun preview(token: String): RequestView =
        ProxyCache.json.decodeFromJsonElement(RequestView.serializer(), checkNotNull(call("GET", "v1/request-links/$token")))

    /** Takes the request of [token] on as its payer, so it shows among the user's requests. */
    suspend fun accept(token: String): RequestView = keep(call("POST", "v1/request-links/$token/accept"))

    /** The payer says they paid. It counts once the requester confirms. */
    suspend fun reportPaid(id: String): RequestView = keep(call("POST", "v1/requests/$id/reported-paid"))

    /** The requester confirms the money arrived; for a group request this also records the payment in the group. */
    suspend fun confirmPaid(id: String): RequestView = keep(call("POST", "v1/requests/$id/confirmed-paid"))

    suspend fun setReminders(id: String, everyDays: Int): RequestView =
        keep(call("PATCH", "v1/requests/$id", buildJsonObject { put("remindEveryDays", everyDays) }))

    /** Deletes a request nobody has confirmed yet. */
    suspend fun delete(id: String) {
        call("DELETE", "v1/requests/$id")
        val left = cache.get(REQUESTS, REQUEST_LIST).orEmpty().filterNot { it.id == id }
        cache.put(REQUESTS, REQUEST_LIST, left)
        cache.remove(linkKey(id))
    }

    private suspend fun cachedRequest(id: String): RequestView? = cache.get(REQUESTS, REQUEST_LIST)?.firstOrNull { it.id == id }

    private suspend fun keep(body: JsonObject?): RequestView =
        keep(ProxyCache.json.decodeFromJsonElement(RequestView.serializer(), checkNotNull(body) { "The proxy sent no request" }))

    /** Puts [request] in the kept list in place of its older copy, newest first. */
    private suspend fun keep(request: RequestView): RequestView {
        val others = cache.get(REQUESTS, REQUEST_LIST).orEmpty().filterNot { it.id == request.id }
        cache.put(REQUESTS, REQUEST_LIST, (others + request).sortedByDescending { it.createdAt })
        return request
    }

    private suspend fun call(method: String, path: String, body: JsonElement? = null): JsonObject? = try {
        api.signedIn(method, path, body)
    } catch (e: ProxyException) {
        if (e.code == ProxyApi.SESSION_REFUSED) account.expireSession()
        throw e
    }

    @Serializable
    private data class RequestList(val requests: List<RequestView>)

    private companion object {
        const val REQUESTS = "requests"
        const val NAME_LENGTH = 60
        const val TITLE_LENGTH = 80
        val REQUEST_LIST = ListSerializer(RequestView.serializer())

        fun linkKey(id: String) = "request-link:$id"
    }
}
