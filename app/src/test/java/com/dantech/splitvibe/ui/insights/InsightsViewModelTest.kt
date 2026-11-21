package com.dantech.splitvibe.ui.insights

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.R
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.SpendingFixtures.FLAT
import com.dantech.splitvibe.SpendingFixtures.TRIP
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.contentWords
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillCategory.ENTERTAINMENT
import com.dantech.splitvibe.domain.BillCategory.FOOD_AND_DRINK
import com.dantech.splitvibe.domain.BillCategory.LODGING
import com.dantech.splitvibe.domain.BillCategory.OTHER
import com.dantech.splitvibe.domain.BillCategory.RENT
import com.dantech.splitvibe.domain.BillCategory.SHOPPING
import com.dantech.splitvibe.domain.BillCategory.TRANSPORT
import com.dantech.splitvibe.domain.BillCategory.UTILITIES
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ExchangeRate
import com.dantech.splitvibe.domain.InsightGroup
import com.dantech.splitvibe.domain.InsightShare
import com.dantech.splitvibe.domain.Insights
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Spending
import com.dantech.splitvibe.english
import com.dantech.splitvibe.export.CsvExport
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.export.Exporter
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The statistics of the saved bills as the insights screen shows them: which bills each period
 * takes, how a saved bill becomes what the statistics count, and the totals, checked against a
 * table worked out by hand for the fixture's twelve finished bills and one draft, in the zone of
 * Ho Chi Minh City.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InsightsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)

    /** A clock that moves on by one at every save, so the bill saved last is the latest. */
    private var now = 0L
    private val bills = BillRepository(storage.database, storage.people, storage.pages, contentWords()) { ++now }
    private val clock = SpendingFixtures.clock
    private val today = LocalDate.now(clock)
    private val watching = MainScope()
    private lateinit var viewModel: InsightsViewModel

    /** The fixture's groups as the statistics tell them apart, by their ids. */
    private val flat = InsightGroup("group-$FLAT", FLAT)
    private val trip = InsightGroup("group-$TRIP", TRIP)

    @Before
    fun setUp() = runBlocking {
        // Oldest first, so the first bill of the fixture is the one saved last, as the list has it.
        SpendingFixtures.year.reversed().forEach { bills.save(it) }
        // An unfinished bill counts nowhere.
        bills.save(
            SpendingFixtures.finished("d1", "2026-11-14", "Draft", FOOD_AND_DRINK, 999_000, listOf("Minh")).copy(status = BillStatus.DRAFT),
        )
        val settings = MutableStateFlow(AppSettings(localCurrency = "VND"))
        viewModel = InsightsViewModel(bills, settings, Exporter(context), clock, SavedStateHandle())
        // The screen watches the state while it shows.
        watching.launch { viewModel.state.collect {} }
        Unit
    }

    @After
    fun tearDown() {
        watching.cancel()
        storage.close()
    }

    /** A screen's view model that nothing watches yet, as one the system just made again, on the day [clock] tells. */
    private fun viewModelOn(clock: Clock) = InsightsViewModel(
        bills,
        MutableStateFlow(AppSettings(localCurrency = "VND")),
        Exporter(context),
        clock,
        SavedStateHandle(),
    )

    private fun shown(period: InsightsPeriod): InsightsState {
        viewModel.choose(period)
        awaitOnMain { viewModel.state.value?.period == period }
        return checkNotNull(viewModel.state.value)
    }

    /** Each person as the statistics name them, with their bills, the totals of those, the user's part and their own. */
    private fun people(state: InsightsState) =
        state.insights.byPerson.map { listOf(personName(it).english(), it.bills, it.totalMinor, it.myShareMinor, it.shareMinor) }

    @Test
    fun `this month takes the bills of November, the undated ones by the day they were saved, and adds them up by hand`() {
        val month = shown(InsightsPeriod.THIS_MONTH)

        assertEquals("November 2026", month.periodName.english())
        assertEquals("2026-11", month.periodKey)
        // By the day each counts on: 9 was saved on the 13th, and 8 on the 1st, in Ho Chi Minh City.
        assertEquals(listOf("b10", "b9", "b1", "b11", "b12", "b5", "b2", "b8"), month.bills.map { it.id })
        // 10, 9, 1, 12, 2 and 8: 600,000 + 1,200,000 + 310,000 + 139,409 + 9,000,000 + 500,000;
        // 0 + 400,000 + 103,334 + 69,704 + 3,000,000 + 250,000. The euros and the baht of 5 have no rate to dong.
        assertEquals(Spending(6, 11_749_409, 3_823_038), month.insights.overall)
        assertEquals(mapOf("EUR" to 1, "THB" to 1), month.insights.withoutRate)
        assertEquals(mapOf(YearMonth.of(2026, 11) to Spending(6, 11_749_409, 3_823_038)), month.insights.byMonth)
        assertEquals(
            listOf(
                RENT to Spending(1, 9_000_000, 3_000_000),
                UTILITIES to Spending(1, 1_200_000, 400_000),
                FOOD_AND_DRINK to Spending(2, 910_000, 103_334),
                SHOPPING to Spending(1, 500_000, 250_000),
                TRANSPORT to Spending(1, 139_409, 69_704),
            ),
            month.insights.byCategory.toList(),
        )
        assertEquals(
            listOf(flat to Spending(2, 10_200_000, 3_400_000), null to Spending(4, 1_549_409, 423_038)),
            month.insights.byGroup.toList(),
        )
        assertEquals(
            listOf(
                listOf("You", 6, 11_749_409L, 3_823_038L, 3_823_038L),
                // 10, 1, 2 and 9: 300,000 + 103,333 + 3,000,000 + 400,000, more than the user's own part.
                listOf("Minh", 4, 11_110_000L, 3_503_334L, 3_803_333L),
                listOf("Khoa", 2, 10_200_000L, 3_400_000L, 3_400_000L),
                // 10, 1, 12 and 8: 300,000 + 103,333 + 69,704 + 250,000.
                listOf("Lan", 4, 1_549_409L, 423_038L, 723_037L),
            ),
            people(month),
        )
        assertEquals(listOf(RATE_NOTE, SAVED_DAY_NOTE), month.notes.map { it.english() })
    }

    @Test
    fun `the last 12 months start on 1 December 2025 and add up to the table worked out by hand`() {
        val year = shown(InsightsPeriod.LAST_12_MONTHS)

        assertEquals("December 2025 to November 2026", year.periodName.english())
        assertEquals("2025-12-to-2026-11", year.periodKey)
        // Bill 7, of 30 November 2025, is a day too early.
        assertEquals(listOf("b10", "b9", "b1", "b11", "b12", "b5", "b2", "b8", "b4", "b3", "b6"), year.bills.map { it.id })
        // November's, then 4, 3 and 6: + 762,024 + 9,000,000 + 800,000; + 381,012 + 3,000,000 + 200,000.
        assertEquals(Spending(9, 22_311_433, 7_404_050), year.insights.overall)
        assertEquals(
            mapOf(
                YearMonth.of(2025, 12) to Spending(1, 800_000, 200_000),
                // 3 and the $30.00 of 4 at the day's rate: 9,000,000 + 762,024; 3,000,000 + 381,012.
                YearMonth.of(2026, 10) to Spending(2, 9_762_024, 3_381_012),
                YearMonth.of(2026, 11) to Spending(6, 11_749_409, 3_823_038),
            ),
            year.insights.byMonth,
        )
        assertEquals(
            listOf(
                RENT to Spending(2, 18_000_000, 6_000_000),
                UTILITIES to Spending(1, 1_200_000, 400_000),
                // 12 and 6: 139,409 + 800,000; 69,704 + 200,000.
                TRANSPORT to Spending(2, 939_409, 269_704),
                FOOD_AND_DRINK to Spending(2, 910_000, 103_334),
                ENTERTAINMENT to Spending(1, 762_024, 381_012),
                SHOPPING to Spending(1, 500_000, 250_000),
            ),
            year.insights.byCategory.toList(),
        )
        assertEquals(
            listOf(
                flat to Spending(3, 19_200_000, 6_400_000),
                // 10, 1, 12, 4 and 8: 600,000 + 310,000 + 139,409 + 762,024 + 500,000; 0 + 103,334 + 69,704 + 381,012 + 250,000.
                null to Spending(5, 2_311_433, 804_050),
                trip to Spending(1, 800_000, 200_000),
            ),
            year.insights.byGroup.toList(),
        )
        assertEquals(
            listOf(
                listOf("You", 9, 22_311_433L, 7_404_050L, 7_404_050L),
                // 10, 1, 2, 3, 6 and 9.
                listOf("Minh", 6, 20_910_000L, 6_703_334L, 7_003_333L),
                listOf("Khoa", 3, 19_200_000L, 6_400_000L, 6_400_000L),
                // 10, 1, 12, 4, 6 and 8: 600,000 + 310,000 + 139,409 + 762,024 + 800,000 + 500,000;
                // 0 + 103,334 + 69,704 + 381,012 + 200,000 + 250,000; and Lan's own 300,000 + 103,333 + 69,704
                // + 381,012 + 200,000 + 250,000.
                listOf("Lan", 6, 3_111_433L, 1_004_050L, 1_304_049L),
                listOf("An", 1, 800_000L, 200_000L, 200_000L),
            ),
            people(year),
        )
        assertEquals(listOf(RATE_NOTE, SAVED_DAY_NOTE), year.notes.map { it.english() })
    }

    @Test
    fun `all time counts every bill, each in a month, and the trip's group under its name now`() {
        val all = shown(InsightsPeriod.ALL_TIME)

        assertEquals("All time", all.periodName.english())
        assertEquals(12, all.bills.size)
        // Every bill but the euros and the baht of 5: the year's, plus 7: + 2,400,000; + 600,000.
        assertEquals(Spending(10, 24_711_433, 8_004_050), all.insights.overall)
        assertEquals(Spending(0, 0, 0), all.insights.undated)
        assertEquals(
            listOf(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
            all.insights.byMonth.keys.toList(),
        )
        assertEquals(Spending(1, 2_400_000, 600_000), all.insights.byMonth[YearMonth.of(2025, 11)])
        assertEquals(
            listOf(
                RENT to Spending(2, 18_000_000, 6_000_000),
                LODGING to Spending(1, 2_400_000, 600_000),
                UTILITIES to Spending(1, 1_200_000, 400_000),
                TRANSPORT to Spending(2, 939_409, 269_704),
                FOOD_AND_DRINK to Spending(2, 910_000, 103_334),
                ENTERTAINMENT to Spending(1, 762_024, 381_012),
                SHOPPING to Spending(1, 500_000, 250_000),
            ),
            all.insights.byCategory.toList(),
        )
        assertEquals(
            listOf(
                flat to Spending(3, 19_200_000, 6_400_000),
                // 6 and 7: 7 went to the group while it was called Đà Lạt; it is named as 6 names it.
                trip to Spending(2, 3_200_000, 800_000),
                null to Spending(5, 2_311_433, 804_050),
            ),
            all.insights.byGroup.toList(),
        )
        assertEquals(TRIP, all.insights.byGroup.keys.elementAt(1)?.name)
        assertEquals(
            listOf(
                listOf("You", 10, 24_711_433L, 8_004_050L, 8_004_050L),
                listOf("Minh", 7, 23_310_000L, 7_303_334L, 7_603_333L),
                listOf("Khoa", 3, 19_200_000L, 6_400_000L, 6_400_000L),
                listOf("Lan", 7, 5_511_433L, 1_604_050L, 1_904_049L),
                // Joined bills 6 and 7 from their links as "An" and "an": the same person, named as the latest bill does.
                listOf("An", 2, 3_200_000L, 800_000L, 800_000L),
            ),
            people(all),
        )
        assertEquals(listOf(RATE_NOTE, SAVED_DAY_NOTE), all.notes.map { it.english() })
    }

    @Test
    fun `the chart has a bar for every month of the period, or every year of all time, and its bars add up to the total at the top`() {
        val month = shown(InsightsPeriod.THIS_MONTH)
        assertEquals(listOf(Span.Month(YearMonth.of(2026, 11))), month.spans.map { it.span })
        assertEquals(month.insights.overall.totalMinor, month.spans.sumOf { it.spending.totalMinor })

        val year = shown(InsightsPeriod.LAST_12_MONTHS)
        val months = year.spans
        assertEquals((0L until 12L).map { Span.Month(YearMonth.of(2025, 12).plusMonths(it)) }, months.map { it.span })
        assertEquals(year.insights.overall.totalMinor, months.sumOf { it.spending.totalMinor })
        // A month without bills is a bar of nothing, which the chart still shows.
        assertEquals(NOTHING, months.single { it.span == Span.Month(YearMonth.of(2026, 1)) }.spending)
        assertEquals(Spending(2, 9_762_024, 3_381_012), months.single { it.span == Span.Month(YearMonth.of(2026, 10)) }.spending)

        // From the year of the first bill, 2025, to this one.
        val all = shown(InsightsPeriod.ALL_TIME)
        assertEquals(listOf(Span.Year(2025), Span.Year(2026)), all.spans.map { it.span })
        assertEquals(all.insights.overall.totalMinor, all.spans.sumOf { it.spending.totalMinor })
        // 7 and 6, of November and December 2025: 2,400,000 + 800,000; 600,000 + 200,000.
        assertEquals(Spending(2, 3_200_000, 800_000), all.spans.first().spending)
    }

    @Test
    fun `the chart says which months or years it runs over and which came to most, the later first of two equal ones`() {
        val year = shown(InsightsPeriod.LAST_12_MONTHS)
        assertEquals(
            "Spending by month from December 2025 to November 2026. November 2026 is the highest, then October 2026.",
            chartDescription(year.spans).english(),
        )
        val all = shown(InsightsPeriod.ALL_TIME)
        assertEquals("Spending by year from 2025 to 2026. 2026 is the highest, then 2025.", chartDescription(all.spans).english())

        val even = listOf(monthOf(2026, 9, 500), monthOf(2026, 10, 500), monthOf(2026, 11, 0))
        assertEquals(
            "Spending by month from September 2026 to November 2026. October 2026 is the highest, then September 2026.",
            chartDescription(even).english(),
        )
        val one = listOf(monthOf(2026, 10, 0), monthOf(2026, 11, 700))
        assertEquals("Spending by month from October 2026 to November 2026. November 2026 is the highest.", chartDescription(one).english())
        val none = listOf(monthOf(2026, 10, 0), monthOf(2026, 11, 0))
        assertEquals("Spending by month from October 2026 to November 2026.", chartDescription(none).english())
    }

    @Test
    fun `a date in a year nobody meant counts as the day the bill was saved, as the notice says, and all time starts in 2000 or later`() {
        // Typed as 2925 for 2025, read off a Thai receipt in the years of the Buddhist era, and typed as 0202.
        val wrong = listOf("2925-11-13", "2568-11-13", "0202-11-13").mapIndexed { index, date ->
            SpendingFixtures.finished("w$index", date, "Cinema", ENTERTAINMENT, 100_000, emptyList()).copy(createdAt = SAVED_ON_THE_10TH)
        }
        for (bill in wrong) {
            assertEquals("The bill dated ${bill.bill.date}", LocalDate.of(2026, 11, 10), insightBillOf(bill, "VND", today, clock.zone).day)
        }
        val wrongNote = "3 bills have no date that can be used, so each is counted on the day it was saved."

        val month = insightsOf(wrong, "VND", InsightsPeriod.THIS_MONTH, clock)
        assertEquals(Spending(3, 300_000, 300_000), month.insights.overall)
        assertEquals(listOf(wrongNote), month.notes.map { it.english() })
        val all = insightsOf(wrong, "VND", InsightsPeriod.ALL_TIME, clock)
        assertEquals(listOf(Span.Year(2026)), all.spans.map { it.span })
        assertEquals(listOf(wrongNote), all.notes.map { it.english() })

        // The earliest day a date still counts on, with the year's bills and the three: a bar for each year from it, and no more.
        val first = SpendingFixtures.finished("first", "2000-01-01", "Phở Hòa", FOOD_AND_DRINK, 50_000, emptyList())
        val since2000 = insightsOf(SpendingFixtures.year + wrong + first, "VND", InsightsPeriod.ALL_TIME, clock)
        assertEquals((2000..2026).map { Span.Year(it) }, since2000.spans.map { it.span })
        assertEquals(since2000.insights.overall.totalMinor, since2000.spans.sumOf { it.spending.totalMinor })
        assertEquals(Spending(1, 50_000, 50_000), since2000.spans.first().spending)
    }

    /** A month of a chart, of [totalMinor] in one bill, or in none for nothing. */
    private fun monthOf(year: Int, month: Int, totalMinor: Long) =
        SpanSpending(Span.Month(YearMonth.of(year, month)), if (totalMinor == 0L) NOTHING else Spending(1, totalMinor, 0))

    @Test
    fun `a saved bill counts with each person under a key of their own, and its rate only when it converts into the local currency`() {
        val saved = runBlocking { checkNotNull(bills.get("b6")) }

        val counted = insightBillOf(saved, "VND", today, clock.zone)

        assertEquals(LocalDate.of(2025, 12, 5), counted.day)
        assertEquals(TRANSPORT, counted.category)
        assertEquals(trip, counted.group)
        assertEquals(800_000L, counted.totalMinor)
        assertEquals(200_000L, counted.myShareMinor)
        // The user is "me" on every bill, and everyone else their name without case or accents, the
        // people the phone remembers as well as someone who joined from a link.
        assertEquals(
            listOf(
                InsightShare(ME_ID, "", 200_000),
                InsightShare("name:minh", "Minh", 200_000),
                InsightShare("name:lan", "Lan", 200_000),
                InsightShare("name:an", "An", 200_000),
            ),
            counted.shares,
        )
        assertNull(counted.rate)
        fun rateOf(id: String) = insightBillOf(runBlocking { checkNotNull(bills.get(id)) }, "VND", today, clock.zone).rate
        // The day's rate saved as the bill was finished, and a rate the user typed.
        assertEquals(SpendingFixtures.usdDayRate, rateOf("b4"))
        assertEquals(ExchangeRate("THB", "VND", BigDecimal("745.5")), rateOf("b12"))
        // A rate typed from euros to dollars converts nothing into dong, and the baht of 5 have none.
        assertNull(rateOf("b11"))
        assertNull(rateOf("b5"))
        // Without a date that can be read, the day it was saved in Ho Chi Minh City, not in UTC.
        val b8 = runBlocking { checkNotNull(bills.get("b8")) }
        assertEquals(LocalDate.of(2026, 11, 1), insightBillOf(b8, "VND", today, clock.zone).day)
    }

    @Test
    fun `a period's notes say how many bills each note is about, and how to count one without a rate`() {
        fun notes(vararg bills: SavedBill) = insightsOf(bills.toList(), "VND", InsightsPeriod.ALL_TIME, clock).notes.map { it.english() }
        val yen = SpendingFixtures.finished("y1", "2026-11-02", "Ichiran", FOOD_AND_DRINK, 1_980, emptyList(), BillCurrency("JPY", 0))
        val undated = SpendingFixtures.finished("u1", null, "Chợ", OTHER, 50_000, emptyList())

        assertEquals(
            listOf(
                "1 bill in JPY is left out of the totals, as no rate to VND is saved with it. To count it, open it and type a rate.",
                "1 bill has no date that can be used, so it is counted on the day it was saved.",
            ),
            notes(yen, undated),
        )
        assertEquals(
            listOf(
                "2 bills in JPY are left out of the totals, as no rate to VND is saved with them. " +
                    "To count one, open it and type a rate.",
            ),
            notes(yen, yen.copy(id = "y2")),
        )
    }

    @Test
    fun `the user and someone without a name are named by the screen, in the language it shows`() {
        val unnamed = SpendingFixtures.finished("u1", "2026-11-03", "Cafe", FOOD_AND_DRINK, 100_000, listOf(""))
        val people = Insights.of(listOf(insightBillOf(unnamed, "VND", today, clock.zone)), SpendingFixtures.vnd).byPerson
        assertEquals("nothing the insights keep is in a language", listOf("", ""), people.map { it.name })

        val inPseudolocale = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("en-XA")) }
        val pseudo = context.createConfigurationContext(inPseudolocale)
        assertEquals(listOf("You", "Someone"), people.map { personName(it).english() })
        assertEquals(
            listOf(pseudo.getString(R.string.common_you), pseudo.getString(R.string.common_someone)),
            people.map { personName(it).resolve(pseudo.resources) },
        )
        assertNotEquals("You", pseudo.getString(R.string.common_you))
    }

    @Test
    fun `someone forgotten and added again, or of the same name from a link, is one person in the insights`() {
        // Lan was forgotten, so the phone remembers her under a new id since, and "minh" joined the
        // newer bill from its link; the older one is with the Minh the phone remembers.
        val older = SpendingFixtures
            .finished("f1", "2026-11-01", "Phở sáng", FOOD_AND_DRINK, 300_000, listOf("Lan", "Minh"))
            .withPersonIds("Lan" to "lan-forgotten", "Minh" to "minh-remembered")
        val newer = SpendingFixtures
            .finished("f2", "2026-11-02", "Bún chả", FOOD_AND_DRINK, 210_000, listOf("Lan"), strangers = listOf("minh"))
            .withPersonIds("Lan" to "lan-again")

        val counted = listOf(newer, older).map { insightBillOf(it, "VND", today, clock.zone) }
        val people = Insights.of(counted, SpendingFixtures.vnd).byPerson

        // 100,000 + 70,000 each, and Minh named as the newer bill names him. Among equal shares the
        // user comes first, whatever the screen's language calls them.
        assertEquals(listOf("You", "Lan", "minh"), people.map { personName(it).english() })
        assertEquals(listOf(2, 2, 2), people.map { it.bills })
        assertEquals(listOf(170_000L, 170_000L, 170_000L), people.map { it.shareMinor })
    }

    /** This bill with the participants named in [ids] remembered by the phone under those ids. */
    private fun SavedBill.withPersonIds(vararg ids: Pair<String, String>): SavedBill {
        val byName = ids.toMap()
        return copy(plan = plan.copy(participants = plan.participants.map { it.copy(personId = byName[it.name] ?: it.personId) }))
    }

    @Test
    fun `the period chosen comes back with the screen, and the CSV files go to the share sheet together`() {
        shown(InsightsPeriod.LAST_12_MONTHS)

        viewModel.share(ExportFormat.CSV)
        awaitOnMain { viewModel.export.value.share != null }

        val chooser = checkNotNull(viewModel.export.value.share)
        val sent = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, sent.action)
        assertEquals(CsvExport.MIME_TYPE, sent.type)
        val files = checkNotNull(IntentCompat.getParcelableArrayListExtra(sent, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(listOf(CsvExport.BILLS, CsvExport.ITEMS, CsvExport.SHARES), files.map { it.lastPathSegment })
        // Through the app's file provider, from its folder of shared exports.
        files.forEach { assertEquals("${context.packageName}.fileprovider", it.authority) }
        assertEquals(1, files.map { it.pathSegments.dropLast(1) }.distinct().size)
        // What each one holds is what the export wrote for the period.
        val content = contentOf(checkNotNull(viewModel.state.value)) { it.english() }
        val written = runBlocking { Exporter(context).files(content, ExportFormat.CSV) }
        files.zip(written).forEach { (uri, file) ->
            assertArrayEquals(file.bytes, File(context.cacheDir, uri.pathSegments.joinToString("/")).readBytes())
        }

        viewModel.shared()
        assertNull(viewModel.export.value.share)
    }

    @Test
    fun `the CSV files are saved into the folder the user picked, and the screen says so`() {
        shown(InsightsPeriod.THIS_MONTH)
        Robolectric.setupContentProvider(PickedFolder::class.java, PickedFolder.AUTHORITY)

        viewModel.saveToFolder(DocumentsContract.buildTreeDocumentUri(PickedFolder.AUTHORITY, "Downloads"))
        awaitOnMain { viewModel.export.value.message != null }

        assertEquals(SAVED to false, viewModel.export.value.message?.let { it.text.english() to it.isError })
        val content = contentOf(checkNotNull(viewModel.state.value)) { it.english() }
        val written = runBlocking { Exporter(context).files(content, ExportFormat.CSV) }
        written.forEach { file -> assertArrayEquals(file.bytes, File(context.cacheDir, "picked/${file.name}").readBytes()) }
        viewModel.dismissMessage()
        assertNull(viewModel.export.value.message)
    }

    @Test
    fun `a folder picked as the system ended the app is saved to once the bills are read again`() {
        Robolectric.setupContentProvider(PickedFolder::class.java, PickedFolder.AUTHORITY)
        // The screen comes back with its period, and the picker's answer comes before the bills are read.
        val restored = viewModelOn(clock)
        assertNull(restored.state.value)

        restored.saveToFolder(DocumentsContract.buildTreeDocumentUri(PickedFolder.AUTHORITY, "Downloads"))
        awaitOnMain { restored.export.value.message != null }

        assertEquals(SAVED to false, restored.export.value.message?.let { it.text.english() to it.isError })
        assertEquals(CsvExport.BILLS, File(context.cacheDir, "picked").list()?.sorted()?.first())
    }

    @Test
    fun `a period without bills says so, instead of exporting nothing without a word`() {
        // Four years on, no bill of the fixture is in this month.
        val later = Clock.fixed(LocalDate.of(2030, 11, 16).atTime(9, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val empty = viewModelOn(later)

        empty.share(ExportFormat.CSV)
        awaitOnMain { empty.export.value.message != null }

        assertEquals(NOTHING_TO_EXPORT to true, empty.export.value.message?.let { it.text.english() to it.isError })
        assertNull(empty.export.value.share)
        assertEquals(false, empty.export.value.working)
    }

    @Test
    fun `a folder that cannot be written to is said so, and nothing else changes`() {
        shown(InsightsPeriod.THIS_MONTH)

        // No app keeps this folder, as when the user's file app went away meanwhile.
        viewModel.saveToFolder(DocumentsContract.buildTreeDocumentUri("com.example.gone", "Downloads"))
        awaitOnMain { viewModel.export.value.message != null }

        assertEquals(true, viewModel.export.value.message?.isError)
        assertEquals(false, viewModel.export.value.working)
    }

    private companion object {
        const val SAVED = "Saved bills.csv, items.csv and shares.csv."
        const val NOTHING_TO_EXPORT = "Nothing was exported, as this period has no finished bills."
        const val RATE_NOTE = "2 bills are left out of the totals, as no rate to VND is saved with them: 1 in EUR and 1 in THB. " +
            "To count one, open it and type a rate."
        const val SAVED_DAY_NOTE = "2 bills have no date that can be used, so each is counted on the day it was saved."
        val NOTHING = Spending(0, 0, 0)

        /** 10 November 2026, in the morning in Ho Chi Minh City. */
        val SAVED_ON_THE_10TH = Instant.parse("2026-11-10T03:00:00Z").toEpochMilli()
    }
}

/**
 * The documents provider of a folder the user picked, as a phone's file app is: it makes each file
 * it is asked for in the cache's "picked" folder, as the system's own provider would in the folder.
 */
class PickedFolder : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        check(method == "android:createDocument") { "Only files are made here, not $method" }
        val name = checkNotNull(extras?.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
        val folder = File(checkNotNull(context).cacheDir, "picked").apply { mkdirs() }
        // The key the system's DocumentsContract reads the new file's address under.
        return Bundle().apply { putParcelable("uri", Uri.fromFile(File(folder, name))) }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, columns: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.example.files"
    }
}
