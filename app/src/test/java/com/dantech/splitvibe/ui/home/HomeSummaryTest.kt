package com.dantech.splitvibe.ui.home

import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.HomeFixtures
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.groups.CurrencyDto
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.SettlementDto
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.requests.RequestTotals
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the home shows above its recent bills, for each state of the requests and of the bills on
 * the phone. Nothing known is not nothing owed: requests not fetched yet, nobody signed in and a list
 * fetched with nothing open are three cases, and none of them shows the user owing or owed a zero.
 */
class HomeSummaryTest {

    @Test
    fun `signed out, a phone with bills shows this month's spending`() {
        val summary = HomeSummary.of(RequestTotals.SignedOut, BILLS, NOVEMBER, LOCAL)

        assertEquals(HomeSummary(main = HomeBlock.ThisMonth(NOVEMBER), month = null), summary)
    }

    @Test
    fun `requests not fetched yet show as no request at all, and the block changes once they come`() {
        val before = HomeSummary.of(RequestTotals.Unknown, BILLS, NOVEMBER, LOCAL)
        assertEquals(HomeSummary(main = HomeBlock.ThisMonth(NOVEMBER), month = null), before)

        val after = HomeSummary.of(loaded(asked(150_000, "Minh")), BILLS, NOVEMBER, LOCAL)
        assertEquals(HomeBlock.FriendsOweYou(owed = open(dong(150_000), count = 1, "Minh"), youOwe = null), after.main)
    }

    @Test
    fun `a list fetched with no open request shows this month's spending, not a zero owed`() {
        val requests = loaded(asked(70_000, "Minh", confirmed = true), owed(30_000, "Dana", reported = true))
        val summary = HomeSummary.of(requests, BILLS, NOVEMBER, LOCAL)

        assertEquals(HomeSummary(main = HomeBlock.ThisMonth(NOVEMBER), month = null), summary)
    }

    @Test
    fun `what friends owe leads in the phone's currency, with who owes it, how many requests and what the user owes`() {
        val requests = loaded(
            asked(501_671, "Minh"),
            asked(3_543, "Lan", currency = BAHT),
            asked(150_000, "Lan", reported = true),
            owed(150_000, "Dana"),
        )

        val summary = HomeSummary.of(requests, BILLS, NOVEMBER, LOCAL)

        val owed = OpenRequests(main = dong(651_671), others = listOf(baht(3_543)), count = 3, people = listOf("Minh", "Lan"))
        assertEquals(HomeBlock.FriendsOweYou(owed = owed, youOwe = open(dong(150_000), count = 1, "Dana")), summary.main)
        // This month's spending goes to its tile, below the block.
        assertEquals(NOVEMBER, summary.month)
    }

    @Test
    fun `when nobody owes the user, what the user owes is the main figure`() {
        val requests = loaded(owed(150_000, "Dana"), owed(2_000, "Bao", currency = BAHT), asked(90_000, "Minh", confirmed = true))

        val summary = HomeSummary.of(requests, BILLS, NOVEMBER, LOCAL)

        val owing = OpenRequests(main = dong(150_000), others = listOf(baht(2_000)), count = 2, people = listOf("Dana", "Bao"))
        assertEquals(HomeSummary(main = HomeBlock.YouOwe(owing), month = NOVEMBER), summary)
    }

    @Test
    fun `open requests win over the invitation on a phone that keeps no bill, with no spending to show beside them`() {
        val summary = HomeSummary.of(loaded(asked(150_000, "Minh")), bills = emptyList(), NOVEMBER, LOCAL)

        assertEquals(
            HomeSummary(main = HomeBlock.FriendsOweYou(open(dong(150_000), count = 1, "Minh"), youOwe = null), month = null),
            summary,
        )
    }

    @Test
    fun `a phone that keeps no bill and has no open request invites to split a first one, whatever the requests' state`() {
        for (requests in listOf(RequestTotals.SignedOut, RequestTotals.Unknown, loaded())) {
            assertEquals(
                "$requests",
                HomeSummary(main = HomeBlock.Invitation, month = null),
                HomeSummary.of(requests, emptyList(), null, LOCAL),
            )
        }
    }

    @Test
    fun `bills not read yet show nothing rather than an invitation, and spending not worked out yet shows nothing either`() {
        assertEquals(HomeSummary(main = null, month = null), HomeSummary.of(RequestTotals.SignedOut, bills = null, NOVEMBER, LOCAL))
        assertEquals(HomeSummary(main = null, month = null), HomeSummary.of(RequestTotals.Unknown, BILLS, month = null, LOCAL))
        // The requests need neither.
        val owed = HomeSummary.of(loaded(asked(150_000, "Minh")), bills = null, month = null, LOCAL)
        assertEquals(HomeSummary(main = HomeBlock.FriendsOweYou(open(dong(150_000), count = 1, "Minh"), youOwe = null), month = null), owed)
    }

    @Test
    fun `without a total in the phone's currency, the newest request's currency leads, and none is converted`() {
        val requests =
            loaded(asked(3_000, "Lan", currency = DOLLAR), asked(3_543, "Lan", currency = BAHT), asked(1_000, "Minh", currency = DOLLAR))

        val main = HomeSummary.of(requests, BILLS, NOVEMBER, LOCAL).main

        val owed = OpenRequests(main = dollars(4_000), others = listOf(baht(3_543)), count = 3, people = listOf("Lan", "Minh"))
        assertEquals(HomeBlock.FriendsOweYou(owed = owed, youOwe = null), main)
    }

    @Test
    fun `the group tile says what the user gets back, or what they owe without its sign`() {
        val trip = GroupTile.of(TRIP)
        assertEquals(GroupTile(GroupFixtures.GROUP_ID, "Chiang Mai trip", baht(3_543)), trip)
        assertEquals(R.string.home_you_get_back, trip?.standing)
        assertEquals(baht(3_543), trip?.amount)

        // Lan owes the group 1,262.50 baht.
        val lan = checkNotNull(GroupTile.of(TRIP.copy(myMemberId = GroupFixtures.LAN)))
        assertEquals(R.string.home_you_owe, lan.standing)
        assertEquals(baht(126_250), lan.amount)
    }

    @Test
    fun `a balance of zero is settled up`() {
        val settled = checkNotNull(GroupTile.of(TRIP.copy(bills = emptyList(), settlements = emptyList())))

        assertEquals(R.string.groups_settled_up, settled.standing)
        assertEquals(baht(0), settled.amount)
    }

    @Test
    fun `no group kept, or a balance beyond adding up, shows no group tile`() {
        assertNull(GroupTile.of(null))
        val beyond = SettlementDto("s9", GroupFixtures.LAN, GroupFixtures.ME, Long.MAX_VALUE, GroupFixtures.ME, createdAt = 1)
        assertNull(GroupTile.of(TRIP.copy(settlements = listOf(beyond, beyond.copy(id = "s10")))))
    }

    private fun loaded(vararg requests: RequestView): RequestTotals = RequestTotals.of(signedIn = true, requests = requests.toList())

    /** Open requests of one side in a single currency, with [people] on the other side. */
    private fun open(total: Money, count: Int, vararg people: String) = OpenRequests(total, emptyList(), count, people.toList())

    /** A request the user made, for [payer] to pay. */
    private fun asked(
        amountMinor: Long,
        payer: String,
        currency: CurrencyDto = DONG,
        reported: Boolean = false,
        confirmed: Boolean = false,
    ) = request(RequestView.CREDITOR, amountMinor, currency, payer = payer, creditor = "Dana", reported = reported, confirmed = confirmed)

    /** A request sent to the user, to pay to [creditor]. */
    private fun owed(amountMinor: Long, creditor: String, currency: CurrencyDto = DONG, reported: Boolean = false) =
        request(RequestView.DEBTOR, amountMinor, currency, payer = "Dana", creditor = creditor, reported = reported, confirmed = false)

    private fun request(
        role: String,
        amountMinor: Long,
        currency: CurrencyDto,
        payer: String,
        creditor: String,
        reported: Boolean,
        confirmed: Boolean,
    ) = RequestView(
        id = "r${requestCount++}",
        role = role,
        amountMinor = amountMinor,
        currency = currency,
        payerName = payer,
        creditorName = creditor,
        createdAt = 1_763_190_000_000,
        reportedPaidAt = if (reported) 1_763_200_000_000 else null,
        confirmedPaidAt = if (confirmed) 1_763_210_000_000 else null,
    )

    private var requestCount = 0

    private companion object {
        const val LOCAL = "VND"
        val DONG = CurrencyDto("VND", 0)
        val BAHT = CurrencyDto("THB", 2)
        val DOLLAR = CurrencyDto("USD", 2)

        fun dong(minor: Long) = Money(BillCurrency("VND", 0), minor)

        fun baht(minor: Long) = Money(BillCurrency("THB", 2), minor)

        fun dollars(minor: Long) = Money(BillCurrency("USD", 2), minor)

        /** The user's share of the six finished bills of November 2026, in dong. */
        val NOVEMBER = MonthSpending(YearMonth.of(2026, 11), dong(3_823_038), bills = 6)

        val BILLS = HomeFixtures.bills

        /** A trip of four kept in baht, where the user gets back 35.43. */
        val TRIP: GroupState = ProxyCache.json.decodeFromString(GroupState.serializer(), GroupFixtures.groupState())
    }
}
