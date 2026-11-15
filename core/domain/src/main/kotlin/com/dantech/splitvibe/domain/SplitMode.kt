package com.dantech.splitvibe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The ways a bill can be divided, as the person chooses on screen. */
enum class SplitMode {
    /** Everyone sharing the bill pays the same part of the total. */
    EQUAL,

    /** Each item is divided between the people who had it; charges follow what each person had. */
    BY_ITEM,

    /** The total is divided in proportion to whole-number shares, such as 2 : 1 : 1. */
    BY_SHARES,

    /** The total is divided by percentages that add up to 100%. */
    BY_PERCENT,

    /** Each person pays an exact amount, and the amounts add up to the total. */
    BY_AMOUNT,
}

/** How one amount, the whole bill or one item, is divided between participants. */
@Serializable
sealed interface Division {

    /**
     * In equal parts between [among] (everyone sharing the bill when it is empty) except the
     * people in [except]. "Everyone but Minh" stays true for people added later.
     */
    @Serializable
    @SerialName("equal")
    data class Equally(val among: Set<String> = emptySet(), val except: Set<String> = emptySet()) : Division

    /** In proportion to whole-number shares. Someone without a share pays nothing. */
    @Serializable
    @SerialName("shares")
    data class ByShares(val shares: Map<String, Long>) : Division

    /** By percentages, in hundredths of a percent ([FULL] is 100%), which must add up to 100%. */
    @Serializable
    @SerialName("percent")
    data class ByPercent(val basisPoints: Map<String, Long>) : Division {
        companion object {
            const val FULL = 10_000L
        }
    }

    /** By exact amounts in minor units, which must add up to the amount divided. */
    @Serializable
    @SerialName("amount")
    data class ByAmount(val amounts: Map<String, Long>) : Division
}

/**
 * How a bill is split. With [whole] set, the whole bill is divided that way; without it, each item
 * is divided by its entry in [items] (shared equally by everyone when it has none) and the charges
 * follow what each person had. [notSharing] are participants who take part without sharing the bill,
 * such as someone who paid but did not eat.
 */
@Serializable
data class SplitSetup(
    val whole: Division? = Division.Equally(),
    val items: Map<String, Division> = emptyMap(),
    val notSharing: Set<String> = emptySet(),
) {
    val mode: SplitMode
        get() = when (whole) {
            null -> SplitMode.BY_ITEM
            is Division.Equally -> SplitMode.EQUAL
            is Division.ByShares -> SplitMode.BY_SHARES
            is Division.ByPercent -> SplitMode.BY_PERCENT
            is Division.ByAmount -> SplitMode.BY_AMOUNT
        }
}
