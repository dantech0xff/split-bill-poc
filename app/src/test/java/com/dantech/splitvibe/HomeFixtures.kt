package com.dantech.splitvibe

import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.groups.CurrencyDto
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.home.GroupTile
import com.dantech.splitvibe.ui.home.Money
import com.dantech.splitvibe.ui.home.MonthSpending
import com.dantech.splitvibe.ui.requests.RequestTotals
import java.time.LocalDate
import java.time.YearMonth

/**
 * The home as the approved design draws it, with its names and amounts: what friends owe the user
 * and what the user owes, this month's spending, the group kept last and the bills split last.
 */
object HomeFixtures {

    /** The day the home is drawn on, so that its bills, all of this year, have their dates written short. */
    val today: LocalDate = LocalDate.of(2026, 11, 20)

    /** The name the user goes by, whose first letter the account button shows. */
    const val MY_NAME = "Dana"

    /**
     * Minh owes ₫501,671 and Lan ₫150,000, which she says she paid; the user owes ₫150,000. Two
     * requests wait for the user: the one to pay, and Lan's to confirm.
     */
    val owed: RequestTotals = loaded(asked("r1", 501_671, "Minh"), asked("r2", 150_000, "Lan", reportedPaid = true), toPay("r3", 150_000))

    /** Only what the user owes. */
    val owing: RequestTotals = loaded(toPay("r3", 150_000))

    /** The user's share of the six finished bills of November 2026. */
    val month = MonthSpending(YearMonth.of(2026, 11), Money(BillCurrency("VND", 0), 3_823_038), bills = 6)

    /** The trip kept in baht, where the user gets back 35.43. */
    val group: GroupTile = checkNotNull(GroupTile.of(ProxyCache.json.decodeFromString(GroupState.serializer(), GroupFixtures.groupState())))

    /** The three bills split last, the newest first. */
    val bills: List<BillListing> = listOf(
        finished("bill-1", "Quán Ốc Ba Miền", "2026-11-13", BillCurrency("VND", 0), 902_664, "You, Minh, Lan", "food_and_drink"),
        finished("bill-2", "Phở Thìn", "2026-11-12", BillCurrency("VND", 0), 310_000, "You, Minh, Lan", "food_and_drink"),
        finished("bill-3", "Grab", "2026-11-05", BillCurrency("THB", 2), 18_700, "You, Lan", "transport"),
    )

    private fun finished(
        id: String,
        merchant: String,
        date: String,
        currency: BillCurrency,
        totalMinor: Long,
        people: String,
        category: String,
    ) = BillListing(id, BillStatus.FINAL.name, 0, merchant, date, currency.code, currency.fractionDigits, totalMinor, people, category)

    private fun loaded(vararg requests: RequestView) = RequestTotals.of(signedIn = true, requests = requests.toList())

    /** A request the user made, for [payer] to pay. */
    private fun asked(id: String, amountMinor: Long, payer: String, reportedPaid: Boolean = false) =
        request(id, RequestView.CREDITOR, amountMinor, payer = payer, creditor = MY_NAME, reportedPaid = reportedPaid)

    /** A request sent to the user, which Dana made, as the approved design names her. */
    private fun toPay(id: String, amountMinor: Long) = request(id, RequestView.DEBTOR, amountMinor, payer = MY_NAME, creditor = "Dana")

    private fun request(id: String, role: String, amountMinor: Long, payer: String, creditor: String, reportedPaid: Boolean = false) =
        RequestView(
            id = id,
            role = role,
            amountMinor = amountMinor,
            currency = CurrencyDto("VND", 0),
            payerName = payer,
            creditorName = creditor,
            createdAt = 1_763_190_000_000,
            reportedPaidAt = if (reportedPaid) 1_763_200_000_000 else null,
        )
}
