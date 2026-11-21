package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.export.ExportFixtures
import com.dantech.splitvibe.ui.editor.BillEditorContent
import com.dantech.splitvibe.ui.history.HistoryScreen
import com.dantech.splitvibe.ui.insights.ExportStatus
import com.dantech.splitvibe.ui.insights.InsightsActions
import com.dantech.splitvibe.ui.insights.InsightsPeriod
import com.dantech.splitvibe.ui.insights.InsightsScreen
import com.dantech.splitvibe.ui.insights.InsightsState
import com.dantech.splitvibe.ui.insights.insightsOf
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.recurring.RecurringRow
import com.dantech.splitvibe.ui.recurring.RepeatBillSheet
import com.dantech.splitvibe.ui.requests.RequestTotals
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screens of what the bills add up to, their export and the recurring bills, and the
 * bills' categories in the editor and the list of bills, with the fixtures' data, in the light and
 * the dark theme, the insights of this month and of all time, the insights, the recurring bills and
 * the list of bills with text at twice the size, and the dialogs that delete a bill and a recurring
 * bill and pick the day of a first draft, and compares them with the reference images in
 * `src/test/screenshots`. Record new images after an intended change with `-Pscreenshots=record`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TALL)
class SpendingGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 16)
    private val insights = insightsOf(SpendingFixtures.year, "VND", InsightsPeriod.LAST_12_MONTHS, SpendingFixtures.clock)
    private val thisMonth = insightsOf(SpendingFixtures.year, "VND", InsightsPeriod.THIS_MONTH, SpendingFixtures.clock)

    /** All time, which the fixture's bills of 2025 and 2026 chart by year. */
    private val allTime = insightsOf(SpendingFixtures.year, "VND", InsightsPeriod.ALL_TIME, SpendingFixtures.clock)

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_PAGE)
    fun `the insights of the last 12 months`() {
        shot("40-insights") { Insights() }
        assertWholePage()
    }

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_PAGE_AT_NIGHT)
    fun `the insights in the dark theme`() {
        shot("40-insights-dark") { Insights() }
        assertWholePage()
    }

    @Test
    fun `the insights with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("40-insights-large-text") { Insights() }
    }

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_PAGE)
    fun `the insights of this month, a single month without a chart`() {
        shot("40-insights-this-month") { Insights(thisMonth) }
        assertWholePage()
    }

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_PAGE)
    fun `the insights of all time, a bar and a line for each year`() {
        shot("40-insights-all-time") { Insights(allTime) }
        assertWholePage()
    }

    @Test
    fun `the export of the bills of a period`() = shot("41-export", open = EXPORT, scrollFirst = true) { Insights() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the export in the dark theme`() = shot("41-export-dark", open = EXPORT, scrollFirst = true) { Insights() }

    @Test
    fun `the recurring bills, one of them paused`() = shot("42-recurring", open = RECURRING) { Recurring() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the recurring bills in the dark theme`() = shot("42-recurring-dark", open = RECURRING) { Recurring() }

    @Test
    fun `the recurring bills with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("42-recurring-large-text", open = RECURRING) { Recurring() }
    }

    @Test
    fun `a finished bill made to recur from the list of bills`() = shot("43-repeat-bill", menu = RENT_MENU, open = REPEAT) { History() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `a bill made to recur in the dark theme`() = shot("43-repeat-bill-dark", menu = RENT_MENU, open = REPEAT) { History() }

    @Test
    fun `the category of a bill in the editor`() = shot("44-edit-bill-category") { Editor() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the category in the editor in the dark theme`() = shot("44-edit-bill-category-dark") { Editor() }

    @Test
    fun `the list of bills with their categories`() = shot("45-history-categories") { History() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the list of bills in the dark theme`() = shot("45-history-categories-dark") { History() }

    @Test
    fun `the list of bills with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("45-history-categories-large-text") { History() }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `deleting a bill from the list of bills`() = shot("45-delete-bill", menu = RENT_MENU, open = DELETE) { History() }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `deleting a recurring bill`() = shot(
        "42-delete-recurring-bill",
        open = RECURRING,
        then = {
            compose.onAllNodesWithContentDescription(RENT_MENU).onFirst().performClick()
            compose.onNode(hasText(DELETE) and hasClickAction()).performClick()
        },
    ) { Recurring() }

    /**
     * The calendar in which the day of the first draft of a recurring bill is picked, for a rent of a
     * year gone by. Material rings today in the calendar by the clock of the computer the test runs on,
     * so a calendar of the month the test runs in would change the picture on that day; no month of a
     * year gone by holds the day a test runs on.
     */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `picking the day of the first draft of a recurring bill`() = shot(
        "43-repeat-bill-first-draft",
        then = { compose.onNode(hasText("Change") and hasClickAction()).performClick() },
    ) {
        RepeatBillSheet(name = "Rent", date = "2025-11-01", today = LocalDate.of(2025, 11, 16), onRepeat = { _, _, _ -> }, onDismiss = {})
    }

    @Composable
    private fun Insights(state: InsightsState = insights) = InsightsScreen(
        state = state,
        export = ExportStatus(),
        actions = InsightsActions(
            onChoosePeriod = {},
            onShare = {},
            onSaveToFolder = {},
            onSaveTo = {},
            onShared = {},
            onPickerMissing = {},
            onDismissMessage = {},
        ),
        bottomBar = { AppNavBar(selected = InsightsKey, requests = RequestTotals.SignedOut, onSelect = {}, onSplitBill = {}) },
    )

    /** The list of bills on its Recurring tab, which [shot] opens. */
    @Composable
    private fun Recurring() {
        val rent = SpendingFixtures.year.first { it.id == "b2" }
        val flat = listOf("Minh", "Khoa")
        val internet = SpendingFixtures.finished("net", "2026-11-05", "Viettel", BillCategory.UTILITIES, 250_000, flat)
        val cleaning = SpendingFixtures.finished("clean", "2026-11-03", "Cleaning", BillCategory.OTHER, 300_000, flat)
        fun row(name: String, frequency: Frequency, start: String, bill: SavedBill, paused: Boolean = false): RecurringRow {
            val day = LocalDate.parse(start)
            val recurring = RecurringBill(name, name, RecurrenceSchedule(frequency, day), bill.bill, bill.plan, paused, 0, today)
            return RecurringRow(recurring, next = day, due = false)
        }
        Bills(
            recurring = listOf(
                row("Rent", Frequency.MONTHLY, "2026-12-31", rent),
                row("Internet", Frequency.MONTHLY, "2026-12-05", internet),
                row("Cleaning", Frequency.WEEKLY, "2026-11-17", cleaning, paused = true),
            ),
        )
    }

    @Composable
    private fun History() = Bills(recurring = emptyList())

    @Composable
    private fun Bills(recurring: List<RecurringRow>) = HistoryScreen(
        listings = SpendingFixtures.year.take(LISTED).map { saved ->
            BillListing(
                id = saved.id,
                status = saved.status.name,
                updatedAt = 0,
                merchant = saved.bill.merchant,
                date = saved.bill.date,
                currency = saved.bill.currency.code,
                fractionDigits = saved.bill.currency.fractionDigits,
                totalMinor = saved.bill.totalMinor,
                people = (listOf("You") + saved.plan.participants.drop(1).map { it.name }).joinToString(", "),
                category = saved.bill.category.wireValue,
            )
        } + BillListing("d1", BillStatus.DRAFT.name, 0, "Bia Hơi", "2026-11-14", "VND", 0, 240_000, "You, Lan", "food_and_drink"),
        query = "",
        onQueryChanged = {},
        onOpen = {},
        onDelete = {},
        onBack = {},
        recurring = recurring,
        recurringActions = RecurringActions(onPause = {}, onResume = {}, onDelete = {}),
        onRepeat = { _, _, _, _ -> },
        today = today,
    )

    @Composable
    private fun Editor() = BillEditorContent(bill = ExportFixtures.bills.first().bill, onEdit = {}, onDone = {})

    /**
     * Draws [content], opens the menu labelled [menu] and then taps [open] when they are given, does
     * [then], and compares the screen with the reference image [name]. With [scrollFirst] the list is
     * scrolled to [open] before the tap, as a lazy list holds only what is on the screen.
     */
    private fun shot(
        name: String,
        menu: String? = null,
        open: String? = null,
        scrollFirst: Boolean = false,
        then: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        menu?.let { compose.onAllNodesWithContentDescription(it).onFirst().performClick() }
        open?.let {
            if (scrollFirst) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(it))
            compose.onNode(hasText(it) and hasClickAction()).performClick()
        }
        then()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    /**
     * The picture is as tall as the window, so a page grown longer than the window would lose its end
     * without a word: its list must have nothing left to scroll to.
     */
    private fun assertWholePage() {
        val list = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertFalse("The insights run on below the bottom of the picture", list.value() < list.maxValue())
    }

    private companion object {
        /** How many of the fixture's bills the list of bills shows, as many as fit on the screen. */
        const val LISTED = 7
        const val RENT_MENU = "More for Rent"
        const val REPEAT = "Repeat this bill"
        const val RECURRING = "Recurring"
        const val EXPORT = "Export these bills"
        const val DELETE = "Delete"
    }
}

/** A phone screen, as large as a dialog shows on. */
private const val PHONE = "w412dp-h915dp-xhdpi"
private const val TALL = "w412dp-h1200dp-xhdpi"
private const val TALL_AT_NIGHT = "w412dp-h1200dp-night-xhdpi"

/** Tall enough to show the whole of the insights at once, down to the export at the end, above the navigation bar. */
private const val WHOLE_PAGE = "w412dp-h2600dp-xhdpi"
private const val WHOLE_PAGE_AT_NIGHT = "w412dp-h2600dp-night-xhdpi"
