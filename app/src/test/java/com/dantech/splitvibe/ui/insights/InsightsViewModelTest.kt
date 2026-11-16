package com.dantech.splitvibe.ui.insights

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.SpendingFixtures.FLAT
import com.dantech.splitvibe.SpendingFixtures.TRIP
import com.dantech.splitvibe.TestStorage
import com.dantech.splitvibe.awaitOnMain
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.settings.AppSettings
import com.dantech.splitvibe.domain.BillCategory.ENTERTAINMENT
import com.dantech.splitvibe.domain.BillCategory.FOOD_AND_DRINK
import com.dantech.splitvibe.domain.BillCategory.LODGING
import com.dantech.splitvibe.domain.BillCategory.RENT
import com.dantech.splitvibe.domain.BillCategory.SHOPPING
import com.dantech.splitvibe.domain.BillCategory.TRANSPORT
import com.dantech.splitvibe.domain.BillCategory.UTILITIES
import com.dantech.splitvibe.domain.InsightGroup
import com.dantech.splitvibe.domain.InsightShare
import com.dantech.splitvibe.domain.Insights
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Spending
import com.dantech.splitvibe.export.CsvExport
import com.dantech.splitvibe.export.ExportFormat
import com.dantech.splitvibe.export.Exporter
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
 * table worked out by hand for the fixture's eleven finished bills and one draft.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InsightsViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)

    /** A clock that moves on by one at every save, so the bill saved last is the latest. */
    private var now = 0L
    private val bills = BillRepository(storage.database, storage.people, storage.pages) { ++now }
    private val today = LocalDate.of(2026, 11, 16)
    private val clock = Clock.fixed(today.atTime(9, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
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
    private fun viewModelOn(clock: Clock) =
        InsightsViewModel(bills, MutableStateFlow(AppSettings(localCurrency = "VND")), Exporter(context), clock, SavedStateHandle())

    private fun shown(period: InsightsPeriod): InsightsState {
        viewModel.choose(period)
        awaitOnMain { viewModel.state.value?.period == period }
        return checkNotNull(viewModel.state.value)
    }

    /** Each person as the statistics name them, with their bills, the totals of those, the user's part and their own. */
    private fun people(state: InsightsState) =
        state.insights.byPerson.map { listOf(it.name, it.bills, it.totalMinor, it.myShareMinor, it.shareMinor) }

    @Test
    fun `this month takes the bills dated in November and adds them up to the table worked out by hand`() {
        val month = shown(InsightsPeriod.THIS_MONTH)

        assertEquals("November 2026", month.periodName)
        assertEquals("2026-11", month.periodKey)
        assertEquals(listOf("b10", "b1", "b11", "b5", "b2"), month.bills.map { it.id })
        // 10, 1 and 2: 600,000 + 300,000 + 9,000,000; 0 + 100,000 + 3,000,000. The euros and the baht have no rate to dong.
        assertEquals(Spending(3, 9_900_000, 3_100_000), month.insights.overall)
        assertEquals(mapOf("EUR" to 1, "THB" to 1), month.insights.withoutRate)
        assertEquals(mapOf(YearMonth.of(2026, 11) to Spending(3, 9_900_000, 3_100_000)), month.insights.byMonth)
        assertEquals(
            listOf(RENT to Spending(1, 9_000_000, 3_000_000), FOOD_AND_DRINK to Spending(2, 900_000, 100_000)),
            month.insights.byCategory.toList(),
        )
        assertEquals(
            listOf(flat to Spending(1, 9_000_000, 3_000_000), null to Spending(2, 900_000, 100_000)),
            month.insights.byGroup.toList(),
        )
        assertEquals(
            listOf(
                // 1, 2 and 10: 100,000 + 3,000,000 + 300,000, more than the user's own part.
                listOf("Minh", 3, 9_900_000L, 3_100_000L, 3_400_000L),
                listOf("You", 3, 9_900_000L, 3_100_000L, 3_100_000L),
                listOf("Khoa", 1, 9_000_000L, 3_000_000L, 3_000_000L),
                listOf("Lan", 2, 900_000L, 100_000L, 400_000L),
            ),
            people(month),
        )
        assertEquals(
            listOf(
                "2 bills are left out of the totals, as no rate to VND is saved with them: 1 in EUR and 1 in THB.",
                "2 bills have no date that can be read, so they are left out of this period. All time counts them.",
            ),
            month.notes,
        )
    }

    @Test
    fun `the last 12 months start on 1 December 2025 and add up to the table worked out by hand`() {
        val year = shown(InsightsPeriod.LAST_12_MONTHS)

        assertEquals("December 2025 to November 2026", year.periodName)
        assertEquals("2025-12-to-2026-11", year.periodKey)
        // Bill 7, of 30 November 2025, is a day too early.
        assertEquals(listOf("b10", "b1", "b11", "b5", "b2", "b4", "b3", "b6"), year.bills.map { it.id })
        // 600,000 + 300,000 + 9,000,000 + 762,000 + 9,000,000 + 800,000; 0 + 100,000 + 3,000,000 + 381,000 + 3,000,000 + 200,000.
        assertEquals(Spending(6, 20_462_000, 6_681_000), year.insights.overall)
        assertEquals(
            mapOf(
                YearMonth.of(2025, 12) to Spending(1, 800_000, 200_000),
                // 3 and the $30.00 of 4 at 25,400: 9,000,000 + 762,000; 3,000,000 + 381,000.
                YearMonth.of(2026, 10) to Spending(2, 9_762_000, 3_381_000),
                YearMonth.of(2026, 11) to Spending(3, 9_900_000, 3_100_000),
            ),
            year.insights.byMonth,
        )
        assertEquals(
            listOf(
                RENT to Spending(2, 18_000_000, 6_000_000),
                FOOD_AND_DRINK to Spending(2, 900_000, 100_000),
                TRANSPORT to Spending(1, 800_000, 200_000),
                ENTERTAINMENT to Spending(1, 762_000, 381_000),
            ),
            year.insights.byCategory.toList(),
        )
        assertEquals(
            listOf(
                flat to Spending(2, 18_000_000, 6_000_000),
                // 1, 4 and 10: 300,000 + 762,000 + 600,000; 100,000 + 381,000 + 0.
                null to Spending(3, 1_662_000, 481_000),
                trip to Spending(1, 800_000, 200_000),
            ),
            year.insights.byGroup.toList(),
        )
        assertEquals(
            listOf(
                listOf("You", 6, 20_462_000L, 6_681_000L, 6_681_000L),
                // 1, 2, 3, 6 and 10.
                listOf("Minh", 5, 19_700_000L, 6_300_000L, 6_600_000L),
                listOf("Khoa", 2, 18_000_000L, 6_000_000L, 6_000_000L),
                // 1, 4, 6 and 10: 300,000 + 762,000 + 800,000 + 600,000; 100,000 + 381,000 + 200,000 + 0;
                // and Lan's own 100,000 + 381,000 + 200,000 + 300,000.
                listOf("Lan", 4, 2_462_000L, 681_000L, 981_000L),
                listOf("An", 1, 800_000L, 200_000L, 200_000L),
            ),
            people(year),
        )
    }

    @Test
    fun `all time also counts the bills without a date, in every sum but those by month`() {
        val all = shown(InsightsPeriod.ALL_TIME)

        assertEquals("All time", all.periodName)
        assertEquals(11, all.bills.size)
        // Every bill but the euros and the baht: the year's, plus 7, 8 and 9: 2,400,000 + 500,000 + 1,200,000; 600,000 + 250,000 + 400,000.
        assertEquals(Spending(9, 24_562_000, 7_931_000), all.insights.overall)
        assertEquals(Spending(2, 1_700_000, 650_000), all.insights.undated)
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
                FOOD_AND_DRINK to Spending(2, 900_000, 100_000),
                TRANSPORT to Spending(1, 800_000, 200_000),
                ENTERTAINMENT to Spending(1, 762_000, 381_000),
                SHOPPING to Spending(1, 500_000, 250_000),
            ),
            all.insights.byCategory.toList(),
        )
        assertEquals(
            listOf(
                flat to Spending(3, 19_200_000, 6_400_000),
                trip to Spending(2, 3_200_000, 800_000),
                // 1, 4, 8 and 10: 300,000 + 762,000 + 500,000 + 600,000; 100,000 + 381,000 + 250,000 + 0.
                null to Spending(4, 2_162_000, 731_000),
            ),
            all.insights.byGroup.toList(),
        )
        assertEquals(
            listOf(
                listOf("You", 9, 24_562_000L, 7_931_000L, 7_931_000L),
                listOf("Minh", 7, 23_300_000L, 7_300_000L, 7_600_000L),
                listOf("Khoa", 3, 19_200_000L, 6_400_000L, 6_400_000L),
                listOf("Lan", 6, 5_362_000L, 1_531_000L, 1_831_000L),
                // Joined bills 6 and 7 from their links as "An" and "an": the same person, named as the latest bill does.
                listOf("An", 2, 3_200_000L, 800_000L, 800_000L),
            ),
            people(all),
        )
        assertEquals(
            listOf(
                "2 bills are left out of the totals, as no rate to VND is saved with them: 1 in EUR and 1 in THB.",
                "2 bills have no date that can be read, so they are in the totals but in no month.",
            ),
            all.notes,
        )
    }

    @Test
    fun `a saved bill counts with each person under a key of their own, and its rate only when it converts into the local currency`() {
        val saved = runBlocking { checkNotNull(bills.get("b6")) }

        val counted = insightBillOf(saved, "VND")

        assertEquals("2025-12-05", counted.date)
        assertEquals(TRANSPORT, counted.category)
        assertEquals(trip, counted.group)
        assertEquals(800_000L, counted.totalMinor)
        assertEquals(200_000L, counted.myShareMinor)
        // The user is "me" on every bill, and everyone else their name without case or accents, the
        // people the phone remembers as well as someone who joined from a link.
        assertEquals(
            listOf(
                InsightShare(ME_ID, "You", 200_000),
                InsightShare("name:minh", "Minh", 200_000),
                InsightShare("name:lan", "Lan", 200_000),
                InsightShare("name:an", "An", 200_000),
            ),
            counted.shares,
        )
        assertNull(counted.rate)
        assertEquals(SpendingFixtures.usdToVnd("25400"), insightBillOf(runBlocking { checkNotNull(bills.get("b4")) }, "VND").rate)
        // A rate from euros to dollars converts nothing into dong.
        assertNull(insightBillOf(runBlocking { checkNotNull(bills.get("b11")) }, "VND").rate)
    }

    @Test
    fun `someone forgotten and added again, or of the same name from a link, is one person in the insights`() {
        // Lan was forgotten, so the phone remembers her under a new id since, and "minh" joined the
        // newer bill from its link; the older one is with the Minh the phone remembers.
        val older = SpendingFixtures
            .finished("f1", "2026-11-01", "Phở Thìn", FOOD_AND_DRINK, 300_000, listOf("Lan", "Minh"))
            .withPersonIds("Lan" to "lan-forgotten", "Minh" to "minh-remembered")
        val newer = SpendingFixtures
            .finished("f2", "2026-11-02", "Bún chả", FOOD_AND_DRINK, 210_000, listOf("Lan"), strangers = listOf("minh"))
            .withPersonIds("Lan" to "lan-again")

        val people = Insights.of(listOf(newer, older).map { insightBillOf(it, "VND") }, SpendingFixtures.vnd).byPerson

        // 100,000 + 70,000 each, and Minh named as the newer bill names him.
        assertEquals(listOf("Lan", "You", "minh"), people.map { it.name })
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
        val written = runBlocking { Exporter(context).files(contentOf(checkNotNull(viewModel.state.value)), ExportFormat.CSV) }
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

        assertEquals(ExportMessage("Saved bills.csv, items.csv and shares.csv.", isError = false), viewModel.export.value.message)
        val written = runBlocking { Exporter(context).files(contentOf(checkNotNull(viewModel.state.value)), ExportFormat.CSV) }
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

        assertEquals(ExportMessage("Saved bills.csv, items.csv and shares.csv.", isError = false), restored.export.value.message)
        assertEquals(CsvExport.BILLS, File(context.cacheDir, "picked").list()?.sorted()?.first())
    }

    @Test
    fun `a period without bills says so, instead of exporting nothing without a word`() {
        // Four years on, no bill of the fixture is in this month.
        val later = Clock.fixed(LocalDate.of(2030, 11, 16).atTime(9, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        val empty = viewModelOn(later)

        empty.share(ExportFormat.CSV)
        awaitOnMain { empty.export.value.message != null }

        assertEquals(ExportMessage(NOTHING_TO_EXPORT, isError = true), empty.export.value.message)
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
        const val NOTHING_TO_EXPORT = "Nothing was exported, as this period has no finished bills."
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
