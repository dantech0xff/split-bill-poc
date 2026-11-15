package com.dantech.splitvibe.data.groups

import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.domain.Balances
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.DebtSimplifier
import com.dantech.splitvibe.domain.GroupAmounts
import com.dantech.splitvibe.domain.Transfer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// The shapes of the proxy's answers about groups, as `docs/proxy.md` describes them.

@Serializable
data class CurrencyDto(val code: String, val fractionDigits: Int) {
    fun toBillCurrency(): BillCurrency = BillCurrency(code, fractionDigits)

    companion object {
        fun of(currency: BillCurrency) = CurrencyDto(currency.code, currency.fractionDigits)
    }
}

/** A group in the list of the caller's groups. */
@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val currency: CurrencyDto,
    val revision: Long,
    /** Null while the caller only waits for the owner to let them in. */
    val myMemberId: String? = null,
    val status: String,
) {
    val isPending: Boolean get() = status == "pending"
}

@Serializable
data class GroupInfo(
    val id: String,
    val name: String,
    val currency: CurrencyDto,
    val ownerMemberId: String,
    val revision: Long,
    val createdAt: Long,
)

/** Someone in a group: a person with the app, or a place kept under a name for someone without it. */
@Serializable
data class MemberDto(val id: String, val name: String, val hasAccount: Boolean, val isOwner: Boolean, val joinedAt: Long)

/** Someone who asked to be a member that was kept under a name; only the owner sees these. */
@Serializable
data class ClaimDto(val memberId: String, val claimantName: String, val requestedAt: Long)

/** The rate a bill was brought into the group's currency at; the proxy keeps the rate as written. */
@Serializable
data class ConversionDto(val rate: String, val date: String? = null, val source: String? = null)

@Serializable
data class GroupBillDto(
    val id: String,
    val ownerMemberId: String,
    val revision: Long,
    val updatedAt: Long,
    /** The bill as the member who added it wrote it, a [GroupBillDocument]; any shape at all from the proxy's point of view. */
    val document: JsonObject = JsonObject(emptyMap()),
    val conversion: ConversionDto? = null,
    val totalMinor: Long,
    val shares: Map<String, Long>,
    val paid: Map<String, Long>,
) {
    val amounts: GroupAmounts get() = GroupAmounts(totalMinor, shares, paid)
}

/** Money one member paid another back, as the member who got it recorded it. */
@Serializable
data class SettlementDto(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val recordedByMemberId: String,
    val requestId: String? = null,
    val createdAt: Long,
)

@Serializable
data class LogEntryDto(val at: Long, val kind: String, val memberId: String? = null, val subjectId: String? = null)

/** Everything a member sees of a group. */
@Serializable
data class GroupState(
    val group: GroupInfo,
    val myMemberId: String,
    val members: List<MemberDto>,
    val claims: List<ClaimDto> = emptyList(),
    val bills: List<GroupBillDto> = emptyList(),
    val settlements: List<SettlementDto> = emptyList(),
    val log: List<LogEntryDto> = emptyList(),
) {
    val currency: BillCurrency get() = group.currency.toBillCurrency()
    val isOwner: Boolean get() = myMemberId == group.ownerMemberId

    fun member(id: String?): MemberDto? = members.firstOrNull { it.id == id }

    fun nameOf(memberId: String?): String = when (memberId) {
        null -> "Someone"
        myMemberId -> "You"
        else -> member(memberId)?.name ?: "A former member"
    }

    /**
     * The bills that count: those whose parts and payments add up and name only members. One that
     * does not, which only a broken or tampered copy can be, is left out whole, so the balances still
     * add up to zero.
     */
    val countedBills: List<GroupBillDto> get() = members.map { it.id }.toSet().let { ids -> bills.filter { it.amounts.isValidFor(ids) } }

    /** What each member is owed (above zero) or owes (below zero), in the group's currency; null when the numbers are beyond adding up. */
    val balances: Map<String, Long>? by lazy {
        val paidBack = settlements.filter { it.amountMinor > 0 }.map { Transfer(it.fromMemberId, it.toMemberId, it.amountMinor) }
        runCatching { Balances.of(members.map { it.id }, countedBills.map { it.amounts }, paidBack) }.getOrNull()
    }

    /** The fewest transfers that settle the group. */
    val suggestedTransfers: List<Transfer> by lazy { balances?.let(DebtSimplifier::transfers).orEmpty() }
}

/** What an invite link shows before the person joins. */
@Serializable
data class Invitation(val group: InvitedGroup, val placeholders: List<Placeholder> = emptyList(), val alreadyMember: Boolean = false) {
    @Serializable
    data class InvitedGroup(val id: String, val name: String, val currency: CurrencyDto)

    @Serializable
    data class Placeholder(val id: String, val name: String)
}

/** A link that lets people join a group, until it runs out or the owner takes it back. */
@Serializable
data class Invite(val token: String, val url: String, val expiresAt: Long)

/**
 * A bill as it is kept in a group: the bill and its split as the member who added it saw them, and
 * which member each participant is. The photos, the text read from them and what was asked of the
 * model stay on that member's phone.
 */
@Serializable
data class GroupBillDocument(val saved: SavedBill, val memberOf: Map<String, String>)
