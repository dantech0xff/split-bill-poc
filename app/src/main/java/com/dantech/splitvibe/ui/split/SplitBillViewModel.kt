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
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.fx.FxRepository
import com.dantech.splitvibe.data.people.ContactNames
import com.dantech.splitvibe.data.people.PeopleRepository
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Division
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.SplitPlanner
import com.dantech.splitvibe.domain.SplitResult
import com.dantech.splitvibe.llm.BillAnalysis
import com.dantech.splitvibe.llm.BillAnalyzer
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.MAX_INSTRUCTION_CHARS
import com.dantech.splitvibe.ocr.BillImageLoader
import com.dantech.splitvibe.ocr.BillTextRecognizer
import com.dantech.splitvibe.ocr.OcrLayoutEncoder
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the instruction field holds until the user types something else. */
const val DEFAULT_INSTRUCTION = "Split this bill"

/** What the screen calls the app's own user. */
const val ME_LABEL = "You"

enum class Progress { IDLE, LOADING_PHOTO, READING_TEXT, ASKING_CLAUDE }

/** A request for money that would be sent to someone. Nothing is actually sent yet. */
data class PaymentRequest(val participant: Participant, val amountMinor: Long)

/** The bill on screen and what it comes to under its plan. */
data class SplitResultState(
    val saved: SavedBill,
    /** The currency the user counts in, such as VND, which the bill is also shown in when it differs. */
    val localCurrency: String = "",
) {
    val bill: Bill get() = saved.bill
    val plan: SplitPlan get() = saved.plan
    val split: SplitResult = saved.plan.result(saved.bill)

    /** The bill is in another currency than the user's, so it is also shown converted. */
    val needsRate: Boolean = bill.currency.code.isNotEmpty() && localCurrency.isNotEmpty() && bill.currency.code != localCurrency

    /** The rate that converts this bill into [localCurrency], when there is one. */
    val exchangeRate: ExchangeRate? = saved.exchangeRate?.takeIf { needsRate && it.converts(bill, localCurrency) }

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
    val sentRequests: List<PaymentRequest>? = null,
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
    people: PeopleRepository,
    private val pageStore: BillPages,
    private val fx: FxRepository,
    private val contactNames: ContactNames,
    private val savedState: SavedStateHandle,
    private val clock: () -> Long = System::currentTimeMillis,
    saveDelayMs: Long = SAVE_DELAY_MS,
) : ViewModel() {

    /** The people the user split with before, most recent first, to suggest their names. */
    val people: StateFlow<List<Person>> = people.people.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val mutableState = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = mutableState.asStateFlow()

    private var work: Job? = null

    /** The bill waiting to be saved. A few changes in quick succession are saved once. */
    private val pendingSave = MutableStateFlow<SavedBill?>(null)

    /** One save at a time, so that each starts from the revision the one before it wrote. */
    private val saveLock = Mutex()

    /** The revision each bill's stored copy is at, so a save never writes over a newer one. */
    private val storedRevisions = mutableMapOf<String, Int>()

    /** The bill this flow works on, kept when the user goes back to change the photo or the instruction. */
    private var billId: String? = null

    /** The stored photos of the pages on screen; [unsavedPages] belong to no saved bill yet. */
    private var pageNames: List<String> = emptyList()
    private var unsavedPages: List<String> = emptyList()

    init {
        viewModelScope.launch {
            pendingSave.debounce(saveDelayMs).filterNotNull().collect { bill ->
                // Taken off the queue first, so it is saved once even if it is also saved at once below.
                if (pendingSave.compareAndSet(bill, null)) persist(bill)
            }
        }
        restore()
    }

    override fun onCleared() {
        // A change made a moment before the screen went away is still saved.
        saveNow(CoroutineScope(Dispatchers.IO))
    }

    fun startNewSplit() {
        saveNow()
        work?.cancel()
        forgetUnsavedPages()
        billId = null
        pageNames = emptyList()
        listOf(KEY_BILL, KEY_PAGES, KEY_INSTRUCTION, KEY_EDITING, KEY_OPENED).forEach { savedState.remove<Any>(it) }
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

    fun onPhotoPicked(uri: Uri) = onPagesPicked(listOf(uri))

    /** The pages of one bill, in order, from the document scanner or another app. */
    fun onPagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        launchWork(Progress.LOADING_PHOTO) { showPages(uris.map { imageLoader.load(it) }) }
    }

    fun onSamplePicked() = launchWork(Progress.LOADING_PHOTO) { showPages(listOf(imageLoader.loadSample())) }

    /** Starts a bill typed in by hand, with no photo, in the currency the app works in. */
    fun startManualEntry() {
        work?.cancel()
        val bill = Bill.empty(BillCurrency.resolve(settings.value.localCurrency, null, emptyList()))
        val now = clock()
        val saved = SavedBill(
            id = billId ?: UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            bill = bill,
            plan = SplitPlan().withBill(bill),
            pages = pageNames,
        )
        // Saved once something is typed into it: an empty bill is not worth keeping.
        showSaved(saved, mutableState.value.pages, editing = true)
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
                        // Reading the same bill again replaces its earlier reading rather than adding a second bill.
                        id = billId ?: UUID.randomUUID().toString(),
                        createdAt = now,
                        updatedAt = now,
                        bill = bill,
                        plan = SplitPlanner.fromHint(analysis.splitHint, bill, people.value),
                        instruction = current.instruction,
                        summary = analysis.message,
                        ocrDocument = OcrLayoutEncoder.encode(read),
                        pages = pageNames,
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
            saved.copy(bill = bill, plan = saved.plan.withBill(bill))
        }
        // A bill moved to another currency needs another rate.
        mutableState.value.result?.takeIf { it.bill.currency != before }?.let { ensureExchangeRate(it.saved) }
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

    /** Converts the bill at a rate the user typed in, such as the one their card was charged at. */
    fun setExchangeRate(rate: BigDecimal) {
        val result = mutableState.value.result ?: return
        if (!result.needsRate || rate.signum() <= 0) return
        change { it.copy(exchangeRate = ExchangeRate(it.bill.currency.code, result.localCurrency, rate)) }
    }

    /** Converts the bill at the day's rate again, in place of one the user typed in. */
    fun useDayRate() {
        val result = mutableState.value.result ?: return
        ensureExchangeRate(result.saved, replace = true)
    }

    /**
     * Records the requests for the confirmation screen and finishes the split. This is where a
     * real payment app would call its payments API; nothing is sent yet.
     */
    fun sendRequests() {
        val result = mutableState.value.result ?: return
        if (result.problems.isNotEmpty()) return
        val requests = result.split.shares.mapNotNull { share ->
            val participant = result.plan.participant(share.personId) ?: return@mapNotNull null
            PaymentRequest(participant, share.totalMinor).takeIf { !participant.isMe && it.amountMinor > 0 }
        }
        if (requests.isEmpty()) return
        finish()
        mutableState.update { it.copy(sentRequests = requests) }
    }

    /** Marks the split as finished; it stays in the history and can still be opened and changed. */
    fun finish() {
        val result = mutableState.value.result ?: return
        if (result.problems.isNotEmpty()) return
        change { it.copy(status = BillStatus.FINAL) }
    }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    private fun editPlan(edit: (SplitPlan, Bill) -> SplitPlan) = change { it.copy(plan = edit(it.plan, it.bill)) }

    private fun change(transform: (SavedBill) -> SavedBill) {
        val result = mutableState.value.result ?: return
        val next = transform(result.saved)
        if (next == result.saved) return
        mutableState.update { it.copy(result = resultOf(next)) }
        pendingSave.value = next
    }

    private fun showSaved(saved: SavedBill, pages: List<Bitmap>, editing: Boolean) {
        billId = saved.id
        pageNames = saved.pages
        storedRevisions.putIfAbsent(saved.id, saved.revision)
        savedState[KEY_BILL] = saved.id
        if (editing) savedState[KEY_EDITING] = true else savedState.remove<Any>(KEY_EDITING)
        mutableState.update {
            it.copy(
                pages = pages,
                instruction = saved.instruction.ifEmpty {
                    it.instruction
                },
                result = resultOf(saved),
                editing = editing,
                error = null,
            )
        }
        ensureExchangeRate(saved)
    }

    private fun resultOf(saved: SavedBill) = SplitResultState(saved, localCurrency())

    private fun localCurrency(): String = settings.value.localCurrency.trim().uppercase(Locale.ROOT)

    /**
     * Finds the day's rate for a bill in another currency than the user's, unless it already has
     * one for that pair; with [replace], a rate the user typed in gives way to it too. Without a
     * rate to be found, a rate that no longer fits the bill is dropped.
     */
    private fun ensureExchangeRate(saved: SavedBill, replace: Boolean = false) {
        val result = resultOf(saved)
        val kept = saved.exchangeRate
        if (!result.needsRate) {
            if (kept != null) change { it.copy(exchangeRate = null) }
            return
        }
        if (!replace && result.exchangeRate != null) return
        val from = saved.bill.currency.code
        val to = result.localCurrency
        viewModelScope.launch {
            val found = fx.rate(from, to)
            // Only for the bill on screen, and only while it is still in the same currency.
            val current = mutableState.value.result?.saved?.takeIf { it.id == saved.id && it.bill.currency.code == from } ?: return@launch
            when {
                found != null -> change { it.copy(exchangeRate = found) }

                current.exchangeRate?.converts(current.bill, to) == false -> change { it.copy(exchangeRate = null) }

                replace -> mutableState.update {
                    it.copy(notice = "The day's rate could not be fetched. Check the connection and try again.")
                }
            }
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
        if (pendingSave.compareAndSet(pending, null)) scope.launch { persist(pending) }
    }

    /** Writes [bill] as the next revision of its stored copy. */
    private suspend fun persist(bill: SavedBill) = saveLock.withLock {
        // A bill typed in by hand is kept once it has something on it.
        if ((storedRevisions[bill.id] ?: 0) == 0 && bill.bill.isBlank()) return@withLock
        try {
            val written = bills.save(bill.copy(revision = storedRevisions[bill.id] ?: bill.revision))
            storedRevisions[written.id] = written.revision
            unsavedPages = unsavedPages - written.pages.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BillConflictException) {
            // Another writer won. Show what is stored now rather than silently writing over it.
            val stored = bills.get(bill.id)
            stored?.let { storedRevisions[it.id] = it.revision }
            mutableState.update { state ->
                if (state.result?.saved?.id != bill.id) return@update state
                if (stored == null) {
                    state.copy(result = null, editing = false, notice = "This bill was deleted, so the last change was not saved.")
                } else {
                    state.copy(result = resultOf(stored), notice = "This bill was changed elsewhere, so it now shows the latest version.")
                }
            }
        } catch (e: Exception) {
            mutableState.update { it.copy(notice = "The bill could not be saved: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /** Brings back the bill or the photos the user was working on after the system ended the app. */
    private fun restore() {
        val id = savedState.get<String>(KEY_BILL)
        val names = savedState.get<List<String>>(KEY_PAGES).orEmpty()
        val instruction = savedState.get<String>(KEY_INSTRUCTION)
        val editing = savedState.get<Boolean>(KEY_EDITING) == true
        val opened = savedState.get<Boolean>(KEY_OPENED) == true
        if (id == null && names.isEmpty() && instruction == null) return
        work = viewModelScope.launch {
            val saved = id?.let { bills.get(it) }
            if (saved != null) {
                mutableState.update { it.copy(openedFromHistory = opened) }
                showSaved(saved, saved.pages.mapNotNull { pageStore.load(it) }, editing)
            } else {
                pageNames = names
                unsavedPages = names
                val pages = names.mapNotNull { pageStore.load(it) }
                mutableState.update { it.copy(pages = pages, instruction = instruction ?: it.instruction) }
            }
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
                val signInHelps = canSignIn &&
                    (
                        e.kind == BillAnalyzerException.Kind.SIGN_IN_EXPIRED ||
                            (e.kind == BillAnalyzerException.Kind.QUOTA_EXHAUSTED && account.value.session == null)
                        )
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
    }
}

/** Nothing has been put on the bill yet: no place, item, charge or total. */
private fun Bill.isBlank(): Boolean = merchant.isNullOrBlank() && items.isEmpty() && charges.isEmpty() && printedTotalMinor == null
