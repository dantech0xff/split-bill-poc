package com.dantech.splitvibe.ui.split

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.bills.BillConflictException
import com.dantech.splitvibe.data.bills.BillPages
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.people.ContactNames
import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.data.share.ShareRepository
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.PersonShare
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.SplitPlanner
import com.dantech.splitvibe.domain.SplitResult
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.MAX_INSTRUCTION_CHARS
import com.dantech.splitvibe.llm.signInHelps
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import com.dantech.splitvibe.scan.DocumentScanner
import com.dantech.splitvibe.ui.claim.BillOnScreen
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the instruction field holds until the user types something else. */
const val DEFAULT_INSTRUCTION = "Split this bill"

/** What the screen calls the app's own user. */
const val ME_LABEL = "You"

enum class Progress { IDLE, LOADING_PHOTO, READING_TEXT, ASKING_CLAUDE }

/** What one person is asked to pay back for a bill: their share of it. */
data class PersonRequest(val participant: Participant, val share: PersonShare) {
    val amountMinor: Long get() = share.totalMinor
}

/** The bill on screen and what it comes to under its plan. */
data class SplitResultState(
    val saved: SavedBill,
    /** The currency the user counts in, such as VND, which the bill is also shown in when it differs. */
    val localCurrency: String = "",
    /**
     * The day's rate from the bill's currency into [localCurrency], when it has been found. It is
     * shown, and saved with the bill only when the user finishes a split that has no rate of its own.
     */
    val dayRate: ExchangeRate? = null,
) {
    val bill: Bill get() = saved.bill
    val plan: SplitPlan get() = saved.plan
    val split: SplitResult = saved.plan.result(saved.bill)

    /** The bill is in another currency than the user's, so it is also shown converted. */
    val needsRate: Boolean = bill.currency.code.isNotEmpty() && localCurrency.isNotEmpty() && bill.currency.code != localCurrency

    /** The rate the bill is shown converted at: the one the user typed in for it, else the day's rate. */
    val exchangeRate: ExchangeRate? = listOfNotNull(saved.exchangeRate, dayRate).firstOrNull {
        needsRate && it.converts(bill, localCurrency)
    }

    private val local: BillCurrency? = if (needsRate) BillCurrency.resolve(localCurrency, null, emptyList()) else null

    /** What keeps the split from being finished: the division, then who paid. */
    val problems: List<String> = saved.plan.problems(saved.bill)

    /** The model's one-line summary of what it read. */
    val summary: String get() = saved.summary

    /** The exact text that was sent to the model, shown for transparency. */
    val ocrDocument: String get() = saved.ocrDocument

    /** [minor] of the bill's currency in the user's, formatted, such as "₫2,535,000"; null without a rate. */
    fun converted(minor: Long): String? {
        val rate = exchangeRate ?: return null
        val to = local ?: return null
        return rate.convert(minor, bill.currency, to)?.let(to::format)
    }

    fun nameOf(participantId: String): String {
        val participant = plan.participant(participantId) ?: return participantId
        return if (participant.isMe) ME_LABEL else participant.name.ifBlank { "Someone" }
    }
}

data class SplitUiState(
    /** The photos of the bill's pages, in order. */
    val pages: List<Bitmap> = emptyList(),
    val instruction: String = DEFAULT_INSTRUCTION,
    val progress: Progress = Progress.IDLE,
    val error: String? = null,
    /** The error is fixed by signing in, which this build can do, so it comes with a way to Settings. */
    val errorOpensSettings: Boolean = false,
    val result: SplitResultState? = null,
    /** The bill is open in the editor. */
    val editing: Boolean = false,
    /** The people asked to pay back, once the user chose to ask them. */
    val sentRequests: List<PersonRequest>? = null,
    /** Something to tell the person once, such as a bill that was changed elsewhere. */
    val notice: String? = null,
    /** The bill was opened from the list of bills, so leaving it goes back there rather than to its photos. */
    val openedFromHistory: Boolean = false,
) {
    val photo: Bitmap? get() = pages.firstOrNull()
    val isBusy: Boolean get() = progress != Progress.IDLE
}

/**
 * Drives the split-bill flow: the photos, on-device text recognition, bill analysis by the model,
 * the user's edits and choice of who shares what. The bill is saved as a draft after every change,
 * so it survives the app being closed, and it stays in the history.
 *
 * Every bill is a bill of its own: reading other photos or typing a bill in by hand starts a new
 * one, and only reading the same photos again replaces the bill they were read as.
 */
@OptIn(FlowPreview::class)
class SplitBillViewModel(
    private val settings: StateFlow<AppSettings>,
    private val account: StateFlow<Account>,
    /** Whether this build can sign in with Google, which is what some errors ask for. */
    private val canSignIn: Boolean,
    private val imageLoader: BillImageLoader,
    private val recognizer: BillTextRecognizer,
    private val analyzer: BillAnalyzer,
    private val bills: BillRepository,
    private val peopleRepository: PeopleRepository,
    private val pageStore: BillPages,
    private val fx: FxRepository,
    private val contactNames: ContactNames,
    private val savedState: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis,
    saveDelayMs: Long = SAVE_DELAY_MS,
    /** Shows the bill again on its link after each change; without it, a bill's link keeps what it showed. */
    private val shares: ShareRepository? = null,
) : ViewModel(),
    BillOnScreen,
    SplitOnScreen {

    /** The people the user split with before, most recent first, to suggest their names. */
    val people: StateFlow<List<Person>> = peopleRepository.people.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val mutableState = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = mutableState.asStateFlow()

    override val billOnScreen: StateFlow<SavedBill?> = state.map { it.result?.saved }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var work: Job? = null

    /** The bill waiting to be saved. A few changes in quick succession are saved once. */
    private val pendingSave = MutableStateFlow<SavedBill?>(null)

    /** One save at a time, so that each starts from the revision the one before it wrote. */
    private val saveLock = Mutex()

    /**
     * The revision of each bill's stored copy that the bill on screen descends from: the one it
     * was read at, or the last one this flow wrote. A save expects it, so it never writes over a
     * copy someone else saved in between.
     */
    private val storedRevisions = ConcurrentHashMap<String, Int>()

    /** The bill this flow works on, kept when the user goes back to change the photo or the instruction. */
    private var billId: String? = null

    /** The photos of the pages of [billId], and whether it was read from them: reading them again then replaces it. */
    private var billPageNames: List<String> = emptyList()
    private var billWasRead = false

    /** The stored photos of the pages on screen; [unsavedPages] belong to no saved bill yet. */
    private var pageNames: List<String> = emptyList()

    @Volatile
    private var unsavedPages: List<String> = emptyList()

    /** The day's rate for the bill on screen, and the lookup that is finding it. */
    private var dayRate: ExchangeRate? = null
    private var rateLookup: Job? = null

    /** Saving failed, which the person has been told; they are told again only after a save works. */
    @Volatile
    private var saveFailing = false

    init {
        viewModelScope.launch {
            pendingSave.debounce(saveDelayMs).filterNotNull().collect { bill ->
                // Taken off the queue first, so it is saved once even if it is also saved at once below.
                if (pendingSave.compareAndSet(bill, null)) {
                    claimPages(bill)
                    persist(bill)
                }
            }
        }
        viewModelScope.launch {
            // A visit to Settings can change the currency the user counts in while a bill is open.
            settings.map { it.localCurrency.trim().uppercase(Locale.ROOT) }.distinctUntilChanged().drop(1).collect {
                mutableState.update { state -> state.copy(result = state.result?.let { resultOf(it.saved) }) }
                lookUpDayRate()
            }
        }
        restore()
    }

    override fun onCleared() {
        // A change made a moment before the screen went away is still saved, and photos that
        // belong to no bill are deleted. The view model's own scope is cancelled by now.
        val scope = CoroutineScope(Dispatchers.IO)
        saveNow(scope)
        val leftover = unsavedPages
        if (leftover.isNotEmpty()) scope.launch { pageStore.delete(leftover) }
    }

    fun startNewSplit() {
        saveNow()
        work?.cancel()
        forgetUnsavedPages()
        billId = null
        billPageNames = emptyList()
        billWasRead = false
        pageNames = emptyList()
        dayRate = null
        listOf(KEY_BILL, KEY_PAGES, KEY_INSTRUCTION, KEY_EDITING, KEY_OPENED, KEY_ON_INPUT).forEach { savedState.remove<Any>(it) }
        mutableState.value = SplitUiState()
    }

    /** Opens a saved bill, then [onOpened] once it is on screen. */
    fun open(id: String, onOpened: () -> Unit = {}) {
        saveNow()
        work?.cancel()
        work = viewModelScope.launch {
            val saved = bills.get(id)
            if (saved == null) {
                mutableState.update { it.copy(notice = "This bill is no longer on this phone.") }
                return@launch
            }
            forgetUnsavedPages()
            storedRevisions[saved.id] = saved.revision
            val pages = saved.pages.mapNotNull { pageStore.load(it) }
            savedState[KEY_OPENED] = true
            mutableState.update { it.copy(openedFromHistory = true, sentRequests = null) }
            showSaved(saved, pages, editing = false)
            onOpened()
        }
    }

    fun onInstructionChanged(text: String) {
        // The AI proxy takes instructions up to MAX_INSTRUCTION_CHARS long, so the field holds no more.
        val instruction = text.take(MAX_INSTRUCTION_CHARS)
        savedState[KEY_INSTRUCTION] = instruction
        mutableState.update { it.copy(instruction = instruction) }
    }

    /** The pages of one bill, in order, from the document scanner, the photo picker, the camera or another app. */
    fun onPagesPicked(uris: List<Uri>) {
        val picked = uris.take(DocumentScanner.MAX_PAGES)
        if (picked.isEmpty()) return
        launchWork(Progress.LOADING_PHOTO) {
            showPages(picked.map { imageLoader.load(it) })
            // The pages are kept as the bill's own copies now.
            imageLoader.discard(picked)
        }
    }

    fun onSamplePicked() = launchWork(Progress.LOADING_PHOTO) { showPages(listOf(imageLoader.loadSample())) }

    /**
     * Starts a new bill typed in by hand, in the currency the app works in. It keeps photos picked
     * for it that belong to no other bill, and is saved once something is typed into it.
     */
    fun startManualEntry() {
        work?.cancel()
        val bill = Bill.empty(BillCurrency.resolve(localCurrency(), null, emptyList()))
        val now = clock()
        val ownPages = pageNames.takeIf { it.isNotEmpty() && unsavedPages.containsAll(it) }.orEmpty()
        val saved = SavedBill(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            bill = bill,
            plan = SplitPlan().withBill(bill),
            pages = ownPages,
        )
        val shown = if (ownPages.isEmpty()) emptyList() else mutableState.value.pages
        showSaved(saved, shown, editing = true)
    }

    fun analyze() {
        val current = mutableState.value
        if (current.pages.isEmpty() || current.isBusy) return
        val appSettings = settings.value
        // When AI is only switched off, the request still goes out: the proxy may have turned it back on.
        if (account.value.ai == AiStatus.NOT_SET_UP) {
            mutableState.update { it.copy(error = AiStatus.NOT_SET_UP.notice) }
            return
        }
        // Reading the photos of a bill again replaces its earlier reading. Other photos are another
        // bill, and so is a bill typed in by hand, which a reading never replaces.
        val rereadId = billId?.takeIf { billWasRead && pageNames.isNotEmpty() && pageNames == billPageNames }
        val pages = pageNames

        launchWork(Progress.READING_TEXT) {
            val read = current.pages.map { recognizer.recognize(it) }.filterNot { it.isEmpty }
            if (read.isEmpty()) {
                showError("No text was found in this photo. Make sure the bill is flat, in focus and well lit.")
                return@launchWork
            }

            mutableState.update { it.copy(progress = Progress.ASKING_CLAUDE) }
            when (val analysis = analyzer.analyze(read, current.instruction, appSettings.localCurrency)) {
                is BillAnalysis.Rejected -> showError(analysis.reason)

                is BillAnalysis.Ready -> {
                    val bill = analysis.bill
                    val now = clock()
                    val saved = SavedBill(
                        id = rereadId ?: UUID.randomUUID().toString(),
                        createdAt = now,
                        updatedAt = now,
                        bill = bill,
                        plan = SplitPlanner.fromHint(analysis.splitHint, bill, people.value),
                        instruction = current.instruction,
                        summary = analysis.message,
                        ocrDocument = OcrLayoutEncoder.encode(read),
                        pages = pages,
                    )
                    showSaved(saved, current.pages, editing = false)
                    pendingSave.value = saved
                }
            }
        }
    }

    /**
     * Stops the work in progress. The screen is released at once: a network call that is already
     * waiting for bytes may take a while to notice the cancellation, and its result is discarded.
     */
    fun cancelWork() {
        work?.cancel()
        work = null
        mutableState.update { it.copy(progress = Progress.IDLE) }
    }

    /** Leaves the result and returns to the photo and instruction, keeping both. The bill stays saved. */
    fun backToInput() {
        savedState.remove<Any>(KEY_EDITING)
        savedState[KEY_ON_INPUT] = true
        mutableState.update { it.copy(result = null, sentRequests = null, editing = false) }
    }

    fun startEditing() {
        savedState[KEY_EDITING] = true
        mutableState.update { it.copy(editing = true) }
    }

    /** Leaves the editor for the split; a bill left empty has nothing to split, so that goes back to the photo instead. */
    fun doneEditing() {
        if (mutableState.value.result?.bill?.isBlank() == true) return backToInput()
        savedState.remove<Any>(KEY_EDITING)
        mutableState.update { it.copy(editing = false) }
    }

    /** Applies one of the [com.dantech.splitvibe.domain.BillEditor] changes; the split follows the edited bill. */
    fun editBill(edit: (Bill) -> Bill) {
        val before = mutableState.value.result?.bill?.currency
        change { saved ->
            val bill = edit(saved.bill)
            // Exact amounts and payments move with the bill's own amounts when its currency changes.
            val plan = if (bill.currency == saved.bill.currency) saved.plan else saved.plan.withCurrency(saved.bill.currency, bill.currency)
            saved.copy(bill = bill, plan = plan.withBill(bill))
        }
        // A bill moved to another currency needs another rate.
        if (mutableState.value.result?.bill?.currency != before) lookUpDayRate()
    }

    fun addPerson(name: String, personId: String? = null) {
        if (name.isBlank()) return
        editPlan { plan, _ -> plan.withParticipantAdded(name, personId) }
    }

    /** Adds the contact picked in the system's contact picker, by name only. */
    fun onContactPicked(contact: Uri) {
        viewModelScope.launch {
            val name = contactNames.nameOf(contact)
            if (name == null) {
                mutableState.update { it.copy(notice = "That contact has no name to add. Type the name instead.") }
            } else {
                addPerson(name)
            }
        }
    }

    /** No longer suggests [person], for example a name that was mistyped. */
    fun forgetPerson(person: Person) {
        viewModelScope.launch { peopleRepository.forget(person.id) }
    }

    fun renamePerson(id: String, name: String) {
        if (name.isBlank()) return
        editPlan { plan, _ -> plan.withParticipantRenamed(id, name) }
    }

    fun removePerson(id: String) = editPlan { plan, bill -> plan.withParticipantRemoved(id, bill) }

    fun toggleMeSharing() = editPlan { plan, _ -> plan.withMeSharingToggled() }

    fun setMode(mode: SplitMode) = editPlan { plan, bill -> plan.withMode(mode, bill) }

    fun setWholeValue(participantId: String, value: Long) = editPlan { plan, _ -> plan.withWholeValue(participantId, value) }

    fun setItemSharers(itemId: String, sharerIds: Set<String>) = editPlan { plan, _ -> plan.withItemSharers(itemId, sharerIds) }

    fun setItemDivision(itemId: String, division: Division?) = editPlan { plan, _ -> plan.withItemDivision(itemId, division) }

    fun setPayments(payments: List<Payment>) = editPlan { plan, _ -> plan.withPayments(payments) }

    /** Converts the bill at a rate the user typed in, such as the one their card was charged at. It is kept with the bill. */
    fun setExchangeRate(rate: BigDecimal) {
        val result = mutableState.value.result ?: return
        if (!result.needsRate || rate.signum() <= 0) return
        change { it.copy(exchangeRate = ExchangeRate(it.bill.currency.code, result.localCurrency, rate)) }
    }

    /** Converts the bill at the day's rate again, in place of one the user typed in. */
    fun useDayRate() {
        val result = mutableState.value.result ?: return
        if (result.saved.exchangeRate != null) change { it.copy(exchangeRate = null) }
        lookUpDayRate(tellWhenMissing = true)
    }

    /**
     * Finishes the split and moves on to asking each person who owes something to pay it back. The
     * requests themselves go out from the next step, one person at a time, through the share sheet.
     */
    fun sendRequests() {
        val result = mutableState.value.result ?: return
        if (result.problems.isNotEmpty()) return
        val requests = result.split.shares.mapNotNull { share ->
            val participant = result.plan.participant(share.personId) ?: return@mapNotNull null
            PersonRequest(participant, share).takeIf { !participant.isMe && it.amountMinor > 0 }
        }
        if (requests.isEmpty()) return
        finish()
        mutableState.update { it.copy(sentRequests = requests) }
    }

    /** Remembers that the bill on screen is now in a group, as [link] says. */
    fun linkToGroup(link: GroupLink) {
        if (mutableState.value.result?.saved?.id == null) return
        change { it.copy(group = link) }
    }

    override fun linkToShare(link: ShareLink?) {
        if (mutableState.value.result?.saved?.id == null) return
        change { it.copy(share = link) }
    }

    override fun updateLink(shareId: String, transform: (ShareLink) -> ShareLink) {
        if (mutableState.value.result?.saved?.share?.shareId != shareId) return
        change { saved -> saved.copy(share = saved.share?.let { if (it.shareId == shareId) transform(it) else it }) }
    }

    override fun applyPlan(plan: SplitPlan) = change { it.copy(plan = plan) }

    /**
     * What the assistant applies, or the bill and split from before it, which undoing it brings back
     * exactly, finished or not. A bill is finished again only when the split it gets back has no
     * problem, as [change] sees to.
     */
    override fun replaceSplit(bill: Bill, plan: SplitPlan, status: BillStatus) =
        change { it.copy(bill = bill, plan = plan, status = status) }

    override suspend fun storedBill(): SavedBill? {
        val id = mutableState.value.result?.saved?.id ?: return null
        val pending = pendingSave.value
        if (pending != null && pendingSave.compareAndSet(pending, null)) {
            claimPages(pending)
            persist(pending)
        }
        return saveLock.withLock { bills.get(id) }
    }

    /**
     * Marks the split as finished; it stays in the history and can still be opened and changed. A
     * bill in another currency without a rate of its own keeps the day's rate it is shown at, so it
     * counts in what the user spent in their own currency. A rate the user typed in stays, and a bill
     * finished while no day's rate is known keeps none.
     */
    override fun finish() {
        val result = mutableState.value.result ?: return
        if (result.problems.isNotEmpty()) return
        // Without a rate of the bill's own, the rate shown is the day's, when it converts the bill into the user's currency.
        val shown = result.exchangeRate
        change { it.copy(status = BillStatus.FINAL, exchangeRate = it.exchangeRate ?: shown) }
    }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    private fun editPlan(edit: (SplitPlan, Bill) -> SplitPlan) = change { it.copy(plan = edit(it.plan, it.bill)) }

    private fun change(transform: (SavedBill) -> SavedBill) {
        val result = mutableState.value.result ?: return
        // A finished bill that a change leaves with a problem is a draft again.
        val next = transform(result.saved).let { it.copy(status = it.settledStatus()) }
        if (next == result.saved) return
        mutableState.update { it.copy(result = resultOf(next)) }
        pendingSave.value = next
    }

    private fun showSaved(saved: SavedBill, pages: List<Bitmap>, editing: Boolean) {
        if (billId != saved.id) dayRate = null
        billId = saved.id
        billPageNames = saved.pages
        billWasRead = saved.ocrDocument.isNotEmpty()
        pageNames = saved.pages
        savedState[KEY_BILL] = saved.id
        savedState[KEY_PAGES] = ArrayList(saved.pages)
        savedState.remove<Any>(KEY_ON_INPUT)
        if (editing) savedState[KEY_EDITING] = true else savedState.remove<Any>(KEY_EDITING)
        mutableState.update {
            it.copy(
                pages = pages,
                instruction = saved.instruction.ifEmpty { it.instruction },
                result = resultOf(saved),
                editing = editing,
                error = null,
            )
        }
        lookUpDayRate()
    }

    private fun resultOf(saved: SavedBill) = SplitResultState(saved, localCurrency(), dayRate)

    private fun localCurrency(): String = settings.value.localCurrency.trim().uppercase(Locale.ROOT)

    /**
     * Finds the day's rate for a bill on screen in another currency than the user's. It is only
     * shown: the bill is not changed, so opening a bill never rewrites it, and a rate the user typed
     * in stays first. Only finishing the split, which the user does, keeps it with the bill
     * ([finish]). With [tellWhenMissing], the person hears when no rate can be found.
     */
    private fun lookUpDayRate(tellWhenMissing: Boolean = false) {
        rateLookup?.cancel()
        val result = mutableState.value.result ?: return
        if (!result.needsRate) return
        val from = result.bill.currency.code
        val to = result.localCurrency
        if (dayRate?.let { it.from == from && it.to == to } == true) return
        rateLookup = viewModelScope.launch {
            val found = fx.rate(from, to)
            // Only for the bill on screen, while it is still in the same currencies.
            val current = mutableState.value.result ?: return@launch
            if (current.bill.currency.code != from || current.localCurrency != to) return@launch
            if (found == null) {
                if (tellWhenMissing) {
                    mutableState.update { it.copy(notice = "The day's rate could not be fetched. Check the connection and try again.") }
                }
                return@launch
            }
            dayRate = found
            mutableState.update { state -> state.copy(result = state.result?.let { resultOf(it.saved) }) }
        }
    }

    private suspend fun showPages(pages: List<Bitmap>) {
        val names = pages.map { pageStore.save(it) }
        forgetUnsavedPages()
        pageNames = names
        unsavedPages = names
        savedState[KEY_PAGES] = ArrayList(names)
        mutableState.update { it.copy(pages = pages) }
    }

    private fun forgetUnsavedPages() {
        val leftover = unsavedPages
        unsavedPages = emptyList()
        if (leftover.isNotEmpty()) viewModelScope.launch { pageStore.delete(leftover) }
    }

    /** Saves the change waiting to be saved without waiting, before the flow moves to another bill. */
    private fun saveNow(scope: CoroutineScope = viewModelScope) {
        val pending = pendingSave.value ?: return
        if (!pendingSave.compareAndSet(pending, null)) return
        claimPages(pending)
        scope.launch { persist(pending) }
    }

    /** The photos of [bill] are its own from the moment it goes to be saved, so leaving it does not delete them. */
    private fun claimPages(bill: SavedBill) {
        if (!isSkipped(bill)) unsavedPages = unsavedPages - bill.pages.toSet()
    }

    /** A bill typed in by hand is kept once it has something on it. */
    private fun isSkipped(bill: SavedBill) = (storedRevisions[bill.id] ?: 0) == 0 && bill.bill.isBlank()

    /**
     * Writes [bill] as the next revision of the stored copy it descends from. A write that has
     * started finishes even when the screen goes away meanwhile, so no change is half saved.
     */
    private suspend fun persist(bill: SavedBill) = withContext(NonCancellable) {
        saveLock.withLock {
            if (isSkipped(bill)) return@withLock
            try {
                val written = bills.save(bill.copy(revision = storedRevisions[bill.id] ?: 0))
                storedRevisions[written.id] = written.revision
                saveFailing = false
                if (written.share != null) showOnLink(written)
            } catch (e: BillConflictException) {
                onConflict(bill)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The change stays on screen and goes with the next save.
                if (!saveFailing) {
                    saveFailing = true
                    mutableState.update { it.copy(notice = "The bill could not be saved on this phone. Your changes stay on screen.") }
                }
            }
        }
    }

    /**
     * Shows [saved] on its link, so people pick from the bill as it is now, when what the page shows
     * changed. A link that no longer works is forgotten; one that cannot be reached now shows this
     * change with the next one. When the change took the settled amounts off the page, the user is told.
     */
    private fun showOnLink(saved: SavedBill) {
        val shares = shares ?: return
        val link = saved.share ?: return
        viewModelScope.launch {
            try {
                when (val projected = shares.project(saved, settings.value.myName)) {
                    null -> Unit

                    ShareRepository.Projection.Forget -> forgetLink(saved.id, link.shareId)

                    is ShareRepository.Projection.Shown -> {
                        // Only what was sent is recorded, so what the link's screen kept meanwhile stays.
                        if (mutableState.value.result?.saved?.id == saved.id) {
                            val shown = projected.link
                            updateLink(link.shareId) { it.copy(shown = shown.shown, settled = shown.settled, expiresAt = shown.expiresAt) }
                        }
                        if (projected.unsettled) mutableState.update { it.copy(notice = UNSETTLED) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Shown with the next change that goes through.
            }
        }
    }

    /** Forgets the link [shareId] of the bill [billId], if the bill on screen still has that one. */
    private fun forgetLink(billId: String, shareId: String) {
        val saved = mutableState.value.result?.saved ?: return
        if (saved.id == billId && saved.share?.shareId == shareId) change { it.copy(share = null) }
    }

    /** Another writer saved [bill] first. What is stored now is shown, and changes made to the refused copy are dropped. */
    private suspend fun onConflict(bill: SavedBill) {
        pendingSave.update { pending -> pending?.takeIf { it.id != bill.id } }
        val stored = bills.get(bill.id)
        if (stored == null) storedRevisions.remove(bill.id) else storedRevisions[stored.id] = stored.revision
        mutableState.update { state ->
            if (state.result?.saved?.id != bill.id) return@update state
            if (stored == null) {
                state.copy(result = null, editing = false, notice = "This bill was deleted, so the last change was not saved.")
            } else {
                state.copy(result = resultOf(stored), notice = "This bill was changed elsewhere, so it now shows the latest version.")
            }
        }
    }

    /** Brings back the step, the bill or the photos the user was working on after the system ended the app. */
    private fun restore() {
        val id = savedState.get<String>(KEY_BILL)
        val names = savedState.get<List<String>>(KEY_PAGES).orEmpty()
        val instruction = savedState.get<String>(KEY_INSTRUCTION)
        val editing = savedState.get<Boolean>(KEY_EDITING) == true
        val opened = savedState.get<Boolean>(KEY_OPENED) == true
        val onInput = savedState.get<Boolean>(KEY_ON_INPUT) == true
        if (id == null && names.isEmpty() && instruction == null) return
        work = viewModelScope.launch {
            val saved = id?.let { bills.get(it) }
            saved?.let { storedRevisions[it.id] = it.revision }
            mutableState.update { it.copy(openedFromHistory = opened) }
            if (saved != null && !onInput) {
                showSaved(saved, saved.pages.mapNotNull { pageStore.load(it) }, editing)
                return@launch
            }
            // The photo step: its photos and instruction, and the bill they were read as, if any.
            billId = saved?.id
            billPageNames = saved?.pages.orEmpty()
            billWasRead = saved?.ocrDocument?.isNotEmpty() == true
            pageNames = names
            unsavedPages = names - billPageNames.toSet()
            val pages = names.mapNotNull { pageStore.load(it) }
            mutableState.update { it.copy(pages = pages, instruction = instruction ?: it.instruction) }
        }
    }

    private fun showError(message: String, opensSettings: Boolean = false) =
        mutableState.update { it.copy(error = message, errorOpensSettings = opensSettings) }

    private fun launchWork(progress: Progress, block: suspend () -> Unit) {
        work?.cancel()
        val job = viewModelScope.launch {
            mutableState.update { it.copy(progress = progress, error = null, errorOpensSettings = false) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillAnalyzerException) {
                val signInHelps = e.signInHelps(canSignIn, signedIn = account.value.session != null)
                showError(e.message ?: "The bill could not be analysed.", opensSettings = signInHelps)
            } catch (e: IOException) {
                showError("That image could not be opened. Try another photo.")
            } catch (e: Exception) {
                // Last line of defence at the UI boundary (for example an ML Kit failure): tell the
                // user instead of crashing the flow.
                showError("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        work = job
        job.invokeOnCompletion {
            // Only the latest piece of work may clear the progress indicator.
            if (work === job) mutableState.update { it.copy(progress = Progress.IDLE) }
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
        const val KEY_BILL = "bill"
        const val KEY_PAGES = "pages"
        const val KEY_INSTRUCTION = "instruction"
        const val KEY_EDITING = "editing"
        const val KEY_OPENED = "opened"
        const val KEY_ON_INPUT = "onInput"
        const val UNSETTLED = "The bill changed, so its link no longer shows what everyone owes. Open the link to send the amounts again."
    }
}

/** Nothing has been put on the bill yet: no place, item, charge or total. */
private fun Bill.isBlank(): Boolean = merchant.isNullOrBlank() && items.isEmpty() && charges.isEmpty() && printedTotalMinor == null
