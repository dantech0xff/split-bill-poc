package com.dantech.splitvibe

import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.GroupLink
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Payments
import com.dantech.splitvibe.domain.SplitPlan
import java.math.BigDecimal

/**
 * The user's bills as the phone keeps them, for the statistics, the exports and their screens. A
 * bill is split equally between the user and the people named on it, and the user paid it, unless
 * a bill says otherwise.
 */
object SpendingFixtures {

    val vnd = BillCurrency("VND", 0)
    val usd = BillCurrency("USD", 2)
    val thb = BillCurrency("THB", 2)
    val eur = BillCurrency("EUR", 2)

    /** A finished bill of one item of [totalMinor], split equally between the user, [people] and [strangers], who joined from its link. */
    fun finished(
        id: String,
        date: String?,
        merchant: String?,
        category: BillCategory,
        totalMinor: Long,
        people: List<String>,
        currency: BillCurrency = vnd,
        group: String? = null,
        rate: ExchangeRate? = null,
        strangers: List<String> = emptyList(),
        userShares: Boolean = true,
    ): SavedBill = finished(
        id = id,
        bill = Bill(merchant, date, currency, listOf(BillItem("i1", "Bill", BigDecimal.ONE, null, totalMinor)), emptyList(), null)
            .copy(category = category),
        people = people,
        group = group,
        rate = rate,
        strangers = strangers,
        userShares = userShares,
    )

    /** [bill] finished, split equally between the user and [people], with [strangers] who joined from its link. */
    fun finished(
        id: String,
        bill: Bill,
        people: List<String>,
        group: String? = null,
        rate: ExchangeRate? = null,
        strangers: List<String> = emptyList(),
        userShares: Boolean = true,
        payer: String? = null,
    ): SavedBill {
        var plan = SplitPlan()
        (people + strangers).forEach { plan = plan.withParticipantAdded(it) }
        if (!userShares) plan = plan.withMeSharingToggled()
        val payerId = payer?.let { name -> plan.participants.first { it.name == name }.id } ?: ME_ID
        plan = plan.withPayments(Payments.single(payerId, bill.totalMinor))
        val added = strangers.associate { name -> "claim-$name" to plan.participants.first { it.name == name }.id }
        return SavedBill(
            id = id,
            status = BillStatus.FINAL,
            createdAt = 0,
            updatedAt = 0,
            bill = bill,
            plan = plan,
            exchangeRate = rate,
            group = group?.let { GroupLink(groupId = "group-$it", groupName = it, revision = 1, memberOf = emptyMap()) },
            share = if (added.isEmpty()) null else ShareLink(shareId = "share-$id", url = "https://links.invalid/s/#token", added = added),
        ).withSharesWorkedOut()
    }

    // Before the bills below, which an object's properties are set up in the order of.
    private val MINH_AND_LAN = listOf("Minh", "Lan")

    fun usdToVnd(rate: String) = ExchangeRate("USD", "VND", BigDecimal(rate), date = "2026-10-20", source = "Frankfurter")

    /**
     * Eleven finished bills from November 2025 to November 2026, the latest saved first, and seen on
     * 16 November 2026: the rent and utilities of the flat the user shares with Minh and Khoa, a trip
     * to Da Lat with Lan and with An, who joined from the bills' links, and bills in no group, two of
     * them without a usable rate to dong and two without a usable date.
     */
    val year: List<SavedBill> = listOf(
        // The user paid for Minh and Lan's dinner and had none of it.
        finished("b10", "2026-11-15", "Bún chả", BillCategory.FOOD_AND_DRINK, 600_000, listOf("Minh", "Lan"), userShares = false),
        finished("b1", "2026-11-12", "Phở Thìn", BillCategory.FOOD_AND_DRINK, 300_000, listOf("Minh", "Lan")),
        // €20.00 with a rate to dollars, not to dong.
        finished(
            "b11",
            "2026-11-10",
            "Musée",
            BillCategory.ENTERTAINMENT,
            2_000,
            listOf("Lan"),
            currency = eur,
            rate = ExchangeRate("EUR", "USD", BigDecimal("1.08")),
        ),
        // ฿412.50 without a rate.
        finished("b5", "2026-11-03", "Tops Market", BillCategory.GROCERIES, 41_250, emptyList(), currency = thb),
        finished("b2", "2026-11-01", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh", "Khoa"), group = FLAT),
        // $30.00 at 25,400 is 762,000; each $15.00 is 381,000.
        finished("b4", "2026-10-20", "Bowling", BillCategory.ENTERTAINMENT, 3_000, listOf("Lan"), currency = usd, rate = usdToVnd("25400")),
        finished("b3", "2026-10-01", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh", "Khoa"), group = FLAT),
        finished("b6", "2025-12-05", "Xe khách", BillCategory.TRANSPORT, 800_000, MINH_AND_LAN, group = TRIP, strangers = listOf("An")),
        // An again, typed differently on this bill's link, is the same person.
        finished("b7", "2025-11-30", "Dalat Palace", BillCategory.LODGING, 2_400_000, MINH_AND_LAN, group = TRIP, strangers = listOf("an")),
        finished("b8", null, "Uniqlo", BillCategory.SHOPPING, 500_000, listOf("Lan")),
        finished("b9", "13/11/2026", "EVN", BillCategory.UTILITIES, 1_200_000, listOf("Minh", "Khoa"), group = FLAT),
    )

    const val FLAT = "Flat 3B"
    const val TRIP = "Da Lat trip"
}
