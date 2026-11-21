package com.dantech.splitvibe.ui.history

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.data.bills.BillStatus
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.recurring.RecurringActions
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
    fun `the search field shows no label above it, and reads as what it searches while nothing is typed`() {
        show()

        compose.onNode(hasSetTextAction() and hasText(SEARCH)).assertExists()
    }

    @Test
    fun `a row of the list names the day, the category and the people in words, and a draft says it is one`() {
        show()

        compose.onNode(hasText("Nov 15 · Food & drink · You, Minh, Lan")).assertExists()
        compose.onNode(hasText("Bia Hơi") and hasText("Draft")).assertExists()
    }

    private fun show() = compose.setContent {
        SplitVibeTheme {
            HistoryScreen(
                listings = listOf(
                    listing("b1", BillStatus.FINAL, "Bún chả", "2026-11-15", 600_000, "You, Minh, Lan"),
                    listing("d1", BillStatus.DRAFT, "Bia Hơi", "2026-11-14", 240_000, "You, Lan"),
                ),
                query = "",
                onQueryChanged = {},
                onOpen = {},
                onDelete = {},
                onBack = {},
                recurring = emptyList(),
                recurringActions = RecurringActions(onPause = {}, onResume = {}, onDelete = {}),
                onRepeat = { _, _, _, _ -> },
                today = LocalDate.of(2026, 11, 16),
            )
        }
    }

    /** A bill of food and drink in dong, as the list of bills reads it from the phone. */
    private fun listing(id: String, status: BillStatus, merchant: String, date: String, totalMinor: Long, people: String) =
        BillListing(id, status.name, 0, merchant, date, "VND", 0, totalMinor, people, "food_and_drink")

    private companion object {
        const val ALL_BILLS = "All bills"
        const val RECURRING = "Recurring"
        const val SEARCH = "Search by place, item or person"
        const val RECURRING_NONE = "No recurring bills yet."

        /** One of the tabs of the list, which a screen reader reads as a tab, as it reads those of the navigation bar. */
        fun tab(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    }
}
