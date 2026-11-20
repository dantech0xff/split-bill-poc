package com.dantech.splitvibe.data.groups

import com.dantech.splitvibe.data.account.AccountStore
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.requests.RequestView
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

    /**
     * The group whose state the phone kept last, when a refresh or a change of it was answered; null
     * while the phone keeps no group's state. It is read from what the cache keeps, so it goes with
     * the cache on every sign-in, sign-out and account deletion. A group's state goes when the user
     * leaves it, when the proxy no longer lets the user read it as it is fetched, and when the list
     * of groups no longer has it once that list is fetched again, as after the user was taken out of
     * the group on another phone. Until one of those happens, a group the user can no longer read is
     * still the latest group.
     */
    val latestGroup: Flow<GroupState?> = cache.watchLatest(GROUP_KEY_PREFIX, GroupState.serializer())

    /** The group [id] as it was last fetched, or null when it never was. */
    suspend fun cached(id: String): GroupState? = cache.get(groupKey(id), GroupState.serializer())

    /**
     * The requester of [request] as their group knows them, when it is a request made in a group that
     * the phone keeps: the name only they can change, rather than the one they typed on the request,
     * which anyone could. Null for any other request.
     */
    suspend fun creditorNameOf(request: RequestView): String? {
        val groupId = request.groupId ?: return null
        val memberId = request.creditorMemberId ?: return null
        return cached(groupId)?.member(memberId)?.name
    }

    /** [creditorNameOf] each of [requests] that has one, by the id of the request. */
    suspend fun creditorNamesOf(requests: List<RequestView>): Map<String, String> =
        requests.mapNotNull { request -> creditorNameOf(request)?.let { request.id to it } }.toMap()

    /**
     * Fetches the list of groups again. The state of a group that is no longer on it goes, unless the
     * cache was emptied while the list was on its way, as the list itself then is not kept either.
     */
    suspend fun refreshGroups() {
        val answer = call("GET", "v1/groups")
        val groups = ProxyCache.json.decodeFromJsonElement(GroupList.serializer(), checkNotNull(answer.body)).groups
        cache.put(GROUPS, GROUP_LIST, groups, answer.since)
        cache.removeUnder(GROUP_KEY_PREFIX, keep = groups.map { groupKey(it.id) }.toSet(), since = answer.since)
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
        call(
            "POST",
            "v1/groups/$id/members",
            buildJsonObject {
                put("id", UUID.randomUUID().toString())
                put("name", name.trim())
            },
        ),
    )

    suspend fun renameMember(id: String, memberId: String, name: String) =
        state(call("PATCH", "v1/groups/$id/members/$memberId", buildJsonObject { put("name", name.trim()) }))

    /** Leaves [id]. What the user paid and owes stays in the group under their name, for the others' balances. */
    suspend fun leave(id: String) {
        call("POST", "v1/groups/$id/leave")
        cache.remove(groupKey(id))
        refreshGroups()
    }

    suspend fun invite(id: String): Invite =
        ProxyCache.json.decodeFromJsonElement(Invite.serializer(), checkNotNull(call("POST", "v1/groups/$id/invites").body))

    /** Takes back every invite link of [id], so nobody else can use one to join. */
    suspend fun revokeInvites(id: String) {
        call("DELETE", "v1/groups/$id/invites")
    }

    /** What the invite [token] shows. Tokens travel in the body, never in an address, so none is ever logged. */
    suspend fun invitation(token: String): Invitation = ProxyCache.json.decodeFromJsonElement(
        Invitation.serializer(),
        checkNotNull(call("POST", "v1/invites/open", buildJsonObject { put("token", token) }).body),
    )

    /** Joins the group of [token] as a new member called [name]. */
    suspend fun join(token: String, name: String): GroupState {
        val body = buildJsonObject {
            put("token", token)
            put("memberId", UUID.randomUUID().toString())
            put("name", name.trim())
        }
        return state(call("POST", "v1/invites/join", body)).also { refreshGroups() }
    }

    /** Asks to be [memberId], a place kept under a name. The group's owner decides; until then the group only shows as waiting. */
    suspend fun claim(token: String, memberId: String, name: String) {
        val body = buildJsonObject {
            put("token", token)
            put("claimMemberId", memberId)
            put("name", name.trim())
        }
        call("POST", "v1/invites/join", body)
        refreshGroups()
    }

    /**
     * Takes [memberId]'s account out of the group: the owner's way to remove someone. They stay as a
     * place kept under their name, with everything they paid and owe, so nobody's balance changes.
     */
    suspend fun removeMember(id: String, memberId: String) = state(call("POST", "v1/groups/$id/members/$memberId/remove"))

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
            put(
                "conversion",
                rate?.let { ProxyCache.json.encodeToJsonElement(ConversionDto(it.rate.toPlainString(), it.date, it.source)) } ?: JsonNull,
            )
            put("totalMinor", amounts.totalMinor)
            put("shares", ProxyCache.json.encodeToJsonElement(amounts.shares))
            put("paid", ProxyCache.json.encodeToJsonElement(amounts.paid))
        }
        return state(call("PUT", "v1/groups/$groupId/bills/$billId", body))
    }

    suspend fun deleteBill(groupId: String, billId: String) = state(call("DELETE", "v1/groups/$groupId/bills/$billId"))

    /**
     * Records that [fromMemberId] paid the user [amountMinor] back. Only the member who got the money
     * records it, and it cannot be undone, so one intent keeps one [settlementId]: sent again, after
     * a lost answer or a second tap, it records nothing more.
     */
    suspend fun recordPaidBack(groupId: String, fromMemberId: String, amountMinor: Long, settlementId: String): GroupState {
        val body = buildJsonObject {
            put("id", settlementId)
            put("fromMemberId", fromMemberId)
            put("amountMinor", amountMinor)
        }
        return state(call("POST", "v1/groups/$groupId/settlements", body))
    }

    /**
     * Keeps the group state a change answered with, so the screen shows it at once. The phone keeps
     * each bill's name and amounts but not its whole document, so a group of any size stays small.
     */
    private suspend fun state(answer: Answer): GroupState {
        val state = ProxyCache.json.decodeFromJsonElement(GroupState.serializer(), checkNotNull(answer.body) { "The proxy sent no group" })
        cache.put(groupKey(state.group.id), GroupState.serializer(), state.withoutDocuments(), answer.since)
        return state
    }

    /**
     * Calls the proxy, noting the cache's generation first, so an answer that arrives after a sign-out
     * or a sign-in is not kept for whoever is signed in now. A sign-in the proxy no longer accepts
     * shows in Settings as one to renew.
     */
    private suspend fun call(method: String, path: String, body: JsonElement? = null): Answer {
        val since = cache.generation
        return try {
            Answer(api.signedIn(method, path, body), since)
        } catch (e: ProxyException) {
            if (e.code == ProxyApi.SESSION_REFUSED) account.expireSession()
            throw e
        }
    }

    private class Answer(val body: JsonObject?, val since: Long)

    @Serializable
    private data class GroupList(val groups: List<GroupSummary>)

    private companion object {
        const val GROUPS = "groups"
        val GROUP_LIST: KSerializer<List<GroupSummary>> = ListSerializer(GroupSummary.serializer())

        /** What the key of every group's state starts with; the list of groups is kept under [GROUPS], which does not. */
        const val GROUP_KEY_PREFIX = "group:"

        fun groupKey(id: String) = "$GROUP_KEY_PREFIX$id"
    }
}
