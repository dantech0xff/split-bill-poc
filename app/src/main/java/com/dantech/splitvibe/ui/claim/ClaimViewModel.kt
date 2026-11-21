package com.dantech.splitvibe.ui.claim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.bills.ShareLink.Companion.NEW_PERSON
import com.dantech.splitvibe.data.payments.PaymentMethodRepository
import com.dantech.splitvibe.data.payments.PaymentPayload
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.data.requests.fingerprintOf
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.BillProjection
import com.dantech.splitvibe.data.share.LiveShare
import com.dantech.splitvibe.data.share.OwnedShare
import com.dantech.splitvibe.data.share.ShareAmount
import com.dantech.splitvibe.data.share.ShareClaim
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.domain.ClaimSplit
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.NameMatcher
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.payments.PaymentRequestBuilder
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.text.sentences
import com.dantech.splitvibe.text.text
import com.dantech.splitvibe.ui.common.ProxyErrors
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Where the bill's link stands, as its screen shows it. */
data class ClaimState(
    /** The link as the proxy last told it, or null before it was made or read. */
    val share: ShareView? = null,
    val shareId: String? = null,
    val url: String? = null,
    /** Who each claim is on the bill: a participant's id, or [NEW_PERSON] to add them as someone new. */
    val matches: Map<String, String> = emptyMap(),
    val working: Boolean = false,
    val message: UiText? = null,
    /** Links need a sign-in, so that only the person who made one can take it down. */
    val needsSignIn: Boolean = false,
    /** The bill has no lines, or more lines or people than a page shows. */
    val cannotShare: Boolean = false,
    /** The bill's link was made while another account was signed in, or no longer exists, so this account cannot change it. */
    val otherAccount: Boolean = false,
    /** The live connection is open. While it is not, picks show once it is again. */
    val connected: Boolean = false,
    /** The link was taken down, or the bill is no longer open: the screen closes. */
    val closed: Boolean = false,
)

/**
 * The screen of a bill's link: makes the link, follows what people pick on it as they pick, and
 * turns their picks into the split in two steps. Locking stops picking and reads every claim made
 * before it; sending puts what each person owes on the page, once the user has checked the split.
 *
 * The bill itself stays with the split screen, which writes it; this screen changes it only
 * through [host], and only while the bill it opened with is the one open there.
 */
class ClaimViewModel(
    private val host: BillOnScreen,
    private val shares: ShareRepository,
    paymentMethods: PaymentMethodRepository,
    private val settings: StateFlow<AppSettings>,
    private val account: StateFlow<Account>,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    billWaitMs: Long = BILL_WAIT_MS,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ClaimState())
    val state: StateFlow<ClaimState> = mutableState.asStateFlow()

    /** The bill whose link this is, as the split screen shows it. */
    val bill: StateFlow<SavedBill?> = host.billOnScreen

    private val chosenMethodId = MutableStateFlow<String?>(null)

    val methods: StateFlow<List<SavedPaymentMethod>?> = paymentMethods.all.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The method the page asks people to pay the user with: the one picked here, else the main one. */
    val method: StateFlow<SavedPaymentMethod?> = combine(methods, chosenMethodId) { list, id -> chosenOf(list, id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The page asks people to pay with a method other than the one chosen now, such as one corrected
     * since. Until the saved methods are there, there is nothing to compare the page's with, so it has
     * not changed. The method is chosen here from the same list, so that it is never one step behind it.
     */
    val methodChanged: StateFlow<Boolean> = combine(bill, methods, chosenMethodId) { saved, list, id ->
        val link = saved?.share
        list != null && link != null && link.settled && asksForMoney(saved) &&
            link.method != chosenOf(list, id)?.let { fingerprintOf(it.method) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Of [list], the method picked here, [id], else the main one, which the list puts first. */
    private fun chosenOf(list: List<SavedPaymentMethod>?, id: String?): SavedPaymentMethod? =
        list?.firstOrNull { it.id == id } ?: list?.firstOrNull()

    /** The bill this screen opened with; nothing is changed once another bill is open. */
    private var billId: String? = null

    private var live: Job? = null

    /** Whether the screen is showing, which is when the link is followed live. */
    private var showing = false

    init {
        viewModelScope.launch {
            // After the system ends the app, the split screen reads its bill back a moment later.
            val opened = withTimeoutOrNull(billWaitMs) { host.billOnScreen.filterNotNull().first() }
            if (opened == null) {
                mutableState.update { it.copy(closed = true) }
                return@launch
            }
            billId = opened.id
            mutableState.update { it.copy(matches = opened.share?.matches.orEmpty()) }
            // Signing in later, from the card this screen shows, starts it.
            account.map { it.session != null }.distinctUntilChanged().collect { signedIn ->
                if (signedIn) {
                    start()
                } else {
                    stopFollowing()
                    mutableState.update { it.copy(needsSignIn = true) }
                }
            }
        }
    }

    /** Makes the bill's link, or reads the one it has. */
    fun start() {
        val saved = openBill() ?: return
        if (account.value.session == null) {
            mutableState.update { it.copy(needsSignIn = true) }
            return
        }
        if (!BillProjection.canShare(saved)) {
            mutableState.update { it.copy(cannotShare = true, needsSignIn = false) }
            return
        }
        mutableState.update { it.copy(needsSignIn = false, cannotShare = false, otherAccount = false) }
        run {
            val stored = storedBill() ?: return@run
            val link = stored.share
            val owned = if (link == null) create(stored) else existing(link, stored) ?: return@run
            show(owned)
        }
    }

    /** Replaces a link this account cannot change with a new one; the old one keeps working until it ends. */
    fun makeNewLink() = run {
        val stored = storedBill() ?: return@run
        mutableState.update { it.copy(otherAccount = false) }
        show(create(stored))
    }

    /** Follows the link while the screen shows. */
    fun resume() {
        showing = true
        state.value.url?.let(::follow)
    }

    /** Stops following the link while the screen does not show, which saves the phone's battery and data. */
    fun pause() {
        showing = false
        stopFollowing()
    }

    fun chooseMethod(id: String) {
        chosenMethodId.value = id
    }

    /** Says who [claimId] is on the bill: [participantId], or [NEW_PERSON]. */
    fun match(claimId: String, participantId: String) {
        mutableState.update { it.copy(matches = it.matches + (claimId to participantId)) }
        val shareId = state.value.shareId ?: return
        if (openBill() != null) host.updateLink(shareId) { it.copy(matches = it.matches + (claimId to participantId)) }
    }

    /** Takes someone off the link while people pick, such as a stranger who got it. */
    fun remove(claimId: String) {
        if (state.value.share?.isOpen != true) return
        run {
            keep(shares.removeClaim(shareId(), claimId))
            host.updateLink(shareId()) { it.copy(matches = it.matches - claimId) }
        }
    }

    /** Stops people from picking, and splits the bill by what each one picked. */
    fun lock() = run {
        val saved = openBill() ?: return@run
        val claims = state.value.share?.claims.orEmpty()
        if (!fits(planFor(saved, claims))) return@run
        val view = shares.lock(shareId())
        keep(view)
        val planned = planFor(saved, view.claims)
        if (!fits(planned)) return@run
        host.applyPlan(planned.plan)
        host.updateLink(shareId()) { it.copy(added = planned.added, matches = state.value.matches) }
    }

    /** Lets people pick again; the page no longer shows what each person owes. */
    fun reopen() = run { keep(shares.reopen(shareId())) }

    /**
     * Puts what each person owes on the page, and how to pay the user when the user is the one owed,
     * and finishes the split. The bill is marked finished first, so the copy the amounts come from
     * is the last one, and no save after them takes them off the page.
     */
    fun send() = run {
        openBill() ?: return@run
        host.finish()
        val stored = storedBill() ?: return@run
        val problems = stored.plan.problems(stored.bill)
        if (problems.isNotEmpty()) {
            mutableState.update { it.copy(message = sentences(text(R.string.claim_finish_first), UiText.Raw(problems.first()))) }
            return@run
        }
        val split = stored.plan.result(stored.bill).shares
        val chosen = method.value?.method?.takeIf { asksForMoney(stored) }
        val amounts = stored.plan.participants.associate { participant ->
            val amount = split.firstOrNull { it.personId == participant.id }?.totalMinor ?: 0L
            val payment = chosen?.takeIf { !participant.isMe && amount > 0 }
                ?.let { PaymentPayload.of(PaymentRequestBuilder.build(it, amount, stored.bill.currency, stored.bill.merchant)) }
            participant.id to ShareAmount(amount, payment)
        }
        val paidBy = stored.plan.payments.singleOrNull()?.participantId?.takeIf { stored.group == null }
        val myName = settings.value.myName
        keep(shares.finalize(stored, myName, amounts, paidBy, matchesOf(stored)))
        host.updateLink(shareId()) {
            it.copy(shown = shares.pageFingerprint(stored, myName), settled = true, method = chosen?.let(::fingerprintOf))
        }
    }

    /** Takes the link down: its page says it no longer works, and what people picked is deleted. */
    fun revoke() = run {
        shares.revoke(shareId())
        stopFollowing()
        if (openBill() != null) host.linkToShare(null)
        mutableState.update { it.copy(closed = true) }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    /** The bill this screen opened with, while it is still the one open on the split screen. */
    private fun openBill(): SavedBill? {
        val saved = host.billOnScreen.value
        if (saved == null || saved.id != billId) {
            mutableState.update { it.copy(closed = true) }
            return null
        }
        return saved
    }

    private suspend fun storedBill(): SavedBill? = host.storedBill()?.takeIf { it.id == billId }

    /** Who each claim on the link is on [saved], as matched or added when picking closed, for those still on it. */
    private fun matchesOf(saved: SavedBill): Map<String, String> {
        val link = saved.share ?: return emptyMap()
        val onBill = saved.plan.participants.map { it.id }.toSet()
        return mutableState.value.share?.claims.orEmpty().mapNotNull { claim ->
            val participant = link.matches[claim.id]?.takeIf { it != NEW_PERSON } ?: link.added[claim.id]
            participant?.takeIf { it in onBill }?.let { claim.id to it }
        }.toMap()
    }

    /** Shows [owned], whose id [create] or [existing] set, and follows it while the screen shows. */
    private fun show(owned: OwnedShare) {
        mutableState.update { it.copy(url = owned.url) }
        keep(owned.share)
        if (showing) follow(owned.url)
    }

    /**
     * The link the bill has, brought up to date with the bill. A link that ended is replaced; one
     * this account cannot read is left alone, and the user decides.
     */
    private suspend fun existing(link: ShareLink, stored: SavedBill): OwnedShare? {
        val owned = try {
            shares.get(link.shareId)
        } catch (e: ProxyException) {
            val ended = e.status == HTTP_GONE || (e.status == HTTP_NOT_FOUND && link.expiresAt in 1..clock())
            if (ended) return create(stored)
            if (e.status != HTTP_NOT_FOUND) throw e
            mutableState.update { it.copy(otherAccount = true, shareId = link.shareId) }
            return null
        }
        mutableState.update { it.copy(shareId = link.shareId) }
        // Changes made while the link could not be reached go to the page now.
        when (val projected = shares.project(stored, settings.value.myName)) {
            is ShareRepository.Projection.Shown -> host.updateLink(link.shareId) {
                it.copy(shown = projected.link.shown, settled = projected.link.settled, expiresAt = projected.link.expiresAt)
            }

            ShareRepository.Projection.Forget -> return create(stored)

            null -> Unit
        }
        return owned
    }

    private suspend fun create(stored: SavedBill): OwnedShare {
        val (owned, link) = shares.publish(newId(), stored, settings.value.myName)
        host.linkToShare(link.copy(matches = state.value.matches))
        mutableState.update { it.copy(shareId = link.shareId) }
        return owned
    }

    private fun follow(url: String) {
        live?.cancel()
        live = viewModelScope.launch {
            shares.live(url).collect { event ->
                when (event) {
                    LiveShare.Connected -> mutableState.update { it.copy(connected = true) }

                    LiveShare.Lost -> mutableState.update { it.copy(connected = false) }

                    is LiveShare.Changed -> keep(event.share)

                    LiveShare.Gone -> {
                        stopFollowing()
                        val shareId = state.value.shareId
                        if (openBill() != null && shareId != null) {
                            if (host.billOnScreen.value?.share?.shareId == shareId) host.linkToShare(null)
                        }
                        mutableState.update { it.copy(closed = true) }
                    }
                }
            }
        }
    }

    private fun stopFollowing() {
        live?.cancel()
        live = null
        mutableState.update { it.copy(connected = false) }
    }

    /** Keeps [share] unless a newer view of the link came first, and suggests who each new claim is. */
    private fun keep(share: ShareView) = mutableState.update { state ->
        val current = state.share
        val newest = if (current != null && current.revision > share.revision) current else share
        state.copy(share = newest, matches = suggested(newest, state.matches))
    }

    /** Each claim not matched yet goes to the participant whose name it is, else to someone new. */
    private fun suggested(share: ShareView, matches: Map<String, String>): Map<String, String> {
        val saved = host.billOnScreen.value ?: return matches
        val myName = settings.value.myName
        val people = saved.plan.participants.map { Person(it.id, if (it.isMe) myName else it.name) }.filter { it.name.isNotBlank() }
        return share.claims.associate { claim ->
            claim.id to (matches[claim.id] ?: NameMatcher.find(claim.name, people)?.id ?: NEW_PERSON)
        }
    }

    /** The bill on screen split by [claims], with the people they add to it, by the matches the user made. */
    private fun planFor(saved: SavedBill, claims: List<ShareClaim>): Planned =
        planFor(saved, claims, state.value.matches, saved.share?.added.orEmpty())

    private fun fits(planned: Planned): Boolean {
        if (planned.plan.participants.size <= BillProjection.MAX_PEOPLE) return true
        mutableState.update { it.copy(message = TOO_MANY_PEOPLE) }
        return false
    }

    private fun shareId(): String = checkNotNull(state.value.shareId) { "The bill has no link" }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(working = true, message = null) }
            try {
                block()
                mutableState.update { it.copy(working = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(working = false, message = messageOf(e)) }
            }
        }
    }

    /** A split by claims: the plan, and the participant added for each claim of someone new. */
    data class Planned(val plan: SplitPlan, val added: Map<String, String>)

    companion object {
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GONE = 410
        private const val HTTP_UNAVAILABLE = 503
        private const val BILL_WAIT_MS = 10_000L
        val TOO_MANY_PEOPLE = text(R.string.claim_too_many_people)

        /** The page asks people to pay the user only when the user paid the whole bill, and it is in no group, where people settle up. */
        fun asksForMoney(saved: SavedBill): Boolean = saved.plan.payments.singleOrNull()?.participantId == ME_ID && saved.group == null

        /**
         * [saved] split by what [claims] say each person picked. A claim the user matched to someone on
         * the bill counts for that person. A claim of someone new adds them, once: the participant
         * added for it before is used again. Someone added before whose claim is gone, or is now
         * matched to someone else, leaves the bill again, and a claim with nothing picked adds nobody.
         */
        fun planFor(saved: SavedBill, claims: List<ShareClaim>, matches: Map<String, String>, addedBefore: Map<String, String>): Planned {
            var plan = saved.plan
            val stillNew = claims.filter { it.itemIds.isNotEmpty() && (matches[it.id] ?: NEW_PERSON) == NEW_PERSON }.map { it.id }.toSet()
            for ((claimId, participantId) in addedBefore) {
                if (claimId !in stillNew) plan = plan.withParticipantRemoved(participantId, saved.bill)
            }
            val added = mutableMapOf<String, String>()
            val picks = claims.mapNotNull { claim ->
                val items = claim.itemIds.toSet()
                val match = matches[claim.id]?.takeIf { it != NEW_PERSON && it in plan.participantIds }
                when {
                    match != null -> ClaimSplit.Pick(match, items)

                    items.isEmpty() -> null

                    else -> {
                        val known = addedBefore[claim.id]?.takeIf { it in plan.participantIds }
                        val id = known ?: plan.withParticipantAdded(claim.name).also { plan = it }.participants.last().id
                        added[claim.id] = id
                        ClaimSplit.Pick(id, items)
                    }
                }
            }
            return Planned(plan.copy(setup = ClaimSplit.setup(saved.bill, picks, plan.setup.notSharing)), added)
        }

        /** What went wrong, in the words of a bill's link. */
        private fun messageOf(error: Exception): UiText = when {
            error is ProxyException && error.code == ProxyApi.SESSION_REFUSED -> text(R.string.claim_sign_in_in_settings)
            error is ProxyException && error.status == HTTP_UNAVAILABLE -> text(R.string.claim_not_set_up)
            else -> ProxyErrors.messageOf(error)
        }
    }
}
