package com.dantech.splitvibe.domain

import com.dantech.splitvibe.data.contacts.Contact

/** Id of the app's own user (the person who paid the bill) among the people sharing it. */
const val ME_ID = "me"

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
 * What the instruction said about one item. The people the [rule] names are [people] plus the user
 * when [includesUser] is true.
 */
data class ItemAssignmentHint(
    val itemIndex: Int,
    val rule: ShareRule,
    val people: List<String>,
    val includesUser: Boolean,
)

/**
 * Something the app could not work out from the instruction and the user should finish by hand.
 * It stops being shown once the user has dealt with it.
 */
data class PlanNotice(
    val text: String,
    /** Settled once one of these contacts shares the bill. */
    val settledByContacts: Set<String> = emptySet(),
    /** Settled once the user has chosen who shares this item. */
    val settledByItem: Int? = null,
)

/** Who shares the bill and who shares which item. The user can change all of it on screen. */
data class SplitPlan(
    val includeMe: Boolean = true,
    val selectedContactIds: List<String> = emptyList(),
    /** Item index to the only people ([ME_ID] or contact ids) who share it. */
    val assignments: Map<Int, Set<String>> = emptyMap(),
    /** Item index to people who do not share it. Everyone else does, including people added later. */
    val exclusions: Map<Int, Set<String>> = emptyMap(),
    val notices: List<PlanNotice> = emptyList(),
    /** Head count stated in the instruction, including the user. */
    val expectedHeadCount: Int? = null,
) {
    /** Ids of everyone sharing the bill, payer first so they absorb any indivisible unit. */
    val participantIds: List<String>
        get() = buildList {
            if (includeMe) add(ME_ID)
            addAll(selectedContactIds)
        }

    /** Who shares each item that is not simply shared by everyone. This is what gets calculated. */
    fun sharersByItem(): Map<Int, Set<String>> {
        val everyone = participantIds.toSet()
        val fromExclusions = exclusions
            .mapValues { (_, excluded) -> everyone - excluded }
            .filterValues { it.isNotEmpty() && it != everyone }
        // A choice the user (or an "only" instruction) made explicitly wins over an exclusion.
        return fromExclusions + assignments
    }

    /** The notices that still need the user's attention. */
    fun openNotices(): List<String> = buildList {
        notices
            .filter { notice -> notice.settledByContacts.none { it in selectedContactIds } }
            .filter { notice -> notice.settledByItem == null || notice.settledByItem !in assignments }
            .forEach { add(it.text) }

        val missing = (expectedHeadCount ?: 0) - participantIds.size
        if (missing > 0) {
            val people = if (missing == 1) "1 more person" else "$missing more people"
            add("Your instruction mentions $expectedHeadCount people. Pick $people below.")
        }
    }

    fun withMeToggled(): SplitPlan = copy(includeMe = !includeMe).withoutAbsentPeople()

    fun withContactToggled(contactId: String): SplitPlan {
        val selected = if (contactId in selectedContactIds) selectedContactIds - contactId else selectedContactIds + contactId
        return copy(selectedContactIds = selected).withoutAbsentPeople()
    }

    /** Sets who shares one item. Choosing everyone (or nobody) puts the item back to "shared by all". */
    fun withItemSharers(itemIndex: Int, sharerIds: Set<String>): SplitPlan {
        val everyone = participantIds.toSet()
        val chosen = sharerIds intersect everyone
        val explicit = if (chosen.isEmpty() || chosen == everyone) assignments - itemIndex else assignments + (itemIndex to chosen)
        return copy(assignments = explicit, exclusions = exclusions - itemIndex)
    }

    /** Drops people who no longer share the bill from every item, so no share goes to someone absent. */
    private fun withoutAbsentPeople(): SplitPlan {
        val present = participantIds.toSet()
        return copy(assignments = assignments.mapValues { it.value intersect present }.filterValues { it.isNotEmpty() })
    }
}

/**
 * Builds the starting [SplitPlan] from what the user typed.
 *
 * Names are matched against the contact list here, on the device. A name that [NameMatcher] cannot
 * pin to one contact is never guessed: it becomes a notice and the user picks the person.
 */
object SplitPlanner {

    fun fromHint(hint: SplitHint, bill: Bill, contacts: List<Contact>): SplitPlan {
        val notices = mutableListOf<PlanNotice>()
        val resolved = mutableMapOf<String, Contact?>()

        fun resolve(name: String): Contact? {
            val key = NameMatcher.normalize(name)
            if (key in resolved) return resolved[key]
            val contact = when (val match = NameMatcher.match(name, contacts)) {
                is NameMatcher.Match.Found -> match.contact
                is NameMatcher.Match.Ambiguous -> {
                    val options = match.candidates.joinToString(" or ") { it.name }
                    notices += PlanNotice(
                        text = "\"$name\" could be $options. Pick the right person below.",
                        settledByContacts = match.candidates.map { it.id }.toSet(),
                    )
                    null
                }
                NameMatcher.Match.None -> {
                    notices += PlanNotice("\"$name\" is not in your contacts. Pick them below.")
                    null
                }
            }
            resolved[key] = contact
            return contact
        }

        val selected = linkedSetOf<String>()
        hint.people.forEach { name -> resolve(name)?.let { selected += it.id } }

        val assignments = mutableMapOf<Int, Set<String>>()
        val exclusions = mutableMapOf<Int, Set<String>>()
        for (assignment in hint.itemAssignments) {
            val item = bill.items.getOrNull(assignment.itemIndex) ?: continue
            val people = assignment.people.map(::resolve)
            if (people.any { it == null }) {
                // Applying the rule with only the people we could identify would charge the wrong ones.
                notices += PlanNotice(
                    text = "\"${item.name}\" was not assigned automatically. Tap it to choose who shares it.",
                    settledByItem = assignment.itemIndex,
                )
                continue
            }
            val contactIds = people.map { it!!.id }
            selected += contactIds // someone named for an item belongs to the group
            val named = buildSet {
                if (assignment.includesUser && hint.userIncluded) add(ME_ID)
                addAll(contactIds)
            }
            if (named.isEmpty()) continue
            when (assignment.rule) {
                ShareRule.ONLY -> assignments[assignment.itemIndex] = assignments[assignment.itemIndex].orEmpty() + named
                ShareRule.ALL_EXCEPT -> exclusions[assignment.itemIndex] = exclusions[assignment.itemIndex].orEmpty() + named
            }
        }

        return SplitPlan(
            includeMe = hint.userIncluded,
            selectedContactIds = selected.toList(),
            assignments = assignments,
            exclusions = exclusions,
            notices = notices.distinct(),
            expectedHeadCount = hint.peopleCount,
        )
    }
}
