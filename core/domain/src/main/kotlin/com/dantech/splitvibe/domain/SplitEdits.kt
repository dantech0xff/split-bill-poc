package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** What [SplitEdits.apply] made of a list of edits: the bill and plan after them, and which edits it applied. */
data class SplitEditResult(
    val bill: Bill,
    val plan: SplitPlan,
    /** The edits that were applied, in order. */
    val applied: List<SplitEdit>,
    /** The edits that were not, in order. */
    val rejected: List<RejectedEdit>,
)

/** An edit [SplitEdits.apply] left out: where it was in the list, the edit, and why, in a short English sentence. */
data class RejectedEdit(val index: Int, val edit: SplitEdit, val reason: String)

/**
 * Applies the edits the AI proposes to a split, and describes them for the user to review.
 *
 * The edits are applied in order, each to the bill and plan that the edits before it left. Each one
 * is either applied whole or rejected with a reason, and a rejected edit changes nothing. An edit is
 * rejected when it names an item, a person or a ref that is not on the bill, carries a number that
 * cannot be right, takes the user off the bill, or gives parts that do not add up. Everything else
 * follows the rules the screens follow: [BillEditor] changes the bill, [SplitPlan] the split and
 * [Payments] who paid, and [SplitCalculator] works the shares out from them. One rule is the AI's
 * own: an edit of an item on a bill split as a whole starts from an even split by item, with every
 * other item shared by everyone, where the screen would bring back how it last divided each item.
 */
object SplitEdits {

    /** The edits of [edits] that can be applied to [bill] and [plan], applied in order. */
    fun apply(bill: Bill, plan: SplitPlan, edits: List<SplitEdit>): SplitEditResult {
        val applied = mutableListOf<SplitEdit>()
        val rejected = mutableListOf<RejectedEdit>()
        val end = walk(bill, plan, edits) { index, edit, _, reason ->
            if (reason == null) applied += edit else rejected += RejectedEdit(index, edit, reason)
        }
        return SplitEditResult(end.bill, end.plan, applied, rejected)
    }

    /**
     * One short sentence on what [edit] does to [bill] and [plan], with the names of the people and
     * items it names, such as "Beer: shared by Dana and Minh". [meName] is the user's own name, as
     * the user is called on the bill; a blank one makes them "You". A ref of someone added by an
     * earlier edit is not on [plan]: the list form of [describe] names them.
     */
    fun describe(edit: SplitEdit, bill: Bill, plan: SplitPlan, meName: String, locale: Locale = Locale.getDefault()): String =
        Describer(State(bill, plan), meName, emptyMap(), locale).sentence(edit)

    /**
     * One sentence for each of [edits], in order, each about the bill and plan that the edits before
     * it leave, as [apply] meets them. So a ref names the person its [SplitEdit.AddPerson] adds, and
     * a percentage charge shows the amount it adds.
     */
    fun describe(edits: List<SplitEdit>, bill: Bill, plan: SplitPlan, meName: String, locale: Locale = Locale.getDefault()): List<String> {
        val newPeople = edits.filterIsInstance<SplitEdit.AddPerson>().associate { it.ref to it.name }
        val sentences = mutableListOf<String>()
        walk(bill, plan, edits) { _, edit, before, _ -> sentences += Describer(before, meName, newPeople, locale).sentence(edit) }
        return sentences
    }

    /** Applies [edits] one after the other and tells [visit] about each: the state it met, and why it was rejected, if it was. */
    private inline fun walk(bill: Bill, plan: SplitPlan, edits: List<SplitEdit>, visit: (Int, SplitEdit, State, String?) -> Unit): State {
        var state = State(bill, plan)
        edits.forEachIndexed { index, edit ->
            val next = try {
                state.after(edit)
            } catch (e: Rejection) {
                visit(index, edit, state, e.reason)
                null
            } catch (e: ArithmeticException) {
                // A number past what the arithmetic holds, which the checks before it are to keep out.
                visit(index, edit, state, TOO_LARGE)
                null
            }
            if (next != null) {
                visit(index, edit, state, null)
                state = next
            }
        }
        return state
    }

    private fun State.after(edit: SplitEdit): State = when (edit) {
        is SplitEdit.ItemSharers -> itemSharers(edit)
        is SplitEdit.ItemShares -> itemShares(edit)
        is SplitEdit.SetMode -> copy(plan = if (edit.mode == SplitMode.BY_ITEM) byItem() else plan.withMode(edit.mode, bill))
        is SplitEdit.WholeShares -> wholeShares(edit)
        is SplitEdit.WholePercent -> wholePercent(edit)
        is SplitEdit.WholeAmounts -> wholeAmounts(edit)
        is SplitEdit.AddPerson -> addPerson(edit)
        is SplitEdit.RemovePerson -> removePerson(edit)
        is SplitEdit.SetSharing -> copy(plan = plan.withSharing(participant(edit.participantId), edit.sharing))
        is SplitEdit.AddCharge -> addCharge(edit)
        is SplitEdit.SetPayer -> copy(plan = plan.withPayments(Payments.single(participant(edit.participantId), bill.totalMinor)))
        is SplitEdit.ItemAmount -> itemAmount(edit)
        is SplitEdit.ItemQuantity -> itemQuantity(edit)
    }

    private fun State.itemSharers(edit: SplitEdit.ItemSharers): State {
        val item = item(edit.itemId)
        if (edit.participantIds.isEmpty()) reject(NOBODY)
        val sharers = edit.participantIds.map { id -> participant(id).also { if (it in plan.setup.notSharing) rejectNotSharing(it) } }
        return copy(plan = byItem().withItemSharers(item.id, sharers.toSet()))
    }

    private fun State.itemShares(edit: SplitEdit.ItemShares): State {
        val item = item(edit.itemId)
        if (edit.shares.isEmpty()) reject(NOBODY)
        if (edit.shares.values.any { it < 1 }) reject("Each share of an item has to be a whole number from 1.")
        return copy(plan = byItem().withItemDivision(item.id, Division.ByShares(parts(edit.shares))))
    }

    // Each part is checked on its own before the calculator adds them up, so that no sum of hostile
    // numbers can overflow into exactly the total, and no part the split ignores is kept below zero.

    private fun State.wholeShares(edit: SplitEdit.WholeShares): State {
        if (edit.shares.values.any { it < 0 }) reject("A share cannot be less than zero.")
        return withWhole(Division.ByShares(parts(edit.shares)))
    }

    private fun State.wholePercent(edit: SplitEdit.WholePercent): State {
        if (edit.basisPoints.values.any { it < 0 }) reject("A percentage cannot be less than zero.")
        if (edit.basisPoints.values.any { it > Division.ByPercent.FULL }) reject("A percentage cannot be more than 100%.")
        return withWhole(Division.ByPercent(parts(edit.basisPoints)))
    }

    private fun State.wholeAmounts(edit: SplitEdit.WholeAmounts): State {
        if (edit.amounts.values.any { it < 0 }) reject("An amount cannot be less than zero.")
        if (edit.amounts.values.any { it > bill.totalMinor }) {
            reject("No one can pay more than the bill total of ${bill.format(bill.totalMinor)}.")
        }
        return withWhole(Division.ByAmount(parts(edit.amounts)))
    }

    private fun State.addPerson(edit: SplitEdit.AddPerson): State {
        if (!REF.matches(edit.ref)) reject("This person could not be added.")
        if (edit.ref in refs) reject("This person was already added.")
        if (edit.name.isBlank()) reject("A new person needs a name.")
        val added = plan.withParticipantAdded(edit.name)
        return copy(plan = added, refs = refs + (edit.ref to added.participants.last().id))
    }

    private fun State.removePerson(edit: SplitEdit.RemovePerson): State {
        val id = participant(edit.participantId)
        if (id == ME_ID) reject("You stay on the bill. You can stop sharing it instead.")
        // The ref goes too: an id freed here can be given to someone added later.
        return copy(plan = plan.withParticipantRemoved(id, bill), refs = refs.filterValues { it != id })
    }

    private fun State.addCharge(edit: SplitEdit.AddCharge): State {
        val amount = edit.amountMinor
        val percent = edit.percentBasisPoints
        val magnitude = when {
            (amount == null) == (percent == null) -> reject("A charge needs an amount or a percentage, and only one of them.")

            amount != null -> {
                if (amount < 1) reject("A charge has to be more than zero.")
                if (amount > BillCurrency.MAX_AMOUNT_MINOR) reject(TOO_LARGE)
                amount
            }

            else -> {
                val share = chargeAmount(bill, checkNotNull(percent)) ?: reject("A percentage has to be more than 0% and at most 100%.")
                if (share == 0L) reject("${percentText(percent)} of the items comes to nothing.")
                share
            }
        }
        val signed = BillEditor.signedCharge(edit.kind, magnitude)
        val charged = BillEditor.withChargeAdded(bill, edit.label, edit.kind, signed)
        // The charge was paid on top of the printed total, or taken off it, so that total moves with it.
        val printed = bill.printedTotalMinor
        return withBill(if (printed == null) charged else BillEditor.withPrintedTotal(charged, printed + signed))
    }

    private fun State.itemAmount(edit: SplitEdit.ItemAmount): State {
        val item = item(edit.itemId)
        if (edit.amountMinor < 0) reject("An amount cannot be less than zero.")
        if (edit.amountMinor > BillCurrency.MAX_AMOUNT_MINOR) reject(TOO_LARGE)
        return withBill(BillEditor.withItemAmount(bill, item.id, edit.amountMinor))
    }

    private fun State.itemQuantity(edit: SplitEdit.ItemQuantity): State {
        val item = item(edit.itemId)
        if (!isQuantity(edit.quantity)) reject("A quantity has to be a number more than zero.")
        // The same number written another way, such as 3.000000 for 3, changes nothing.
        if (edit.quantity.compareTo(item.quantity) == 0) return this
        // Kept as people write it, without trailing zeros: 2.5, and 100 rather than 1E+2.
        val quantity = edit.quantity.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        val price = item.unitPriceMinor
        // BillEditor keeps a line it cannot hold as it was; an edit that does nothing is better rejected.
        if (price != null && BillEditor.lineAmount(quantity, price) == null) reject(TOO_LARGE)
        return withBill(BillEditor.withItemQuantity(bill, item.id, quantity))
    }

    /** The plan with [division] for the whole bill, unless the calculator finds the division wrong, as it does on screen. */
    private fun State.withWhole(division: Division): State {
        val divided = plan.withWholeDivision(division)
        divided.result(bill).problems.firstOrNull()?.let(::reject)
        return copy(plan = divided)
    }

    /** [changed] as the bill, which a single payer pays all of; rejected when its total is less than nothing or more than a bill holds. */
    private fun State.withBill(changed: Bill): State {
        if (changed.totalMinor < 0) reject("That would take the bill total below zero.")
        if (changed.totalMinor > BillCurrency.MAX_AMOUNT_MINOR) reject(TOO_LARGE)
        return copy(bill = changed, plan = plan.withBill(changed))
    }

    /**
     * The plan split item by item, as it is about to change how one item is split. A bill split as a
     * whole starts from an even split by item, as the contract has it: every item shared by everyone.
     * The screen remembers how each item was divided before the bill was split as a whole, but those
     * divisions are not in effect, and neither the model nor the user reading the edits knows them,
     * so they do not come back. The screen's own switch to by item still brings them back.
     */
    private fun State.byItem(): SplitPlan =
        if (plan.mode == SplitMode.BY_ITEM) plan else plan.copy(setup = plan.setup.copy(whole = null, items = emptyMap()))

    private fun State.item(id: String): BillItem = findItem(id) ?: reject("That item is not on the bill.")

    private fun State.participant(id: String): String = participantId(id) ?: reject("That person is not on the bill.")

    /** [values] by the participant each key names. Someone who does not share the bill can only be given nothing. */
    private fun State.parts(values: Map<String, Long>): Map<String, Long> {
        val parts = LinkedHashMap<String, Long>()
        values.forEach { (key, value) ->
            val id = participant(key)
            if (value > 0 && id in plan.setup.notSharing) rejectNotSharing(id)
            if (parts.put(id, value) != null) reject("The same person is named twice.")
        }
        return parts
    }

    private fun State.rejectNotSharing(id: String): Nothing {
        if (id == ME_ID) reject("You do not share the bill.")
        reject("${plan.participant(id)?.name?.ifBlank { null } ?: "Someone"} does not share the bill.")
    }

    /** [basisPoints] of what the items of [bill] add up to, to the nearest minor unit and half to even; null for no valid percentage. */
    private fun chargeAmount(bill: Bill, basisPoints: Long): Long? {
        if (basisPoints !in 1..Division.ByPercent.FULL) return null
        return BigDecimal.valueOf(bill.itemsTotalMinor)
            .multiply(BigDecimal.valueOf(basisPoints))
            .divide(BigDecimal.valueOf(Division.ByPercent.FULL))
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()
    }

    /**
     * Whether [quantity] can be an item's quantity: more than zero, below a million, with at most six
     * decimals. The bounds also keep out a number such as 1E-999999999, which would take far too long to round.
     */
    private fun isQuantity(quantity: BigDecimal): Boolean =
        quantity.signum() > 0 && quantity < MAX_QUANTITY && quantity.stripTrailingZeros().scale() <= MAX_QUANTITY_DECIMALS

    /** A bill and plan part way through a list of edits, with the participant each ref added so far stands for. */
    private data class State(val bill: Bill, val plan: SplitPlan, val refs: Map<String, String> = emptyMap()) {
        fun findItem(id: String): BillItem? = bill.items.firstOrNull { it.id == id }

        /** The participant [id] names: someone added under that ref, or the participant with that id; null for anyone else. */
        fun participantId(id: String): String? = (refs[id] ?: id).takeIf { plan.participant(it) != null }
    }

    /** Why an edit cannot be applied. It never leaves this object: [walk] turns it into a rejection. */
    private class Rejection(val reason: String) : Exception(reason, null, false, false)

    private fun reject(reason: String): Nothing = throw Rejection(reason)

    /** Writes the sentences of [describe] about the bill and plan an edit meets. */
    private class Describer(
        private val state: State,
        private val meName: String,
        /** The names of the people the edits add, by ref, for an edit that names someone whose addition was rejected. */
        private val newPeople: Map<String, String>,
        private val locale: Locale,
    ) {
        fun sentence(edit: SplitEdit): String = text(edit).replaceFirstChar { it.titlecase(locale) }

        private fun text(edit: SplitEdit): String = when (edit) {
            is SplitEdit.ItemSharers -> {
                val people = edit.participantIds.distinct()
                val who = if (people.size == 1) "only ${name(people.single())}" else "shared by ${list(people.map(::name))}"
                "${item(edit.itemId)}: $who${othersByEveryone(edit.itemId)}"
            }

            is SplitEdit.ItemShares -> {
                val shares = parts(edit.shares) { it.toString() }
                "${item(edit.itemId)}: in shares, $shares${othersByEveryone(edit.itemId)}"
            }

            is SplitEdit.SetMode -> when (edit.mode) {
                SplitMode.EQUAL -> "Split the whole bill equally"
                SplitMode.BY_ITEM -> "Split the bill item by item" + if (splitAsWhole) ", every item shared by everyone" else ""
                SplitMode.BY_SHARES -> "Split the whole bill in shares"
                SplitMode.BY_PERCENT -> "Split the whole bill by percentage"
                SplitMode.BY_AMOUNT -> "Split the whole bill by amount"
            }

            is SplitEdit.WholeShares -> "Whole bill in shares: ${parts(edit.shares) { it.toString() }}"

            is SplitEdit.WholePercent -> "Whole bill by percentage: ${parts(edit.basisPoints, ::percentText)}"

            is SplitEdit.WholeAmounts -> "Whole bill by amount: ${parts(edit.amounts, ::money)}"

            is SplitEdit.AddPerson -> "Add ${edit.name.trim().ifEmpty { "someone new" }}"

            is SplitEdit.RemovePerson -> "Take ${name(edit.participantId)} off the bill"

            is SplitEdit.SetSharing -> {
                val you = isYou(edit.participantId)
                val verb = when {
                    edit.sharing -> if (you) "share" else "shares"
                    else -> if (you) "do not share" else "does not share"
                }
                "${name(edit.participantId)} $verb the bill"
            }

            is SplitEdit.AddCharge -> charge(edit)

            is SplitEdit.SetPayer -> "${name(edit.participantId)} paid the whole bill"

            is SplitEdit.ItemAmount -> "${item(edit.itemId)} comes to ${money(edit.amountMinor)}"

            is SplitEdit.ItemQuantity -> {
                val quantity = edit.quantity
                val price = state.findItem(edit.itemId)?.unitPriceMinor
                val amount = price?.takeIf { isQuantity(quantity) }?.let { BillEditor.lineAmount(quantity, it) }
                // A number too odd to be a quantity is shown in scientific notation, which stays short.
                val shown = if (isQuantity(quantity)) quantity.stripTrailingZeros().toPlainString() else quantity.toString()
                "${item(edit.itemId)}: quantity $shown" + if (amount == null) "" else " (${money(amount)})"
            }
        }

        /** "Add a 10% tip (₫14,000)", "Add an ₫8,000 service charge". */
        private fun charge(edit: SplitEdit.AddCharge): String {
            val label = edit.label.trim()
            val standard = BillEditor.defaultLabel(edit.kind)
            val noun = when {
                label.isNotEmpty() && !label.equals(standard, ignoreCase = true) -> label
                edit.kind == ChargeKind.OTHER -> "charge"
                else -> standard.lowercase(Locale.ROOT)
            }
            val amount = edit.amountMinor
            val percent = edit.percentBasisPoints
            return when {
                percent != null && amount == null -> {
                    val shown = percentText(percent)
                    val added = chargeAmount(state.bill, percent)?.let { " (${money(BillEditor.signedCharge(edit.kind, it))})" }.orEmpty()
                    "Add ${article(shown)} $shown $noun$added"
                }

                amount != null && percent == null -> money(amount).let { "Add ${article(it)} $it $noun" }

                else -> "Add $noun"
            }
        }

        private fun item(id: String): String {
            val item = state.findItem(id) ?: return "an item that is not on the bill"
            return item.name.trim().ifEmpty { "an item" }
        }

        /** Whether the bill is split as a whole now, which an edit of one item turns into an even split by item. */
        private val splitAsWhole: Boolean get() = state.plan.setup.whole != null

        /** What becomes of the bill's other items when an edit of [itemId] splits it item by item: each is shared by everyone. */
        private fun othersByEveryone(itemId: String): String {
            val others = state.findItem(itemId) != null && state.bill.items.any { it.id != itemId }
            return if (splitAsWhole && others) ", and every other item by everyone" else ""
        }

        private fun name(id: String): String {
            val participant = state.participantId(id)?.let(state.plan::participant)
            return when {
                participant == null -> newPeople[id]?.trim()?.ifEmpty { null } ?: "someone who is not on the bill"
                participant.isMe -> meName.trim().ifEmpty { "you" }
                else -> participant.name.trim().ifEmpty { "someone" }
            }
        }

        /** Whether [id] is the user called "you", for the verbs that follow. */
        private fun isYou(id: String): Boolean = state.participantId(id) == ME_ID && meName.isBlank()

        private fun parts(values: Map<String, Long>, shown: (Long) -> String): String =
            list(values.map { (id, value) -> "${name(id)} ${shown(value)}" })

        private fun money(minor: Long): String = state.bill.currency.format(minor, locale)
    }

    /** "Dana", "Dana and Minh", "Dana, Minh and Lan". */
    private fun list(parts: List<String>): String = when (parts.size) {
        0 -> "no one"
        1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    /**
     * "a" or "an" before a number, as the number is read out: an 8% tip, an 11% tip, an 18% tip, an
     * 80,000 dong tip, but a 10% tip.
     */
    private fun article(number: String): String {
        val digits = number.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
        return if (digits.startsWith("8") || digits == "11" || digits == "18") "an" else "a"
    }

    private val REF = Regex("new[1-9]")
    private val MAX_QUANTITY = BigDecimal(1_000_000)
    private const val MAX_QUANTITY_DECIMALS = 6
    private const val NOBODY = "No one was named to share it."
    private const val TOO_LARGE = "That is more than a bill can hold."
}
