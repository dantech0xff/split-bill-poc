package com.example.splitbill.ui.split

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splitbill.SplitBillApp
import com.example.splitbill.data.contacts.Contact
import com.example.splitbill.data.contacts.ContactRepository
import com.example.splitbill.data.settings.AppSettings
import com.example.splitbill.domain.Bill
import com.example.splitbill.domain.SplitCalculator
import com.example.splitbill.domain.SplitPlan
import com.example.splitbill.domain.SplitPlanner
import com.example.splitbill.domain.SplitResult
import com.example.splitbill.domain.currencyMismatchNote
import com.example.splitbill.llm.BillAnalysis
import com.example.splitbill.llm.BillAnalyzer
import com.example.splitbill.llm.BillAnalyzerException
import com.example.splitbill.llm.BillPrompt
import com.example.splitbill.ocr.BillImageLoader
import com.example.splitbill.ocr.BillTextRecognizer
import com.example.splitbill.ocr.OcrLayoutEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class Progress { IDLE, LOADING_PHOTO, READING_TEXT, ASKING_CLAUDE }

/** A request for money that would be sent to a contact. Nothing is actually sent in this demo. */
data class PaymentRequest(val contact: Contact, val amountMinor: Long)

/** The analysed bill together with the user's current choices and the resulting shares. */
data class SplitResultState(
    val bill: Bill,
    /** The model's one-line summary of what it read. */
    val summary: String,
    /** The exact OCR document that was sent to the model, shown for transparency and debugging. */
    val ocrDocument: String,
    val plan: SplitPlan,
    val split: SplitResult,
)

data class SplitUiState(
    val photo: Bitmap? = null,
    val instruction: String = BillPrompt.DEFAULT_INSTRUCTION,
    val progress: Progress = Progress.IDLE,
    val error: String? = null,
    val result: SplitResultState? = null,
    val sentRequests: List<PaymentRequest>? = null,
) {
    val isBusy: Boolean get() = progress != Progress.IDLE
}

/**
 * Drives the split-bill flow: photo, on-device text recognition, bill analysis by the model, then
 * the user's choice of who shares what.
 */
class SplitBillViewModel(
    contactRepository: ContactRepository,
    private val settings: StateFlow<AppSettings>,
    private val imageLoader: BillImageLoader,
    private val recognizer: BillTextRecognizer,
    private val analyzerFor: (AppSettings) -> BillAnalyzer,
) : ViewModel() {

    val contacts: List<Contact> = contactRepository.contacts()
    private val contactsById = contacts.associateBy { it.id }

    private val mutableState = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = mutableState.asStateFlow()

    private var work: Job? = null

    fun startNewSplit() {
        work?.cancel()
        mutableState.value = SplitUiState()
    }

    fun onInstructionChanged(text: String) = mutableState.update { it.copy(instruction = text) }

    fun onPhotoPicked(uri: Uri) = launchWork(Progress.LOADING_PHOTO) {
        val photo = imageLoader.load(uri)
        mutableState.update { it.copy(photo = photo) }
    }

    fun onSamplePicked() = launchWork(Progress.LOADING_PHOTO) {
        val photo = imageLoader.loadSample()
        mutableState.update { it.copy(photo = photo) }
    }

    fun analyze() {
        val current = mutableState.value
        val photo = current.photo ?: return
        if (current.isBusy) return
        val appSettings = settings.value
        if (!appSettings.hasApiKey) {
            mutableState.update { it.copy(error = "Add your Anthropic API key in Settings first.") }
            return
        }

        launchWork(Progress.READING_TEXT) {
            val ocr = recognizer.recognize(photo)
            if (ocr.isEmpty) {
                showError("No text was found in this photo. Make sure the bill is flat, in focus and well lit.")
                return@launchWork
            }

            mutableState.update { it.copy(progress = Progress.ASKING_CLAUDE) }
            // Off the main thread: switching settings closes the previous HTTP client here.
            val analyzer = withContext(Dispatchers.IO) { analyzerFor(appSettings) }
            when (val analysis = analyzer.analyze(ocr, current.instruction, appSettings.localCurrency)) {
                is BillAnalysis.Rejected -> showError(analysis.reason)
                is BillAnalysis.Ready -> {
                    val foreignCurrencyNote = analysis.bill.currencyMismatchNote(appSettings.localCurrency)
                    val bill = analysis.bill.copy(warnings = analysis.bill.warnings + listOfNotNull(foreignCurrencyNote))
                    val plan = SplitPlanner.fromHint(analysis.splitHint, bill, contacts)
                    val result = SplitResultState(
                        bill = bill,
                        summary = analysis.message,
                        ocrDocument = OcrLayoutEncoder.encode(ocr),
                        plan = plan,
                        split = SplitCalculator.split(bill, plan.participantIds, plan.sharersByItem()),
                    )
                    mutableState.update { it.copy(result = result) }
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

    /** Leaves the result and returns to the photo and instruction, keeping both. */
    fun backToInput() = mutableState.update { it.copy(result = null, sentRequests = null) }

    fun toggleMe() = editPlan { it.withMeToggled() }

    fun toggleContact(contactId: String) = editPlan { it.withContactToggled(contactId) }

    fun setItemSharers(itemIndex: Int, sharerIds: Set<String>) = editPlan { it.withItemSharers(itemIndex, sharerIds) }

    /**
     * Records the requests for the confirmation screen. This is where a real payment app would call
     * its payments API; the proof of concept sends nothing.
     */
    fun sendRequests() {
        val result = mutableState.value.result ?: return
        val requests = result.split.shares.mapNotNull { share ->
            val contact = contactsById[share.personId] ?: return@mapNotNull null
            PaymentRequest(contact, share.totalMinor).takeIf { it.amountMinor > 0 }
        }
        if (requests.isNotEmpty()) mutableState.update { it.copy(sentRequests = requests) }
    }

    private fun editPlan(change: (SplitPlan) -> SplitPlan) = mutableState.update { state ->
        val result = state.result ?: return@update state
        val plan = change(result.plan)
        state.copy(result = result.copy(plan = plan, split = SplitCalculator.split(result.bill, plan.participantIds, plan.sharersByItem())))
    }

    private fun showError(message: String) = mutableState.update { it.copy(error = message) }

    private fun launchWork(progress: Progress, block: suspend () -> Unit) {
        work?.cancel()
        val job = viewModelScope.launch {
            mutableState.update { it.copy(progress = progress, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillAnalyzerException) {
                showError(e.message ?: "The bill could not be analysed.")
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SplitBillApp
                val container = app.container
                SplitBillViewModel(
                    contactRepository = container.contacts,
                    settings = container.settings.settings,
                    imageLoader = container.imageLoader,
                    recognizer = container.recognizer,
                    analyzerFor = container::analyzerFor,
                )
            }
        }
    }
}
