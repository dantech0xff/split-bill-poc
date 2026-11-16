package com.dantech.splitvibe.domain

import com.dantech.splitvibe.domain.BillCategory.ENTERTAINMENT
import com.dantech.splitvibe.domain.BillCategory.FOOD_AND_DRINK
import com.dantech.splitvibe.domain.BillCategory.GROCERIES
import com.dantech.splitvibe.domain.BillCategory.LODGING
import com.dantech.splitvibe.domain.BillCategory.OTHER
import com.dantech.splitvibe.domain.BillCategory.RENT
import com.dantech.splitvibe.domain.BillCategory.SHOPPING
import com.dantech.splitvibe.domain.BillCategory.TRANSPORT
import com.dantech.splitvibe.domain.BillCategory.UTILITIES
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {

    private val vnd = BillCurrency("VND", 0)
    private val usd = BillCurrency("USD", 2)
    private val thb = BillCurrency("THB", 2)
    private val eur = BillCurrency("EUR", 2)
    private val names = mapOf(ME to "Dana", MINH to "Minh", LAN to "Lan", KHOA to "Khoa")

    /** A bill of the day [day] names, as yyyy-mm-dd, or of no day. */
    private fun bill(
        day: String?,
        category: BillCategory,
        group: InsightGroup?,
        currency: BillCurrency,
        total: Long,
        shares: Map<String, Long>,
        rate: ExchangeRate? = null,
    ) = InsightBill(
        day = day?.let(LocalDate::parse),
        category = category,
        group = group,
        currency = currency,
        totalMinor = total,
        shares = shares.map { (key, amount) -> InsightShare(key, names.getValue(key), amount) },
        myShareMinor = shares[ME] ?: 0,
        rate = rate,
    )

    private fun usdAt(rate: String) = ExchangeRate("USD", "VND", BigDecimal(rate))

    /**
     * Twenty bills from September to November 2026: rent, utilities and groceries of the flat the
     * user shares with Minh and Khoa, a trip to Da Lat with Lan too, and bills in no group, among
     * them some in dollars, baht and euros. Amounts are in minor units: dong, cents and satang.
     */
    private val bills = listOf(
        // 1 to 7, 14 to 20: in dong, counted as they are.
        bill("2026-09-05", FOOD_AND_DRINK, null, vnd, 300_000, mapOf(ME to 100_000, MINH to 100_000, LAN to 100_000)),
        bill("2026-09-12", GROCERIES, FLAT, vnd, 450_000, mapOf(ME to 150_000, MINH to 150_000, KHOA to 150_000)),
        bill("2026-09-30", RENT, FLAT, vnd, 9_000_000, mapOf(ME to 3_000_000, MINH to 3_000_000, KHOA to 3_000_000)),
        bill("2026-10-01", UTILITIES, FLAT, vnd, 1_200_000, mapOf(ME to 400_000, MINH to 400_000, KHOA to 400_000)),
        bill("2026-10-15", TRANSPORT, TRIP, vnd, 800_000, mapOf(ME to 200_000, MINH to 200_000, LAN to 200_000, KHOA to 200_000)),
        bill("2026-10-16", LODGING, TRIP, vnd, 2_400_000, mapOf(ME to 600_000, MINH to 600_000, LAN to 600_000, KHOA to 600_000)),
        bill("2026-10-17", FOOD_AND_DRINK, TRIP, vnd, 1_000_000, mapOf(ME to 250_000, MINH to 250_000, LAN to 250_000, KHOA to 250_000)),
        // 8: $12.00 at 25,350.5: the total is 304,206, the user's $5.00 is 126,752.5, which half up makes
        // 126,753 as the bill itself shows it (half to even would make 126,752), and Lan's $7.00 is
        // 177,453.5, which makes 177,454. These parts add up to 304,207, a dong more than the total.
        bill("2026-10-20", ENTERTAINMENT, null, usd, 1_200, mapOf(ME to 500, LAN to 700), usdAt("25350.5")),
        // 9: $40.50 at 25,400 is 1,028,700, and each $20.25 is 514,350. This bill calls Minh by his full
        // name, but the first bill that has him named him first.
        bill("2026-11-02", SHOPPING, null, usd, 4_050, mapOf(ME to 2_025, MINH to 2_025), usdAt("25400")).let { bill ->
            bill.copy(shares = bill.shares.map { if (it.personKey == MINH) it.copy(name = "Minh Tran") else it })
        },
        // 10: ฿1,000.00 at 700.25 is 700,250; the user's ฿333.34 is 233,421.335, so 233,421, and each
        // ฿333.33 is 233,414.3325, so 233,414. These parts add up to 700,249, a dong short of the total.
        bill(
            "2026-11-08",
            ENTERTAINMENT,
            null,
            thb,
            100_000,
            mapOf(ME to 33_334, MINH to 33_333, LAN to 33_333),
            ExchangeRate("THB", "VND", BigDecimal("700.25")),
        ),
        // 11 to 13: no rate to dong, so they are only counted: one in baht without a rate, one in euros
        // whose rate is to dollars, one in dollars without a rate.
        bill("2026-11-09", FOOD_AND_DRINK, null, thb, 50_000, mapOf(ME to 25_000, KHOA to 25_000)),
        bill("2026-10-05", LODGING, null, eur, 20_000, mapOf(ME to 10_000, LAN to 10_000), ExchangeRate("EUR", "USD", BigDecimal("1.08"))),
        bill("2026-11-03", SHOPPING, null, usd, 1_500, mapOf(ME to 1_500)),
        // 14 and 15: no day, so in every sum but those by month.
        bill(null, GROCERIES, FLAT, vnd, 210_000, mapOf(ME to 70_000, MINH to 70_000, KHOA to 70_000)),
        bill(null, TRANSPORT, null, vnd, 90_000, mapOf(ME to 45_000, LAN to 45_000)),
        // 16: the user paid for Minh and Lan's dinner and had none of it.
        bill("2026-10-18", FOOD_AND_DRINK, TRIP, vnd, 600_000, mapOf(ME to 0, MINH to 300_000, LAN to 300_000)),
        bill("2026-11-12", RENT, FLAT, vnd, 9_000_000, mapOf(ME to 3_000_000, MINH to 3_000_000, KHOA to 3_000_000)),
        bill("2026-11-14", UTILITIES, FLAT, vnd, 1_350_000, mapOf(ME to 450_000, MINH to 450_000, KHOA to 450_000)),
        bill("2026-09-20", ENTERTAINMENT, null, vnd, 500_000, mapOf(ME to 250_000, LAN to 250_000)),
        bill("2026-11-15", TRANSPORT, null, vnd, 120_000, mapOf(ME to 120_000)),
    )

    @Test
    fun `twenty bills add up by month, person, group and category to the table worked out by hand`() {
        val insights = Insights.of(bills, vnd)

        assertEquals(20, bills.size)
        assertEquals(vnd, insights.currency)
        assertEquals(mapOf("THB" to 1, "EUR" to 1, "USD" to 1), insights.withoutRate)
        // The 17 bills left, totals and the user's parts:
        // 300,000 + 450,000 + 9,000,000 + 1,200,000 + 800,000 + 2,400,000 + 1,000,000 + 304,206 + 1,028,700
        //   + 700,250 + 210,000 + 90,000 + 600,000 + 9,000,000 + 1,350,000 + 500,000 + 120,000 = 29,053,156;
        // 100,000 + 150,000 + 3,000,000 + 400,000 + 200,000 + 600,000 + 250,000 + 126,753 + 514,350
        //   + 233,421 + 70,000 + 45,000 + 0 + 3,000,000 + 450,000 + 250,000 + 120,000 = 9,509,524.
        assertEquals(Spending(17, 29_053_156, 9_509_524), insights.overall)
        // Bills 14 and 15: 210,000 + 90,000, and 70,000 + 45,000.
        assertEquals(Spending(2, 300_000, 115_000), insights.undated)
        assertEquals(
            mapOf(
                // 1, 2, 3, 19: 300,000 + 450,000 + 9,000,000 + 500,000; 100,000 + 150,000 + 3,000,000 + 250,000.
                YearMonth.of(2026, 9) to Spending(4, 10_250_000, 3_500_000),
                // 4, 5, 6, 7, 8, 16: 1,200,000 + 800,000 + 2,400,000 + 1,000,000 + 304,206 + 600,000;
                // 400,000 + 200,000 + 600,000 + 250,000 + 126,753 + 0.
                YearMonth.of(2026, 10) to Spending(6, 6_304_206, 1_576_753),
                // 9, 10, 17, 18, 20: 1,028,700 + 700,250 + 9,000,000 + 1,350,000 + 120,000;
                // 514,350 + 233,421 + 3,000,000 + 450,000 + 120,000.
                YearMonth.of(2026, 11) to Spending(5, 12_198_950, 4_317_771),
            ),
            insights.byMonth,
        )
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), insights.byMonth.keys.toList())
        assertEquals(
            mapOf(
                // 2, 3, 4, 14, 17, 18: 450,000 + 9,000,000 + 1,200,000 + 210,000 + 9,000,000 + 1,350,000;
                // 150,000 + 3,000,000 + 400,000 + 70,000 + 3,000,000 + 450,000.
                FLAT to Spending(6, 21_210_000, 7_070_000),
                // 5, 6, 7, 16: 800,000 + 2,400,000 + 1,000,000 + 600,000; 200,000 + 600,000 + 250,000 + 0.
                TRIP to Spending(4, 4_800_000, 1_050_000),
                // 1, 8, 9, 10, 15, 19, 20: 300,000 + 304,206 + 1,028,700 + 700,250 + 90,000 + 500,000 + 120,000;
                // 100,000 + 126,753 + 514,350 + 233,421 + 45,000 + 250,000 + 120,000.
                null to Spending(7, 3_043_156, 1_389_524),
            ),
            insights.byGroup,
        )
        assertEquals(listOf(FLAT, TRIP, null), insights.byGroup.keys.toList())
        assertEquals(
            mapOf(
                RENT to Spending(2, 18_000_000, 6_000_000), // 3 and 17
                UTILITIES to Spending(2, 2_550_000, 850_000), // 4 and 18: 1,200,000 + 1,350,000; 400,000 + 450,000
                LODGING to Spending(1, 2_400_000, 600_000), // 6; the euro bill 12 is left out
                FOOD_AND_DRINK to Spending(3, 1_900_000, 350_000), // 1, 7, 16; the baht bill 11 is left out
                ENTERTAINMENT to Spending(3, 1_504_456, 610_174), // 8, 10, 19: 304,206 + 700,250 + 500,000; 126,753 + 233,421 + 250,000
                SHOPPING to Spending(1, 1_028_700, 514_350), // 9; the dollar bill 13 is left out
                TRANSPORT to Spending(3, 1_010_000, 365_000), // 5, 15, 20: 800,000 + 90,000 + 120,000; 200,000 + 45,000 + 120,000
                GROCERIES to Spending(2, 660_000, 220_000), // 2 and 14: 450,000 + 210,000; 150,000 + 70,000
            ),
            insights.byCategory,
        )
        assertEquals(
            listOf(RENT, UTILITIES, LODGING, FOOD_AND_DRINK, ENTERTAINMENT, SHOPPING, TRANSPORT, GROCERIES),
            insights.byCategory.keys.toList(),
        )
        assertEquals(
            listOf(
                // On all 17 bills; their own parts are the user's parts.
                PersonSpending(ME, "Dana", 17, 29_053_156, 9_509_524, 9_509_524),
                // 1 to 7, 9, 10, 14, 16, 17, 18. Totals: 300,000 + 450,000 + 9,000,000 + 1,200,000 + 800,000
                // + 2,400,000 + 1,000,000 + 1,028,700 + 700,250 + 210,000 + 600,000 + 9,000,000 + 1,350,000.
                // The user's parts: the same bills' 100,000 + 150,000 + 3,000,000 + 400,000 + 200,000 + 600,000
                // + 250,000 + 514,350 + 233,421 + 70,000 + 0 + 3,000,000 + 450,000. Minh's: 100,000 + 150,000
                // + 3,000,000 + 400,000 + 200,000 + 600,000 + 250,000 + 514,350 + 233,414 + 70,000 + 300,000
                // + 3,000,000 + 450,000.
                PersonSpending(MINH, "Minh", 13, 28_038_950, 8_967_771, 9_267_764),
                // 2 to 7, 14, 17, 18: 450,000 + 9,000,000 + 1,200,000 + 800,000 + 2,400,000 + 1,000,000
                // + 210,000 + 9,000,000 + 1,350,000; Khoa's parts are the user's on each of them.
                PersonSpending(KHOA, "Khoa", 9, 25_410_000, 8_120_000, 8_120_000),
                // 1, 5, 6, 7, 8, 10, 15, 16, 19: 300,000 + 800,000 + 2,400,000 + 1,000,000 + 304,206 + 700,250
                // + 90,000 + 600,000 + 500,000. The user's: 100,000 + 200,000 + 600,000 + 250,000 + 126,753
                // + 233,421 + 45,000 + 0 + 250,000. Lan's: 100,000 + 200,000 + 600,000 + 250,000 + 177,454
                // + 233,414 + 45,000 + 300,000 + 250,000.
                PersonSpending(LAN, "Lan", 9, 6_694_456, 1_805_174, 2_155_868),
            ),
            insights.byPerson,
        )
        // Everyone's parts come to the totals: rounded one by one, bill 8's parts are a dong more than
        // its total and bill 10's a dong less.
        assertEquals(insights.overall.totalMinor, insights.byPerson.sumOf { it.shareMinor })
    }

    @Test
    fun `each bill is converted at its own rate, half up as the bill shows it, before the bills are added up`() {
        val coffee = bill("2026-11-01", FOOD_AND_DRINK, null, usd, 50, mapOf(ME to 50), usdAt("25349"))

        val insights = Insights.of(listOf(coffee, coffee.copy(day = LocalDate.of(2026, 11, 2))), vnd)

        // Each $0.50 at 25,349 is 12,674.5, which half up makes 12,675, as the bill shows it in dong:
        // 25,350 for both, where half to even would have made 25,348, and their $1.00 converted at
        // once 25,349.
        assertEquals(Spending(2, 25_350, 25_350), insights.overall)
        assertEquals(12_675L, usdAt("25349").convert(50, usd, vnd))
    }

    @Test
    fun `groups are told apart by their id, so two of the same name stay apart and a renamed one stays one`() {
        val work = InsightGroup("group-work", "Lunch club")
        val friends = InsightGroup("group-friends", "Lunch club")
        fun lunch(group: InsightGroup, total: Long) =
            bill("2026-11-01", FOOD_AND_DRINK, group, vnd, total, mapOf(ME to total / 2, LAN to total / 2))

        // Newest first. The last bill is in the same group as the first, from before it was called "Lunch club".
        val insights = Insights.of(listOf(lunch(work, 100_000), lunch(friends, 250_000), lunch(work.copy(name = "Office"), 200_000)), vnd)

        assertEquals(
            listOf(work to Spending(2, 300_000, 150_000), friends to Spending(1, 250_000, 125_000)),
            insights.byGroup.toList(),
        )
    }

    @Test
    fun `a bill is left out when it has no rate to the local currency, or one that cannot be right`() {
        val dinner = bill("2026-11-01", FOOD_AND_DRINK, null, usd, 2_000, mapOf(ME to 1_000, LAN to 1_000))

        val insights = Insights.of(
            listOf(
                dinner.copy(rate = ExchangeRate("USD", "THB", BigDecimal("36"))),
                dinner.copy(rate = ExchangeRate("THB", "VND", BigDecimal("700"))),
                dinner.copy(rate = usdAt("0")),
                dinner.copy(rate = usdAt("1E+20")),
                dinner.copy(rate = usdAt("25400")),
            ),
            vnd,
        )

        assertEquals(mapOf("USD" to 4), insights.withoutRate)
        assertEquals(Spending(1, 508_000, 254_000), insights.overall)
    }

    @Test
    fun `a bill in the local currency kept to other decimals is the same money`() {
        val rupiah = BillCurrency("IDR", 0)
        val warung = bill("2026-11-01", FOOD_AND_DRINK, null, rupiah, 103_400, mapOf(ME to 51_700, LAN to 51_700))

        val insights = Insights.of(listOf(warung), BillCurrency("IDR", 2))

        assertEquals(Spending(1, 10_340_000, 5_170_000), insights.overall)
        assertTrue(insights.withoutRate.isEmpty())
    }

    @Test
    fun `someone who is two participants of one bill is on it once, with both parts`() {
        val twice = InsightBill(
            day = LocalDate.of(2026, 11, 1),
            category = OTHER,
            group = null,
            currency = vnd,
            totalMinor = 90_000,
            shares = listOf(InsightShare(ME, "Dana", 30_000), InsightShare(MINH, "Minh", 30_000), InsightShare(MINH, "Minh", 30_000)),
            myShareMinor = 30_000,
            rate = null,
        )

        val minh = Insights.of(listOf(twice), vnd).byPerson.first()

        assertEquals(PersonSpending(MINH, "Minh", 1, 90_000, 30_000, 60_000), minh)
    }

    @Test
    fun `no bills add up to nothing`() {
        val insights = Insights.of(emptyList(), vnd)

        assertEquals(Spending(0, 0, 0), insights.overall)
        assertEquals(Spending(0, 0, 0), insights.undated)
        assertTrue(insights.byMonth.isEmpty() && insights.byPerson.isEmpty() && insights.byGroup.isEmpty() && insights.byCategory.isEmpty())
    }

    private companion object {
        const val ME = "me"
        const val MINH = "person-minh"
        const val LAN = "person-lan"
        const val KHOA = "person-khoa"
        val FLAT = InsightGroup("group-flat", "Flat 3B")
        val TRIP = InsightGroup("group-trip", "Da Lat trip")
    }
}
