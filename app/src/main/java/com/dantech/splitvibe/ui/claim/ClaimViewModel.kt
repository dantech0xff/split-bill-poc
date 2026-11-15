package com.dantech.splitvibe.ui.claim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.payments.PaymentPayload
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.BillProjection
import com.dantech.splitvibe.data.share.LiveShare
import com.dantech.splitvibe.data.share.OwnedShare
import com.dantech.splitvibe.data.share.ShareAmount
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.domain.ClaimSplit
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.ui.common.ProxyErrors
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the bill's link stands, as its screen shows it. */
data class ClaimState(
    /** The link as the proxy last told it, or null before it was made or read. */
    val share: ShareView? = null,
    val shareId: String? = null,
    val url: String? = null,
    /** Who each claim is on the bill: a participant's id, or [NEW_PERSON] to add them as someone new. */
    val matches: Map<String, String> = emptyMap(),
    val working: Boolean = false,
    val message: String? = null,
    /** Links need a sign-in, so that only the person who made one can take it down. */
    val needsSignIn: Boolean = false,
    /** The bill has no lines, or more lines or people than a page shows. */
    val cannotShare: Boolean = false,
    /** The link was taken down; the screen closes. */
    val closed: Boolean = false,
) {
    companion object {
        const val NEW_PERSON = "new"
    }
}

/**
 * The screen of a bill's link: makes the link, follows what people pick on it as they pick, and
 * turns their picks into the split in two steps. Locking stops picking and reads every claim made
 * before it; sending puts what each person owes on the page, once the user has checked the split.
 */
class ClaimViewModel(
    private val host: BillOnScreen,
    private val shares: ShareRepository,
    private val paymentMethods: PaymentMethodRepository,
    private val settings: StateFlow<AppSettings>,
    private val account: StateFlow<Account>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val mutableState = MutableStateFlow(ClaimState())
    val state: StateFlow<ClaimState> = mutableState.asStateFlow()

    /** The bill whose link this is, as the split screen shows it. */
    val bill: StateFlow<SavedBill?> = host.billOnScreen

    private var live: Job? = null

    init {
        start()
    }

    /** Makes the bill's link, or reads the one it has. */
    fun start() {
        val saved = host.billOnScreen.value ?: return
        if (account.value.session == null) {
            mutableState.update { it.copy(needsSignIn = true) }
            return
        }
        if (!BillProjection.canShare(saved)) {
            mutableState.update { it.copy(cannotShare = true, needsSignIn = false) }
            return
        }
        mutableState.update { it.copy(needsSignIn = false) }
        run {
            val stored = checkNotNull(host.storedBill()) { "The bill is not open any more" }
            val (shareId, owned) = stored.share?.let { link -> existing(link)?.let { link.shareId to it } } ?: create(stored)
            mutableState.update { it.copy(shareId = shareId) }
            keep(owned.share, owned.url)
            listen(owned.url)
        }
    }

    /** Says who [claimId] is on the bill: [participantId], or [ClaimState.NEW_PERSON]. */
    fun match(claimId: String, participantId: String) = mutableState.update { it.copy(matches = it.matches + (claimId to participantId)) }

    /** Takes someone off the link, such as a stranger who got it. */
    fun remove(claimId: String) = run { keep(shares.removeClaim(shareId(), claimId)) }

    /** Stops people from picking, and splits the bill by what each one picked. */
    fun lock() = run {
        val view = shares.lock(shareId())
        keep(view)
        applyPicks(view)
    }

    /** Lets people pick again; the page no longer shows what each person owes. */
    fun reopen() = run { keep(shares.reopen(shareId())) }

    /** Puts what each person owes, and how to pay the user, on the page, and finishes the split. */
    fun send() = run {
        val stored = checkNotNull(host.storedBill()) { "The bill is not open any more" }
        val result = stored.plan.result(stored.bill)
        if (!result.isComplete) {
            mutableState.update { it.copy(message = "Finish the split first. ${result.problems.first()}") }
            return@run
        }
        val method = paymentMethods.all.first().firstOrNull()?.method
        val amounts = stored.plan.participants.associate { participant ->
            val amount = result.shares.firstOrNull { it.personId == participant.id }?.totalMinor ?: 0L
            val payment = method?.takeIf { !participant.isMe && amount > 0 }
                ?.let { PaymentPayload.of(PaymentRequestBuilder.build(it, amount, stored.bill.currency, stored.bill.merchant)) }
            participant.id to ShareAmount(amount, payment)
        }
        keep(shares.finalize(stored, settings.value.myName, amounts))
        host.finish()
    }

    /** Takes the link down: its page says it no longer works, and what people picked is deleted. */
    fun revoke() = run {
        shares.revoke(shareId())
        live?.cancel()
        host.linkToShare(null)
        mutableState.update { it.copy(closed = true) }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    private suspend fun existing(link: ShareLink): OwnedShare? = try {
        shares.get(link.shareId)
    } catch (e: ProxyException) {
        // A link that expired or was taken down is replaced by a new one.
        if (e.status != HTTP_NOT_FOUND && e.status != HTTP_GONE) throw e
        null
    }

    private suspend fun create(stored: SavedBill): Pair<String, OwnedShare> {
        val id = newId()
        val owned = shares.publish(id, stored, settings.value.myName)
        host.linkToShare(ShareLink(id, owned.url))
        return id to owned
    }

    private fun listen(url: String) {
        live?.cancel()
        live = viewModelScope.launch {
            shares.live(url).collect { event ->
                when (event) {
                    is LiveShare.Changed -> keep(event.share)

                    LiveShare.Gone -> {
                        host.linkToShare(null)
                        mutableState.update { it.copy(closed = true) }
                    }
                }
            }
        }
    }

    /** Keeps [share] unless a newer view of the link came first, and suggests who each new claim is. */
    private fun keep(share: ShareView, url: String? = null) = mutableState.update { state ->
        val current = state.share
        val newest = if (current != null && current.revision > share.revision) current else share
        state.copy(share = newest, url = url ?: state.url, matches = suggested(newest, state.matches))
    }

    /** Each claim not matched yet goes to the participant whose name it is, else to someone new. */
    private fun suggested(share: ShareView, matches: Map<String, String>): Map<String, String> {
        val saved = host.billOnScreen.value ?: return matches
        val myName = settings.value.myName
        val people = saved.plan.participants.map { Person(it.id, if (it.isMe) myName else it.name) }.filter { it.name.isNotBlank() }
        return share.claims.associate { claim ->
            claim.id to (matches[claim.id] ?: NameMatcher.find(claim.name, people)?.id ?: ClaimState.NEW_PERSON)
        }
    }

    /** Splits the bill on screen by the claims of [view], adding the people who are new to it. */
    private fun applyPicks(view: ShareView) {
        val saved = host.billOnScreen.value ?: return
        val matches = mutableState.value.matches
        var plan = saved.plan
        val picks = view.claims.map { claim ->
            val known = matches[claim.id]?.takeIf { it in plan.participantIds }
            val participantId = known ?: plan.withParticipantAdded(claim.name).also { plan = it }.participants.last().id
            mutableState.update { it.copy(matches = it.matches + (claim.id to participantId)) }
            ClaimSplit.Pick(participantId, claim.itemIds.toSet())
        }
        host.applyPlan(plan.copy(setup = ClaimSplit.setup(saved.bill, picks, plan.setup.notSharing)))
    }

    private fun shareId(): String = checkNotNull(mutableState.value.shareId) { "The bill has no link" }

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
        const val HTTP_NOT_FOUND = 404
        const val HTTP_GONE = 410
    }
}
