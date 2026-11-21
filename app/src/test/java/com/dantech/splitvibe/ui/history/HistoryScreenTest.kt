package com.dantech.splitvibe.ui.history

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.data.recurring.RecurringBill
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import com.dantech.splitvibe.ui.recurring.RecurringActions
import com.dantech.splitvibe.ui.recurring.RecurringRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The list of bills as someone who hears the screen uses it: its two tabs, its search and its rows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the two tabs read as tabs, say which is open, and the tab just chosen stays the element it was`() {
        show()
        compose.onNode(tab(ALL_BILLS)).assertIsSelected()
        val recurring = compose.onNode(tab(RECURRING)).assertIsNotSelected()
        val chosen = recurring.fetchSemanticsNode().id

        recurring.performClick()

        compose.onNode(tab(RECURRING)).assertIsSelected()
        compose.onNode(tab(ALL_BILLS)).assertIsNotSelected()
        // The same element, so the focus of a screen reader and of the keyboard stays on it.
        assertEquals(chosen, compose.onNode(tab(RECURRING)).fetchSemanticsNode().id)
        // Searching finds bills only, so the recurring bills have no field to search them with.
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText(RECURRING_NONE, substring = true).assertExists()
    }

    @Test
    fun `back on All bills the search field is at the top again, and the tab just chosen stays the element it was`() {
        // More bills than the screen holds, so the list could keep the field above its top.
        show(listings = (1..BILLS_TO_SCROLL).map { listing("b$it", BillStatus.FINAL, "Bún chả $it", "2026-11-15", 600_000, "You, Minh") })
        compose.onNode(tab(RECURRING)).performClick()
        val all = compose.onNode(tab(ALL_BILLS))
        val chosen = all.fetchSemanticsNode().id

        all.performClick()

        compose.onNode(hasSetTextAction() and hasText(SEARCH)).assertIsDisplayed()
        assertEquals(chosen, compose.onNode(tab(ALL_BILLS)).fetchSemanticsNode().id)
    }

    @Test
    // How tall the field is depends on how its text is really laid out, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the search field shows no label above it, and reads as what it searches while nothing is typed`() {
        show()

        val field = compose.onNode(hasSetTextAction() and hasText(SEARCH)).getBoundsInRoot()
        // As tall as the box of a field of one line: a label would stand above the box, as part of the field.
        assertEquals(FIELD_BOX_HEIGHT, field.bottom - field.top)
    }

    @Test
    fun `a row of the list names the day, the category and the people in words, and a draft says it is one`() {
        show()

        compose.onNode(hasText("Nov 15 · Food & drink · You, Minh, Lan")).assertExists()
        compose.onNode(hasText("Bia Hơi") and hasText("Draft")).assertExists()
    }

    @Test
    // Whether a word fits beside a total depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = PHONE)
    fun `with text at twice the size, the total of a recurring bill goes under a name whose longest word would not fit beside it`() {
        RuntimeEnvironment.setFontScale(2f)
        show(recurring = listOf(recurring(INTERNET, INTERNET_TOTAL)))
        compose.onNode(tab(RECURRING)).performClick()

        val name = compose.onNode(hasText(INTERNET), useUnmergedTree = true)
        val total = compose.onNode(hasText(SpendingFixtures.vnd.format(INTERNET_TOTAL)), useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The total is not under the name", total.top >= name.getBoundsInRoot().bottom)
        assertEquals("The name is broken inside its word", 1, layoutOf(name).lineCount)
    }

    private fun show(listings: List<BillListing> = TWO_BILLS, recurring: List<RecurringRow> = emptyList()) = compose.setContent {
        SplitVibeTheme {
            HistoryScreen(
                listings = listings,
                query = "",
                onQueryChanged = {},
                onOpen = {},
                onDelete = {},
                onBack = {},
                recurring = recurring,
                recurringActions = RecurringActions(onPause = {}, onResume = {}, onDelete = {}),
                onRepeat = { _, _, _, _ -> },
                today = TODAY,
            )
        }
    }

    /** A bill called [name] of [totalMinor] dong that comes back on the 5th of every month. */
    private fun recurring(name: String, totalMinor: Long): RecurringRow {
        val bill = SpendingFixtures.finished("net", "2026-11-05", name, BillCategory.UTILITIES, totalMinor, listOf("Minh"))
        val start = LocalDate.of(2026, 12, 5)
        val schedule = RecurrenceSchedule(Frequency.MONTHLY, start)
        return RecurringRow(RecurringBill(name, name, schedule, bill.bill, bill.plan, paused = false, 0, TODAY), next = start, due = false)
    }

    /** How the text of [node] is laid out. */
    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private companion object {
        const val ALL_BILLS = "All bills"
        const val RECURRING = "Recurring"
        const val SEARCH = "Search by place, item or person"
        const val RECURRING_NONE = "No recurring bills yet."
        const val INTERNET = "Internet"
        const val INTERNET_TOTAL = 250_000L
        const val BILLS_TO_SCROLL = 20
        val TODAY: LocalDate = LocalDate.of(2026, 11, 16)

        /** A phone of common size, as the screen galleries draw. */
        const val PHONE = "w412dp-h915dp-xhdpi"

        /** The box of a field of one line, as the approved designs draw it. */
        val FIELD_BOX_HEIGHT = 52.dp

        val TWO_BILLS = listOf(
            listing("b1", BillStatus.FINAL, "Bún chả", "2026-11-15", 600_000, "You, Minh, Lan"),
            listing("d1", BillStatus.DRAFT, "Bia Hơi", "2026-11-14", 240_000, "You, Lan"),
        )

        /** A bill of food and drink in dong, as the list of bills reads it from the phone. */
        fun listing(id: String, status: BillStatus, merchant: String, date: String, totalMinor: Long, people: String) =
            BillListing(id, status.name, 0, merchant, date, "VND", 0, totalMinor, people, "food_and_drink")

        /** One of the tabs of the list, which a screen reader reads as a tab, as it reads those of the navigation bar. */
        fun tab(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    }
}
