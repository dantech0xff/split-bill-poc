package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.contentWords
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
import com.dantech.splitvibe.ui.insights.insightsOf
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.recurring.RecurringRow
import com.dantech.splitvibe.ui.recurring.RecurringScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screens of what the bills add up to, their export and the recurring bills, and the
 * bills' categories in the editor and the list of bills, with the fixtures' data, in the light and
 * the dark theme, and compares them with the reference images in `src/test/screenshots`. Record
 * new images after an intended change with `-Pscreenshots=record`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = TALL)
class SpendingGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 16)
    private val insights = insightsOf(SpendingFixtures.year, "VND", InsightsPeriod.LAST_12_MONTHS, SpendingFixtures.clock, contentWords())

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_PAGE)
    fun `the insights of the last 12 months`() = shot("40-insights") { Insights() }

    @Test
    @Config(sdk = [34], qualifiers = WHOLE_PAGE_AT_NIGHT)
    fun `the insights in the dark theme`() = shot("40-insights-dark") { Insights() }

    @Test
    fun `the export of the bills of a period`() = shot("41-export", open = "Export these bills") { Insights() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the export in the dark theme`() = shot("41-export-dark", open = "Export these bills") { Insights() }

    @Test
    fun `the recurring bills, one of them paused`() = shot("42-recurring") { Recurring() }

    @Test
    @Config(sdk = [34], qualifiers = TALL_AT_NIGHT)
    fun `the recurring bills in the dark theme`() = shot("42-recurring-dark") { Recurring() }

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

    @Composable
    private fun Insights() = InsightsScreen(
        state = insights,
        export = ExportStatus(),
        actions = InsightsActions(
            onChoosePeriod = {},
            onShare = {},
            onSaveToFolder = {},
            onSaveTo = {},
            onShared = {},
            onPickerMissing = {},
            onDismissMessage = {},
            onBack = {},
        ),
    )

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
        RecurringScreen(
            rows = listOf(
                row("Rent", Frequency.MONTHLY, "2026-12-31", rent),
                row("Internet", Frequency.MONTHLY, "2026-12-05", internet),
                row("Cleaning", Frequency.WEEKLY, "2026-11-17", cleaning, paused = true),
            ),
            actions = RecurringActions(onPause = {}, onResume = {}, onDelete = {}, onBack = {}),
        )
    }

    @Composable
    private fun History() = HistoryScreen(
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
        onRepeat = { _, _, _, _ -> },
        today = today,
    )

    @Composable
    private fun Editor() = BillEditorContent(bill = ExportFixtures.bills.first().bill, onEdit = {}, onDone = {})

    /**
     * Draws [content], opens the menu labelled [menu] and then taps [open] when they are given, and
     * compares the screen with the reference image [name].
     */
    private fun shot(name: String, menu: String? = null, open: String? = null, content: @Composable () -> Unit) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        menu?.let { compose.onAllNodesWithContentDescription(it).onFirst().performClick() }
        open?.let { compose.onNode(hasText(it) and hasClickAction()).performClick() }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    private companion object {
        /** How many of the fixture's bills the list of bills shows, as many as fit on the screen. */
        const val LISTED = 7
        const val RENT_MENU = "More for Rent"
        const val REPEAT = "Repeat this bill"
    }
}

private const val TALL = "w412dp-h1200dp-xhdpi"
private const val TALL_AT_NIGHT = "w412dp-h1200dp-night-xhdpi"

/** Tall enough to show every breakdown of the insights at once, above the bottom bar. */
private const val WHOLE_PAGE = "w412dp-h2000dp-xhdpi"
private const val WHOLE_PAGE_AT_NIGHT = "w412dp-h2000dp-night-xhdpi"
