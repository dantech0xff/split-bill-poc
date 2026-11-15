package com.dantech.splitvibe.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.groups.GroupBillDocument
import com.dantech.splitvibe.data.groups.GroupRepository
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.GroupSummary
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.GroupAmounts
import com.dantech.splitvibe.domain.GroupConversion
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.ui.common.ProxyErrors
import java.math.BigDecimal
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

/** Where adding a bill to a group stands. */
data class AddToGroupState(
    val group: GroupState? = null,
    /** Which member each participant of the bill is; a participant missing here is not matched yet. */
    val memberOf: Map<String, String> = emptyMap(),
    /** The rate from the bill's currency into the group's, when they differ. */
    val rate: ExchangeRate? = null,
    val working: Boolean = false,
    val message: String? = null,
    /** The bill is in the group, as this says. */
    val done: GroupLink? = null,
)

/**
 * Brings a split bill into a group: picks the group, matches each person on the bill to a member,
 * converts the total once at the day's rate (or one the user types), and sends it. A bill already
 * in the group is sent as its next revision.
 */
class AddToGroupViewModel(private val groups: GroupRepository, private val fx: FxRepository) : ViewModel() {

    /** The groups the user is a member of. */
    val choices: StateFlow<List<GroupSummary>?> = groups.groups.map { list -> list?.filter { !it.isPending } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableState = MutableStateFlow(AddToGroupState())
    val state: StateFlow<AddToGroupState> = mutableState.asStateFlow()

    /** Starts with the group the bill is already in, if any. */
    fun start(saved: SavedBill) {
        mutableState.value = AddToGroupState()
        run { groups.refreshGroups() }
        saved.group?.let { choose(saved, it.groupId) }
    }

    fun choose(saved: SavedBill, groupId: String) = run {
        groups.refresh(groupId)
        val group = checkNotNull(groups.group(groupId).first())
        val rate = if (saved.bill.currency.code == group.currency.code) null else fx.rate(saved.bill.currency.code, group.currency.code)
        mutableState.update { it.copy(group = group, memberOf = matched(saved, group), rate = rate) }
    }

    fun match(participantId: String, memberId: String) = mutableState.update { it.copy(memberOf = it.memberOf + (participantId to memberId)) }

    /** Converts at [rate], such as the one the user's card was charged at. */
    fun setRate(rate: BigDecimal, saved: SavedBill) {
        val group = mutableState.value.group ?: return
        if (rate.signum() <= 0) return
        mutableState.update { it.copy(rate = ExchangeRate(saved.bill.currency.code, group.currency.code, rate)) }
    }

    /** The bill's amounts in the group's currency, or null while something is missing. */
    fun amounts(saved: SavedBill): GroupAmounts? {
        val current = mutableState.value
        val group = current.group ?: return null
        val participants = saved.plan.participants.map { it.id }
        if (!current.memberOf.keys.containsAll(participants)) return null
        val shares = saved.plan.result(saved.bill).shares
        val amounts = GroupConversion.amountsOf(saved.bill.totalMinor, shares, saved.plan.payments, current.memberOf.filterKeys { it in participants })
        return GroupConversion.convert(amounts, saved.bill.currency, group.currency, current.rate)
    }

    fun submit(saved: SavedBill) = run {
        val current = mutableState.value
        val group = checkNotNull(current.group)
        val amounts = checkNotNull(amounts(saved)) { "The bill is not ready for the group" }
        val memberOf = current.memberOf.filterKeys { id -> saved.plan.participants.any { it.id == id } }
        // Sent again as the next revision when it is already in this group.
        val expected = saved.group?.takeIf { it.groupId == group.group.id }?.revision ?: 0L
        val rate = current.rate.takeIf { saved.bill.currency.code != group.currency.code }
        val answer = groups.putBill(group.group.id, saved.id, expected, GroupBillDocument(sharedCopyOf(saved), memberOf), amounts, rate)
        val revision = answer.bills.firstOrNull { it.id == saved.id }?.revision ?: (expected + 1)
        mutableState.update { it.copy(group = answer, done = GroupLink(answer.group.id, answer.group.name, revision, memberOf)) }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(working = true, message = null) }
            try {
                block()
                mutableState.update { it.copy(working = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(working = false, message = ProxyErrors.messageOf(e)) }
            }
        }
    }

    private companion object {
        /** The user is their own member; everyone else is matched by name where a member is called the same. */
        fun matched(saved: SavedBill, group: GroupState): Map<String, String> {
            val earlier = saved.group?.takeIf { it.groupId == group.group.id }?.memberOf.orEmpty()
            val others = group.members.filter { it.id != group.myMemberId }.map { Person(it.id, it.name) }
            return saved.plan.participants.mapNotNull { participant ->
                val member = earlier[participant.id]?.takeIf { id -> group.members.any { it.id == id } }
                    ?: if (participant.isMe) group.myMemberId else NameMatcher.find(participant.name, others)?.id
                member?.let { participant.id to it }
            }.toMap()
        }

        /**
         * What the group gets to see of the bill: what was bought and how it was split. The photos,
         * the text read from them, what was asked of the model and the user's own rate stay on the phone.
         */
        fun sharedCopyOf(saved: SavedBill): SavedBill = saved.copy(
            instruction = "",
            summary = "",
            ocrDocument = "",
            pages = emptyList(),
            exchangeRate = null,
            group = null,
            plan = saved.plan.copy(participants = saved.plan.participants.map { it.copy(personId = null) }),
        )
    }
}
