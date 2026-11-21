package com.dantech.splitvibe.ui.groups

import androidx.annotation.StringRes
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.designsystem.components.SvHeroKind
import kotlin.math.absoluteValue

/**
 * Where the user stands in a group as a whole, from their own balance in it: above zero what the
 * group owes them, below zero what they owe it, and at zero settled up. The group's screen and the
 * home's tile of the group both say it in these words. There is none where the group's numbers are
 * beyond adding up, as nothing is known of the balance then.
 */
internal data class GroupStanding(val balanceMinor: Long) {

    /** The words that say where the user stands. */
    @get:StringRes
    val label: Int
        get() = when {
            balanceMinor > 0 -> R.string.home_you_get_back
            balanceMinor < 0 -> R.string.home_you_owe
            else -> R.string.home_settled_up
        }

    /**
     * The colour of the group's block: lime when the user gets money back, the colour of the
     * floating bar when they owe it, and violet when they are settled up. The words say the same.
     */
    val kind: SvHeroKind
        get() = when {
            balanceMinor > 0 -> SvHeroKind.Accent
            balanceMinor < 0 -> SvHeroKind.Bar
            else -> SvHeroKind.Brand
        }

    /** The balance without its sign, which [label] says. */
    val amountMinor: Long get() = balanceMinor.absoluteValue

    companion object {
        /** Where the user stands in [state]; null when its balances cannot be added up. */
        fun of(state: GroupState): GroupStanding? = state.balances?.get(state.myMemberId)?.let(::GroupStanding)
    }
}
