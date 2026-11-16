package com.dantech.splitvibe.ui.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dantech.splitvibe.assistant.SplitAssistant
import com.dantech.splitvibe.assistant.VoiceEnd
import com.dantech.splitvibe.assistant.VoiceInput
import com.dantech.splitvibe.assistant.VoiceSupport
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.SplitEdit
import com.dantech.splitvibe.domain.SplitEdits
import com.dantech.splitvibe.domain.SplitPlan
import com.dantech.splitvibe.domain.codePointLength
import com.dantech.splitvibe.domain.takeCodePoints
import com.dantech.splitvibe.llm.BillAnalyzerException
import com.dantech.splitvibe.llm.SplitRevision
import com.dantech.splitvibe.llm.SplitRevisionRequest
import com.dantech.splitvibe.llm.signInHelps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The bill on the split screen as the assistant reads and changes it. The split screen saves every change. */
interface SplitOnScreen {
    /** The bill as it is on screen, or null when none is open. */
    val billOnScreen: StateFlow<SavedBill?>

    /** Puts [bill], split as [plan], on screen in place of the bill and split there, as one change. */
    fun replaceSplit(bill: Bill, plan: SplitPlan)
}

/** One edit the AI proposed, as the screen lists it. */
data class ProposedEdit(
    /** Where it is in the list the AI proposed, which is how it is dropped or kept again. */
    val index: Int,
    val text: String,
    /** The user keeps it; a dropped one is not applied. */
    val kept: Boolean,
    /** Why it cannot be applied, while it is kept; null for one that can. */
    val reason: String? = null,
)

/** What one person pays before the proposed changes and after them; an amount is null while the split does not add up. */
data class ShareChange(
    val name: String,
    val beforeMinor: Long?,
    val afterMinor: Long?,
    val onBillBefore: Boolean = true,
    val onBillAfter: Boolean = true,
) {
    val changed: Boolean get() = beforeMinor != afterMinor || onBillBefore != onBillAfter
}

/** The changes the AI proposed, worked out on the split on screen, for the user to check before anything changes. */
data class ProposalUi(
    /** What the user asked for. */
    val instruction: String,
    /** What the AI says about the changes, in the language of the instruction; empty when it said nothing. */
    val explanation: String,
    val edits: List<ProposedEdit>,
    /** How many of the proposed edits could not be read at all, and are left out. */
    val unreadable: Int,
    val shares: List<ShareChange>,
    /** What keeps the split from being finished after the changes, which the result screen then shows too. */
    val problems: List<String>,
    val currency: BillCurrency,
    /** Applying the kept edits changes the split. */
    val canApply: Boolean,
)

/** Listening to the user, as the mic button and what is around it show it. */
data class VoiceUi(
    /** The phone can turn speech into text, so the mic is offered. */
    val available: Boolean = false,
    val listening: Boolean = false,
    /** Only the phone's speech service can listen, which the user is asked about first. */
    val askingNetwork: Boolean = false,
    /** The screen is to ask for the microphone now. */
    val askingPermission: Boolean = false,
    /** What to tell the user about listening, such as a microphone that is not allowed; never an error, as typing still works. */
    val note: String? = null,
)

/** What the assistant on the result screen shows. */
data class AssistantUiState(
    /** What is in the field, typed or heard. */
    val text: String = "",
    val working: Boolean = false,
    /** What the AI asked back; what is sent next answers it. */
    val question: String? = null,
    val proposal: ProposalUi? = null,
    /** What the last applied change did, while it can still be undone. */
    val applied: String? = null,
    /** Something to tell the user that is not an error, such as that nothing needs changing. */
    val note: String? = null,
    val error: String? = null,
    /** The error is fixed by signing in, which this build can do, so it comes with a way to Settings. */
    val errorOpensSettings: Boolean = false,
    /** AI is not set up in this build or is switched off, which the assistant says above its field. */
    val notice: String? = null,
    val canSend: Boolean = false,
    val voice: VoiceUi = VoiceUi(),
)

/**
 * Changes the split on the result screen by a sentence the user types or says, such as "Lan didn't
 * have beer". The AI proposes edits. They are worked out on the split on screen with
 * [SplitEdits.apply] and the calculator, so the user sees each one in words and what everyone pays
 * before and after them. Nothing changes until the user applies them. The split screen then saves
 * the change as it saves any other, and the change can be undone until the split changes again.
 *
 * The proposed changes are always worked out on the split as it is on screen, so what is applied is
 * exactly what is shown, also after a change made by hand meanwhile.
 */
class SplitAssistantViewModel(
    private val host: SplitOnScreen,
    private val assistant: SplitAssistant,
    private val voice: VoiceInput,
    private val settings: StateFlow<AppSettings>,
    private val account: StateFlow<Account>,
) : ViewModel() {

    private val draft = MutableStateFlow(Draft(voiceAvailable = voice.support != VoiceSupport.NONE))

    val state: StateFlow<AssistantUiState> = combine(draft, host.billOnScreen, account, ::uiOf)
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiOf(draft.value, host.billOnScreen.value, account.value))

    private var asking: Job? = null

    init {
        viewModelScope.launch {
            // Another bill on screen starts afresh: nothing proposed or asked for one bill belongs to another.
            host.billOnScreen.map { it?.id }.distinctUntilChanged().drop(1).collect {
                asking?.cancel()
                voice.cancel()
                draft.value = Draft(voiceAvailable = voice.support != VoiceSupport.NONE)
            }
        }
    }

    override fun onCleared() = voice.cancel()

    fun onTextChanged(text: String) {
        // The proxy takes an instruction up to this long, so the field holds no more.
        draft.update { it.copy(text = text.takeCodePoints(SplitRevisionRequest.MAX_INSTRUCTION_CODE_POINTS)) }
    }

    /** Asks the AI for the change in the field, or, after it asked a question, sends the answer with what it asked about. */
    fun send() {
        val current = draft.value
        val saved = host.billOnScreen.value ?: return
        val typed = current.text.trim()
        if (current.working || typed.isEmpty() || account.value.ai == AiStatus.NOT_SET_UP) return
        val instruction = current.question?.let { followUp(it, typed) } ?: typed
        voice.cancel()
        draft.update { it.copy(working = true, listening = false, note = null, error = null, errorOpensSettings = false) }
        asking?.cancel()
        val job = viewModelScope.launch {
            try {
                val revision = assistant.revise(instruction, saved.bill, saved.plan, settings.value.myName)
                // An answer about another bill than the one on screen now says nothing about it.
                if (host.billOnScreen.value?.id == saved.id) show(revision, saved.id, instruction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillAnalyzerException) {
                val signInHelps = e.signInHelps(assistant.canSignIn, signedIn = account.value.session != null)
                draft.update { it.copy(error = e.message, errorOpensSettings = signInHelps) }
            }
        }
        asking = job
        job.invokeOnCompletion {
            // Only the latest request may say it is done.
            if (asking === job) draft.update { it.copy(working = false) }
        }
    }

    /** Stops asking. The same request, sent again, is not charged again. */
    fun cancel() {
        asking?.cancel()
        asking = null
        draft.update { it.copy(working = false) }
    }

    /** Drops one of the proposed edits, or keeps it again. */
    fun toggleEdit(index: Int) = draft.update { current ->
        val proposal = current.proposal ?: return@update current
        if (index !in proposal.edits.indices) return@update current
        val dropped = if (index in proposal.dropped) proposal.dropped - index else proposal.dropped + index
        current.copy(proposal = proposal.copy(dropped = dropped))
    }

    /** Applies the kept edits to the split on screen, as one change, which can then be undone. */
    fun apply() {
        val proposal = draft.value.proposal ?: return
        val saved = host.billOnScreen.value?.takeIf { it.id == proposal.billId } ?: return
        val result = SplitEdits.apply(saved.bill, saved.plan, proposal.kept)
        if (result.bill == saved.bill && result.plan == saved.plan) return
        host.replaceSplit(result.bill, result.plan)
        val summary = proposal.explanation.ifBlank { "The split was changed." }
        draft.update { it.copy(proposal = null, undo = Undo(saved.id, saved.bill, saved.plan, result.bill, result.plan, summary)) }
    }

    /** Puts the proposed changes aside without applying any of them. */
    fun discard() = draft.update { it.copy(proposal = null) }

    /** Brings back the bill and split from before the last applied change, while nothing else changed them since. */
    fun undo() {
        val undo = draft.value.undo ?: return
        val saved = host.billOnScreen.value ?: return
        if (!undo.holdsFor(saved)) return
        host.replaceSplit(undo.bill, undo.plan)
        draft.update { it.copy(undo = null) }
    }

    /** Leaves the question the AI asked unanswered, so the next text sent is a change of its own. */
    fun forgetQuestion() = draft.update { it.copy(question = null) }

    fun dismissError() = draft.update { it.copy(error = null, errorOpensSettings = false) }

    /** A tap on the mic: listens, or stops listening, after asking for what listening needs the first time. */
    fun onMic() {
        if (voice.isListening) return voice.stop()
        if (draft.value.working) return
        when (voice.support) {
            VoiceSupport.NONE -> return draft.update { it.copy(voiceAvailable = false) }
            VoiceSupport.NETWORK -> if (!voice.networkAllowed) return draft.update { it.copy(askingNetwork = true) }
            VoiceSupport.ON_DEVICE -> Unit
        }
        if (!voice.hasPermission) return draft.update { it.copy(askingPermission = true, voiceNote = null) }
        listen()
    }

    /** The user's answer to whether the phone's speech service may hear them. Saying no leaves typing as it was. */
    fun onNetworkVoice(allowed: Boolean) {
        draft.update { it.copy(askingNetwork = false) }
        if (!allowed) return
        voice.networkAllowed = true
        onMic()
    }

    /** The screen has asked for the microphone, so it does not ask again, also after it is drawn anew. */
    fun onMicPermissionAsked() = draft.update { it.copy(askingPermission = false) }

    /** The system's answer to the request for the microphone. Without it the change is typed, and nothing is said to be wrong. */
    fun onMicPermission(granted: Boolean) {
        draft.update { it.copy(askingPermission = false) }
        if (granted) listen() else draft.update { it.copy(voiceNote = NO_MICROPHONE) }
    }

    private fun listen() {
        val before = draft.value.text
        draft.update { it.copy(listening = true, voiceNote = null) }
        voice.listen(
            onPartial = { heard -> draft.update { it.copy(text = joined(before, heard)) } },
            onEnd = { end ->
                draft.update { current ->
                    val ended = current.copy(listening = false, voiceAvailable = voice.support != VoiceSupport.NONE)
                    when (end) {
                        is VoiceEnd.Heard -> ended.copy(text = joined(before, end.text))
                        VoiceEnd.NothingHeard -> ended.copy(text = before, voiceNote = NOTHING_HEARD)
                        VoiceEnd.NoPermission -> ended.copy(text = before, voiceNote = NO_MICROPHONE)
                        VoiceEnd.LanguageUnavailable -> ended.copy(text = before, voiceNote = LANGUAGE_UNAVAILABLE)
                        VoiceEnd.Failed -> ended.copy(text = before, voiceNote = NOT_TURNED_INTO_TEXT)
                    }
                }
            },
        )
    }

    private fun show(revision: SplitRevision, billId: String, instruction: String) {
        val question = revision.question
        draft.update { current ->
            when {
                // The user answers first, whatever else the AI sent with its question.
                question != null -> current.copy(text = "", question = Question(instruction, question), proposal = null)

                revision.edits.isEmpty() -> current.copy(
                    question = null,
                    proposal = null,
                    note = revision.explanation.ifBlank { "Claude found nothing to change." },
                )

                else -> current.copy(
                    text = "",
                    question = null,
                    proposal = Proposal(billId, instruction, revision.edits, emptySet(), revision.explanation, revision.unreadable),
                )
            }
        }
    }

    private fun uiOf(draft: Draft, saved: SavedBill?, account: Account): AssistantUiState {
        val notice = when (account.ai) {
            AiStatus.NOT_SET_UP -> SplitAssistant.NOT_SET_UP
            AiStatus.OFF -> SplitAssistant.SWITCHED_OFF
            AiStatus.AVAILABLE -> null
        }
        val proposal = draft.proposal?.takeIf { saved?.id == it.billId }
        return AssistantUiState(
            text = draft.text,
            working = draft.working,
            question = draft.question?.question,
            proposal = if (saved != null && proposal != null) proposalUi(proposal, saved.bill, saved.plan) else null,
            applied = draft.undo?.takeIf { saved != null && it.holdsFor(saved) }?.summary,
            note = draft.note,
            // An error that only repeats the notice says nothing new.
            error = draft.error?.takeIf { it != notice },
            errorOpensSettings = draft.errorOpensSettings,
            notice = notice,
            // When AI is only switched off, the request still goes out: the proxy may have turned it back on.
            canSend = saved != null && !draft.working && !draft.listening && draft.text.isNotBlank() && account.ai != AiStatus.NOT_SET_UP,
            voice = VoiceUi(
                available = draft.voiceAvailable && account.ai != AiStatus.NOT_SET_UP,
                listening = draft.listening,
                askingNetwork = draft.askingNetwork,
                askingPermission = draft.askingPermission,
                note = draft.voiceNote,
            ),
        )
    }

    /**
     * The kept edits of [proposal] worked out on [bill] split as [plan]: each edit in words, a kept
     * one that cannot be applied with its reason, and what everyone pays before and after.
     */
    private fun proposalUi(proposal: Proposal, bill: Bill, plan: SplitPlan): ProposalUi {
        val keptAt = proposal.edits.indices.filter { it !in proposal.dropped }
        val kept = keptAt.map(proposal.edits::get)
        val result = SplitEdits.apply(bill, plan, kept)
        // A kept edit is described as it is applied, after the kept edits before it, so a reason lines
        // up with its sentence; a dropped one as it stood in the list the AI proposed.
        val keptTexts = SplitEdits.describe(kept, bill, plan, ME_NAME)
        val proposedTexts = SplitEdits.describe(proposal.edits, bill, plan, ME_NAME)
        val reasons = result.rejected.associate { keptAt[it.index] to it.reason }
        val edits = proposal.edits.indices.map { index ->
            val position = keptAt.indexOf(index)
            if (position < 0) {
                ProposedEdit(index, proposedTexts[index], kept = false)
            } else {
                ProposedEdit(index, keptTexts[position], kept = true, reason = reasons[index])
            }
        }
        return ProposalUi(
            instruction = proposal.instruction,
            explanation = proposal.explanation,
            edits = edits,
            unreadable = proposal.unreadable,
            shares = shareChanges(bill, plan, result.bill, result.plan),
            problems = result.plan.problems(result.bill),
            currency = result.bill.currency,
            canApply = result.bill != bill || result.plan != plan,
        )
    }

    /** What each person pays under [plan] and under [changedPlan], with whoever the changes add after everyone else. */
    private fun shareChanges(bill: Bill, plan: SplitPlan, changedBill: Bill, changedPlan: SplitPlan): List<ShareChange> {
        val before = plan.result(bill)
        val after = changedPlan.result(changedBill)

        // The same id under another name is someone else: the id of someone taken off can go to someone added.
        fun List<Participant>.has(person: Participant) = any { it.id == person.id && it.name == person.name }

        val stayed = plan.participants.map { person ->
            val stays = changedPlan.participants.has(person)
            ShareChange(
                name = nameOf(person),
                beforeMinor = before.shareOf(person.id)?.totalMinor,
                afterMinor = if (stays) after.shareOf(person.id)?.totalMinor else null,
                onBillAfter = stays,
            )
        }
        val added = changedPlan.participants.filterNot { plan.participants.has(it) }.map { person ->
            ShareChange(nameOf(person), beforeMinor = null, afterMinor = after.shareOf(person.id)?.totalMinor, onBillBefore = false)
        }
        return stayed + added
    }

    private fun nameOf(person: Participant): String = if (person.isMe) ME_LABEL else person.name.ifBlank { "Someone" }

    /** What the assistant keeps between the user's steps; what it shows follows from this and the bill on screen. */
    private data class Draft(
        val text: String = "",
        val working: Boolean = false,
        val question: Question? = null,
        val proposal: Proposal? = null,
        val undo: Undo? = null,
        val note: String? = null,
        val error: String? = null,
        val errorOpensSettings: Boolean = false,
        val voiceAvailable: Boolean,
        val listening: Boolean = false,
        val askingNetwork: Boolean = false,
        val askingPermission: Boolean = false,
        val voiceNote: String? = null,
    )

    /** What the AI asked back about [instruction]. */
    private data class Question(val instruction: String, val question: String)

    /** The edits the AI proposed for the bill [billId], and the ones the user dropped, by their place in [edits]. */
    private data class Proposal(
        val billId: String,
        val instruction: String,
        val edits: List<SplitEdit>,
        val dropped: Set<Int>,
        val explanation: String,
        val unreadable: Int,
    ) {
        val kept: List<SplitEdit> get() = edits.filterIndexed { index, _ -> index !in dropped }
    }

    /** The bill and split from before an applied change, offered back while the bill on screen is still what the change made it. */
    private data class Undo(
        val billId: String,
        val bill: Bill,
        val plan: SplitPlan,
        val changedBill: Bill,
        val changedPlan: SplitPlan,
        val summary: String,
    ) {
        fun holdsFor(saved: SavedBill): Boolean = saved.id == billId && saved.bill == changedBill && saved.plan == changedPlan
    }

    private companion object {
        /** Blank, so the sentences call the user "you", as the result screen does. */
        const val ME_NAME = ""

        const val NO_MICROPHONE = "To speak a change, allow the microphone for Split Vibe in the phone's settings. You can type it instead."
        const val NOTHING_HEARD = "Nothing was heard. Tap the mic to try again, or type the change."
        const val LANGUAGE_UNAVAILABLE =
            "This phone cannot turn speech in its language into text by itself. Tap the mic to try its speech service, or type the change."
        const val NOT_TURNED_INTO_TEXT = "Speech could not be turned into text just now. Type the change instead."

        /**
         * The answer to a question the AI asked, sent with what it asked about, so the AI has the whole
         * exchange: "Lan didn't have the drink (Which drink? The Tiger)". When that is longer than the
         * proxy takes, the question is left out, and then the first instruction.
         */
        fun followUp(question: Question, answer: String): String = listOf(
            "${question.instruction} (${question.question} $answer)",
            "${question.instruction} ($answer)",
            answer,
        ).first { it.codePointLength() <= SplitRevisionRequest.MAX_INSTRUCTION_CODE_POINTS }

        /** What was typed before listening, followed by what was heard. */
        fun joined(before: String, heard: String): String {
            val words = listOf(before.trim(), heard.trim()).filter { it.isNotEmpty() }.joinToString(" ")
            return words.takeCodePoints(SplitRevisionRequest.MAX_INSTRUCTION_CODE_POINTS)
        }
    }
}
