package com.dantech.splitvibe.domain

import kotlinx.serialization.Serializable

/** What the user's instruction said about who shares the bill, as understood by the model. */
data class SplitHint(
    /** Names of other people exactly as the user wrote them, e.g. "Minh". */
    val people: List<String> = emptyList(),
    /** False only when the user said they are not taking part. */
    val userIncluded: Boolean = true,
    /** Total head count including the user, when the instruction states it. */
    val peopleCount: Int? = null,
    val itemAssignments: List<ItemAssignmentHint> = emptyList(),
)

enum class ShareRule {
    /** "Minh had the beers": only the named people share the item. */
    ONLY,

    /** "I didn't drink": everyone shares the item except the named people. */
    ALL_EXCEPT,
}

/**
 * What the instruction said about one item of the bill. The people the [rule] names are [people]
 * plus the user when [includesUser] is true.
 */
data class ItemAssignmentHint(val itemId: String, val rule: ShareRule, val people: List<String>, val includesUser: Boolean)

/**
 * Who takes part in a bill, how it is split and who paid. Every change returns a new plan; the
 * shares follow from it with [result].
 */
@Serializable
data class SplitPlan(
    /** Everyone on the bill, the app's user first, so they absorb any indivisible unit. */
    val participants: List<Participant> = listOf(Participant.ME),
    val setup: SplitSetup = SplitSetup(),
    /** Who paid how much. A single payer always paid the whole bill. */
    val payments: List<Payment> = emptyList(),
    /** Head count stated in the instruction, including the user. */
    val expectedHeadCount: Int? = null,
) {
    val participantIds: List<String> get() = participants.map { it.id }

    /** The participants who share the bill, in display order. */
    val sharingIds: List<String> get() = participantIds.filter { it !in setup.notSharing }

    val mode: SplitMode get() = setup.mode

    fun participant(id: String): Participant? = participants.firstOrNull { it.id == id }

    /** The shares of [bill] under this plan. */
    fun result(bill: Bill): SplitResult = SplitCalculator.split(bill, participantIds, setup)

    /** Everything that keeps this plan from a finished split of [bill]: the split, then the payments. */
    fun problems(bill: Bill): List<String> =
        result(bill).problems + listOfNotNull(Payments.problem(payments, bill.totalMinor, bill.currency))

    /** What the instruction asked for that the user still has to do. */
    fun openNotices(): List<String> {
        val missing = (expectedHeadCount ?: 0) - participants.size
        if (missing <= 0) return emptyList()
        val people = if (missing == 1) "1 more person" else "$missing more people"
        return listOf("Your instruction mentions $expectedHeadCount people. Add $people below.")
    }

    fun withParticipantAdded(name: String, personId: String? = null): SplitPlan {
        val added = Participant(Participant.nextId(participants), name.trim(), personId)
        // Someone added to an exact division counts for nothing until the user gives them a part,
        // so the parts that already add up keep adding up; a share of one is the natural start.
        val whole = when (val current = setup.whole) {
            is Division.ByShares -> current.copy(shares = current.shares + (added.id to 1L))
            else -> current
        }
        return copy(participants = participants + added, setup = setup.copy(whole = whole))
    }

    fun withParticipantRenamed(id: String, name: String): SplitPlan =
        copy(participants = participants.map { if (it.id == id) it.copy(name = name.trim()) else it })

    /** Takes someone off the bill. The app's user stays; they can only stop sharing it. */
    fun withParticipantRemoved(id: String, bill: Bill): SplitPlan {
        if (id == ME_ID || participants.none { it.id == id }) return this
        val remaining = participants.filter { it.id != id }
        val present = remaining.map { it.id }.toSet()
        return copy(
            participants = remaining,
            setup = setup.withoutPerson(id),
            payments = Payments.withoutAbsent(payments, present, ME_ID, bill.totalMinor),
        )
    }

    /** The app's user shares the bill, or only takes part in it, for example as the one who paid. */
    fun withMeSharingToggled(): SplitPlan = withSharing(ME_ID, ME_ID in setup.notSharing)

    /** Whether [participantId] shares the bill, or only takes part in it, for example as the one who paid. */
    fun withSharing(participantId: String, sharing: Boolean): SplitPlan {
        val notSharing = if (sharing) setup.notSharing - participantId else setup.notSharing + participantId
        return copy(setup = setup.copy(notSharing = notSharing))
    }

    /** Switches how the bill is divided, starting the new way from an even division between the people sharing it. */
    fun withMode(mode: SplitMode, bill: Bill): SplitPlan {
        if (mode == this.mode) return this
        val sharing = sharingIds
        val whole: Division? = when (mode) {
            SplitMode.EQUAL -> Division.Equally()
            SplitMode.BY_ITEM -> null
            SplitMode.BY_SHARES -> Division.ByShares(sharing.associateWith { 1L })
            SplitMode.BY_PERCENT -> Division.ByPercent(evenly(Division.ByPercent.FULL, sharing))
            SplitMode.BY_AMOUNT -> Division.ByAmount(evenly(bill.totalMinor, sharing))
        }
        return copy(setup = setup.copy(whole = whole))
    }

    /** Sets one person's value in the whole-bill division: shares, hundredths of a percent, or minor units. */
    fun withWholeValue(participantId: String, value: Long): SplitPlan {
        val whole = when (val current = setup.whole) {
            is Division.ByShares -> current.copy(shares = current.shares + (participantId to value))
            is Division.ByPercent -> current.copy(basisPoints = current.basisPoints + (participantId to value))
            is Division.ByAmount -> current.copy(amounts = current.amounts + (participantId to value))
            else -> return this
        }
        return copy(setup = setup.copy(whole = whole))
    }

    /** Divides the whole bill by [division], whatever way it was split before. */
    fun withWholeDivision(division: Division): SplitPlan = copy(setup = setup.copy(whole = division))

    /** How one item is divided; null shares it equally by everyone again. */
    fun withItemDivision(itemId: String, division: Division?): SplitPlan {
        val items = if (division == null) setup.items - itemId else setup.items + (itemId to division)
        return copy(setup = setup.copy(items = items))
    }

    /** Sets who shares one item equally. Choosing everyone, or nobody, shares it with everyone again. */
    fun withItemSharers(itemId: String, sharerIds: Set<String>): SplitPlan {
        val everyone = sharingIds.toSet()
        val chosen = sharerIds intersect everyone
        return withItemDivision(itemId, if (chosen.isEmpty() || chosen == everyone) null else Division.Equally(among = chosen))
    }

    fun withPayments(payments: List<Payment>): SplitPlan = copy(payments = payments)

    /**
     * Keeps the exact amounts in step when the bill moves from [from] to [to], the way the bill's
     * own amounts move: as written, so 30.00 dollars become 30.00 of the new currency.
     */
    fun withCurrency(from: BillCurrency, to: BillCurrency): SplitPlan {
        if (from == to) return this
        fun convert(minor: Long): Long = to.toMinor(from.toMajor(minor))
        fun Division.converted(): Division = if (this is Division.ByAmount) {
            copy(
                amounts = amounts.mapValues { (_, value) ->
                    convert(value)
                },
            )
        } else {
            this
        }
        return copy(
            setup = setup.copy(whole = setup.whole?.converted(), items = setup.items.mapValues { (_, division) -> division.converted() }),
            payments = payments.map { it.copy(amountMinor = convert(it.amountMinor)) },
        )
    }

    /** Keeps the plan in step after the bill was edited: a single payer pays the new total, and removed items lose their division. */
    fun withBill(bill: Bill): SplitPlan {
        val itemIds = bill.items.map { it.id }.toSet()
        return copy(
            setup = setup.copy(items = setup.items.filterKeys { it in itemIds }),
            payments = Payments.followTotal(payments.ifEmpty { Payments.single(ME_ID, bill.totalMinor) }, bill.totalMinor),
        )
    }

    private fun evenly(amount: Long, people: List<String>): Map<String, Long> {
        if (people.isEmpty()) return emptyMap()
        return people.zip(SplitCalculator.divideEqually(amount, people.size)).toMap()
    }
}

/** The setup without one person, who no longer shares anything or counts in any division. */
private fun SplitSetup.withoutPerson(id: String): SplitSetup {
    fun Division.without(): Division = when (this) {
        is Division.Equally -> copy(among = among - id, except = except - id)
        is Division.ByShares -> copy(shares = shares - id)
        is Division.ByPercent -> copy(basisPoints = basisPoints - id)
        is Division.ByAmount -> copy(amounts = amounts - id)
    }
    return copy(
        whole = whole?.without(),
        items = items.mapValues { (_, division) -> division.without() }
            // An item that was only this person's goes back to being shared by everyone.
            .filterValues { !(it is Division.Equally && it.among.isEmpty() && it.except.isEmpty()) },
        notSharing = notSharing - id,
    )
}

/**
 * Builds the starting [SplitPlan] from what the user typed.
 *
 * Every name in the instruction becomes a participant. A name that is exactly one the user has
 * split with before ([people], most recent first) is that person again; any other name is someone
 * new, named as typed, whom the user can rename on screen.
 */
object SplitPlanner {

    fun fromHint(hint: SplitHint, bill: Bill, people: List<Person>): SplitPlan {
        var plan = SplitPlan()
        val idsByName = mutableMapOf<String, String>()

        fun participantFor(name: String): String {
            val key = NameMatcher.normalize(name)
            idsByName[key]?.let { return it }
            val person = NameMatcher.find(name, people)
            plan = plan.withParticipantAdded(person?.name ?: name.trim(), person?.id)
            val id = plan.participants.last().id
            idsByName[key] = id
            return id
        }

        hint.people.filter { it.isNotBlank() }.forEach { participantFor(it) }

        val divisions = mutableMapOf<String, Division.Equally>()
        for (assignment in hint.itemAssignments) {
            // An item the bill no longer lists, such as a negative line that became a discount, has no rule.
            val item = bill.items.firstOrNull { it.id == assignment.itemId } ?: continue
            val named = buildSet {
                if (assignment.includesUser && hint.userIncluded) add(ME_ID)
                assignment.people.filter { it.isNotBlank() }.forEach { add(participantFor(it)) }
            }
            if (named.isEmpty()) continue
            val current = divisions[item.id] ?: Division.Equally()
            divisions[item.id] = when (assignment.rule) {
                ShareRule.ONLY -> current.copy(among = current.among + named)
                ShareRule.ALL_EXCEPT -> current.copy(except = current.except + named)
            }
        }

        val notSharing = if (hint.userIncluded) emptySet() else setOf(ME_ID)
        val setup = if (divisions.isEmpty()) {
            SplitSetup(whole = Division.Equally(), notSharing = notSharing)
        } else {
            SplitSetup(whole = null, items = divisions, notSharing = notSharing)
        }
        return plan.copy(
            setup = setup,
            payments = Payments.single(ME_ID, bill.totalMinor),
            expectedHeadCount = hint.peopleCount,
        )
    }
}
