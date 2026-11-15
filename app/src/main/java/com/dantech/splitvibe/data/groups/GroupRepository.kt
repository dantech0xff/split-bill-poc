package com.dantech.splitvibe.data.groups

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.GroupAmounts
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * The user's groups, kept on the proxy and checked there: who may read a group, who may change a
 * bill, who may approve a claim. The phone keeps the last answers so groups can be read without a
 * connection; every change goes to the proxy first and shows once it has been accepted.
 */
class GroupRepository(private val api: ProxyApi, private val cache: ProxyCache, private val account: AccountStore) {

    /** The groups as the proxy last listed them, or null before they were ever fetched. */
    val groups: Flow<List<GroupSummary>?> = cache.watch(GROUPS, GROUP_LIST)

    fun group(id: String): Flow<GroupState?> = cache.watch(groupKey(id), GroupState.serializer())

    suspend fun refreshGroups() {
        val body = call("GET", "v1/groups") ?: return
        val groups = ProxyCache.json.decodeFromJsonElement(GroupList.serializer(), body).groups
        cache.put(GROUPS, GROUP_LIST, groups)
    }

    /** Fetches [id] again. A group the caller can no longer read is forgotten. */
    suspend fun refresh(id: String) {
        try {
            state(call("GET", "v1/groups/$id"))
        } catch (e: ProxyException) {
            if (e.status == 403 || e.status == 404) {
                cache.remove(groupKey(id))
                refreshGroups()
            }
            throw e
        }
    }

    suspend fun create(name: String, currency: BillCurrency, myName: String): GroupState {
        val body = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("name", name.trim())
            put("currency", ProxyCache.json.encodeToJsonElement(CurrencyDto.of(currency)))
            put("memberId", UUID.randomUUID().toString())
            put("memberName", myName.trim())
        }
        return state(call("POST", "v1/groups", body)).also { refreshGroups() }
    }

    suspend fun rename(id: String, name: String) = state(call("PATCH", "v1/groups/$id", buildJsonObject { put("name", name.trim()) }))

    /** Keeps a place in the group under [name] for someone who does not have the app. */
    suspend fun addPlaceholder(id: String, name: String) = state(
        call("POST", "v1/groups/$id/members", buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("name", name.trim())
        }),
    )

    suspend fun renameMember(id: String, memberId: String, name: String) =
        state(call("PATCH", "v1/groups/$id/members/$memberId", buildJsonObject { put("name", name.trim()) }))

    /** Leaves [id]. What the user paid and owes stays in the group under their name, for the others' balances. */
    suspend fun leave(id: String) {
        call("POST", "v1/groups/$id/leave")
        cache.remove(groupKey(id))
        refreshGroups()
    }

    suspend fun invite(id: String): Invite = ProxyCache.json.decodeFromJsonElement(Invite.serializer(), checkNotNull(call("POST", "v1/groups/$id/invites")))

    /** Takes back every invite link of [id], so nobody else can use one to join. */
    suspend fun revokeInvites(id: String) {
        call("DELETE", "v1/groups/$id/invites")
    }

    suspend fun invitation(token: String): Invitation =
        ProxyCache.json.decodeFromJsonElement(Invitation.serializer(), checkNotNull(call("GET", "v1/invites/$token")))

    /** Joins the group of [token] as a new member called [name]. */
    suspend fun join(token: String, name: String): GroupState {
        val body = buildJsonObject {
            put("memberId", UUID.randomUUID().toString())
            put("name", name.trim())
        }
        return state(call("POST", "v1/invites/$token/join", body)).also { refreshGroups() }
    }

    /** Asks to be [memberId], a place kept under a name. The group's owner decides; until then the group only shows as waiting. */
    suspend fun claim(token: String, memberId: String, name: String) {
        val body = buildJsonObject {
            put("claimMemberId", memberId)
            put("name", name.trim())
        }
        call("POST", "v1/invites/$token/join", body)
        refreshGroups()
    }

    suspend fun approveClaim(id: String, memberId: String) = state(call("POST", "v1/groups/$id/claims/$memberId/approve"))

    suspend fun rejectClaim(id: String, memberId: String) = state(call("POST", "v1/groups/$id/claims/$memberId/reject"))

    /**
     * Writes a bill of the group: new with [expectedRevision] 0, else in place of revision
     * [expectedRevision]. [amounts] are in the group's currency, [rate] the one they were converted at
     * when the bill is in another currency.
     */
    suspend fun putBill(
        groupId: String,
        billId: String,
        expectedRevision: Long,
        document: GroupBillDocument,
        amounts: GroupAmounts,
        rate: ExchangeRate?,
    ): GroupState {
        val body = buildJsonObject {
            put("expectedRevision", expectedRevision)
            put("document", ProxyCache.json.encodeToJsonElement(GroupBillDocument.serializer(), document))
            put("conversion", rate?.let { ProxyCache.json.encodeToJsonElement(ConversionDto(it.rate.toPlainString(), it.date, it.source)) } ?: JsonNull)
            put("totalMinor", amounts.totalMinor)
            put("shares", ProxyCache.json.encodeToJsonElement(amounts.shares))
            put("paid", ProxyCache.json.encodeToJsonElement(amounts.paid))
        }
        return state(call("PUT", "v1/groups/$groupId/bills/$billId", body))
    }

    suspend fun deleteBill(groupId: String, billId: String) = state(call("DELETE", "v1/groups/$groupId/bills/$billId"))

    /** Records that [fromMemberId] paid the user [amountMinor] back. Only the member who got the money records it, and it cannot be undone. */
    suspend fun recordPaidBack(groupId: String, fromMemberId: String, amountMinor: Long): GroupState {
        val body = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("fromMemberId", fromMemberId)
            put("amountMinor", amountMinor)
        }
        return state(call("POST", "v1/groups/$groupId/settlements", body))
    }

    /** Keeps the group state a change answered with, so the screen shows it at once. */
    private suspend fun state(body: JsonObject?): GroupState {
        val state = ProxyCache.json.decodeFromJsonElement(GroupState.serializer(), checkNotNull(body) { "The proxy sent no group" })
        cache.put(groupKey(state.group.id), GroupState.serializer(), state)
        return state
    }

    /** A sign-in the proxy no longer accepts shows in Settings as one to renew. */
    private suspend fun call(method: String, path: String, body: JsonElement? = null): JsonObject? = try {
        api.signedIn(method, path, body)
    } catch (e: ProxyException) {
        if (e.code == ProxyApi.SESSION_REFUSED) account.expireSession()
        throw e
    }

    @Serializable
    private data class GroupList(val groups: List<GroupSummary>)

    private companion object {
        const val GROUPS = "groups"
        val GROUP_LIST: KSerializer<List<GroupSummary>> = ListSerializer(GroupSummary.serializer())

        fun groupKey(id: String) = "group:$id"
    }
}
