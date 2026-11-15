package com.dantech.splitvibe.domain

/**
 * Turns the items people picked on a bill's link into a split by item.
 *
 * The bill's owner matches each person who picked items to a participant of the bill. An item that
 * several of them picked is shared equally between them. An item nobody picked gets no entry, so it
 * is shared by everyone, as a split by item does for any item without one, and the owner can still
 * change it. The charges then follow what each person had.
 */
object ClaimSplit {

    /** The items with [itemIds] that the participant [participantId] picked, through one claim or several. */
    data class Pick(val participantId: String, val itemIds: Set<String>)

    /**
     * The split of [bill] by what [picks] say each participant had. Ids of items that are no longer on
     * the bill are left out. Someone who picked an item shares the bill, even if [notSharing] says
     * they did not; the others in [notSharing] stay out.
     */
    fun setup(bill: Bill, picks: List<Pick>, notSharing: Set<String> = emptySet()): SplitSetup {
        val items = bill.items.mapNotNull { item ->
            val pickers = picks.filter { item.id in it.itemIds }.map { it.participantId }.toSet()
            if (pickers.isEmpty()) null else item.id to Division.Equally(among = pickers)
        }.toMap()
        val picking = items.values.flatMap { it.among }.toSet()
        return SplitSetup(whole = null, items = items, notSharing = notSharing - picking)
    }
}
