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
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.requests.RequestRepository
import com.dantech.splitvibe.data.requests.RequestStatus
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.GroupAmounts
import com.dantech.splitvibe.domain.GroupConversion
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.ui.common.ProxyErrors
import com.dantech.splitvibe.ui.split.positiveRate
import java.util.UUID
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

/** Why the group refused a bill that is already in it, and what the user can do about it. */
enum class GroupConflict {
    /** The group's copy was deleted; the bill can only go in again as a new bill. */
    DELETED,

    /** Someone changed the group's copy since this phone last sent it; sending again replaces their change. */
    CHANGED,
}

/** Where adding a bill to a group stands. */
data class AddToGroupState(
    val group: GroupState? = null,
    /** Which member each participant of the bill is; a participant missing here is not matched yet. */
    val memberOf: Map<String, String> = emptyMap(),
    /** The rate from the bill's currency into the group's, when they differ. */
    val rate: ExchangeRate? = null,
    val working: Boolean = false,
    val message: String? = null,
    /** The group refused the bill; the sheet asks what to do. */
    val conflict: GroupConflict? = null,
    /** People were already asked for this bill one by one; paying those does not count in the group. */
    val askedPersonally: Boolean = false,
    /** The bill is in the group, as this says. */
    val done: GroupLink? = null,
)

/**
 * Brings a split bill into a group: picks the group, matches each person on the bill to a member,
 * converts the total once at the day's rate (or one the user types), and sends it. A bill already
 * in the group is sent as its next revision; when the group refused it, the user decides what next.
 */
class AddToGroupViewModel(private val groups: GroupRepository, private val requests: RequestRepository, private val fx: FxRepository) :
    ViewModel() {

    /** The groups the user is a member of. */
    val choices: StateFlow<List<GroupSummary>?> = groups.groups.map { list -> list?.filter { !it.isPending } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableState = MutableStateFlow(AddToGroupState())
    val state: StateFlow<AddToGroupState> = mutableState.asStateFlow()

    /** The bill this sheet was started for, so turning the phone keeps what was chosen. */
    private var startedFor: String? = null

    /** The id the group's copy goes under, and the revision of it the next send replaces. */
    private var billIdInGroup: String? = null
    private var expectedRevision = 0L

    /** Starts with the group the bill is already in, if any; a second call for the same bill changes nothing. */
    fun start(saved: SavedBill) {
        if (startedFor == saved.id) return
        startedFor = saved.id
        mutableState.value = AddToGroupState()
        run {
            groups.refreshGroups()
            val asked = requests.requests.first().orEmpty()
                .any { it.isMine && it.billId == saved.id && it.groupId == null && it.status != RequestStatus.PAID }
            mutableState.update { it.copy(askedPersonally = asked) }
        }
        saved.group?.let { choose(saved, it.groupId) }
    }

    fun choose(saved: SavedBill, groupId: String) = run {
        groups.refresh(groupId)
        val group = checkNotNull(groups.group(groupId).first())
        val rate = if (saved.bill.currency.code == group.currency.code) null else fx.rate(saved.bill.currency.code, group.currency.code)
        val link = saved.group?.takeIf { it.groupId == groupId }
        billIdInGroup = link?.billId ?: saved.id
        expectedRevision = link?.revision ?: 0L
        mutableState.update { it.copy(group = group, memberOf = matched(saved, group), rate = rate, conflict = null) }
    }

    fun match(participantId: String, memberId: String) = mutableState.update {
        it.copy(memberOf = it.memberOf + (participantId to memberId))
    }

    /** Converts at the rate typed as [text], such as the one the user's card was charged at. */
    fun setRate(text: String, saved: SavedBill) {
        val group = mutableState.value.group ?: return
        val rate = positiveRate(text) ?: return
        mutableState.update { it.copy(rate = ExchangeRate(saved.bill.currency.code, group.currency.code, rate)) }
    }

    /** The bill's amounts in the group's currency, or null while something is missing. */
    fun amounts(saved: SavedBill): GroupAmounts? {
        val current = mutableState.value
        val group = current.group ?: return null
        val participants = saved.plan.participants.map { it.id }
        if (!current.memberOf.keys.containsAll(participants)) return null
        val shares = saved.plan.result(saved.bill).shares
        val memberOf = current.memberOf.filterKeys { it in participants }
        val amounts = GroupConversion.amountsOf(saved.bill.totalMinor, shares, saved.plan.payments, memberOf)
        return GroupConversion.convert(amounts, saved.bill.currency, group.currency, current.rate)
    }

    fun submit(saved: SavedBill) = run { send(saved) }

    /** After the group's copy was deleted: the bill goes in again as a new bill, under a new id. */
    fun addAgain(saved: SavedBill) = run {
        billIdInGroup = UUID.randomUUID().toString()
        expectedRevision = 0L
        send(saved)
    }

    /** After someone else changed the group's copy: the user's version takes its place. */
    fun replaceTheirs(saved: SavedBill) = run {
        val group = checkNotNull(mutableState.value.group)
        expectedRevision = group.bills.firstOrNull { it.id == billIdInGroup }?.revision ?: expectedRevision
        send(saved)
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null, conflict = null) }

    private suspend fun send(saved: SavedBill) {
        val current = mutableState.value
        val group = checkNotNull(current.group)
        val amounts = checkNotNull(amounts(saved)) { "The bill is not ready for the group" }
        val memberOf = current.memberOf.filterKeys { id -> saved.plan.participants.any { it.id == id } }
        val rate = current.rate.takeIf { saved.bill.currency.code != group.currency.code }
        val id = billIdInGroup ?: saved.id
        val document = GroupBillDocument(sharedCopyOf(saved), memberOf)
        val answer = try {
            groups.putBill(group.group.id, id, expectedRevision, document, amounts, rate)
        } catch (e: ProxyException) {
            if (e.status != 409) throw e
            // The group's copy is not what this phone last sent: see why, and let the user decide.
            groups.refresh(group.group.id)
            val now = checkNotNull(groups.cached(group.group.id))
            val conflict = if (now.bills.none { it.id == id }) GroupConflict.DELETED else GroupConflict.CHANGED
            mutableState.update { it.copy(group = now, conflict = conflict) }
            return
        }
        val revision = answer.bills.firstOrNull { it.id == id }?.revision ?: (expectedRevision + 1)
        expectedRevision = revision
        val link = GroupLink(answer.group.id, answer.group.name, revision, memberOf, billId = id.takeIf { it != saved.id })
        mutableState.update { it.copy(group = answer, conflict = null, done = link) }
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
         * the text read from them, what was asked of the model, what the model warned about, the head
         * count read from the instruction and the user's own rate stay on the phone.
         */
        fun sharedCopyOf(saved: SavedBill): SavedBill = saved.copy(
            instruction = "",
            summary = "",
            ocrDocument = "",
            pages = emptyList(),
            exchangeRate = null,
            group = null,
            bill = saved.bill.copy(warnings = emptyList()),
            plan = saved.plan.copy(participants = saved.plan.participants.map { it.copy(personId = null) }, expectedHeadCount = null),
        )
    }
}
