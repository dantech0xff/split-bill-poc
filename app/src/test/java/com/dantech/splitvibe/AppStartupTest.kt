package com.dantech.splitvibe

import android.os.Looper
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.bills.BillRepository
import com.dantech.splitvibe.data.recurring.RecurringBillRepository
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Starts the real application class and the real activity, as the launcher would, and moves
 * between the screens. It fails if the manifest, the dependency module and the navigation
 * no longer fit together.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xhdpi")
class AppStartupTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app starts on the home screen and moves between screens`() {
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()

        // The round button in the middle of the navigation bar shows no text, only what it says to a screen reader.
        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.onNodeWithText("New split").assertIsDisplayed()

        // The arrow in the top bar, as opposed to the system back button.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Local currency").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
    }

    @Test
    fun `each tab of the navigation bar opens its screen in place of the one before, and shows that it is open`() {
        compose.onNode(tab("Home")).assertIsSelected()

        for ((name, title) in listOf("Groups" to "Groups", "Requests" to "Requests", "Insights" to "Insights")) {
            compose.onNode(tab(name)).performClick()

            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNode(tab(name)).assertIsSelected()
            TABS.filter { it != name }.forEach { other -> compose.onNode(tab(other)).assertIsNotSelected() }
        }
        // The tab that is open stays as it is when it is chosen again.
        compose.onNode(tab("Insights")).performClick()
        compose.onNodeWithText("Insights").assertIsDisplayed()

        // Each tab took the place of the one before it, so one back goes home, whatever tabs were open.
        pressBack()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNode(tab("Home")).assertIsSelected()

        // Home, chosen from a tab, has nothing under it to go back to: back then leaves the app.
        compose.onNode(tab("Requests")).performClick()
        compose.onNode(tab("Home")).performClick()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        var backIsTheApps = true
        compose.activityRule.scenario.onActivity { backIsTheApps = it.onBackPressedDispatcher.hasEnabledCallbacks() }
        assertFalse("Back on home stays in the app", backIsTheApps)
    }

    @Test
    fun `Settings opens the Pro screen, and back returns to Settings`() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNode(hasText("Get Pro") and hasClickAction()).performClick()
        compose.onNodeWithText("Split Vibe Pro").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Local currency").assertIsDisplayed()
    }

    @Test
    fun `a second start in the same process works`() {
        // Each test gets a new application object; the dependency container must cope with that.
        compose.onNodeWithText("Split Vibe").assertIsDisplayed()
    }

    @Test
    fun `the open screens come back after the system recreates the activity`() {
        compose.onNodeWithContentDescription("Split a bill").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("New split").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Local currency").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
    }

    @Test
    fun `the open tab comes back after the system recreates the activity, and back still goes home`() {
        compose.onNode(tab("Requests")).performClick()
        compose.onNodeWithText("Requests").assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Requests").assertIsDisplayed()
        compose.onNode(tab("Requests")).assertIsSelected()
        pressBack()
        compose.onNodeWithText("Recent bills").assertIsDisplayed()
        compose.onNode(tab("Home")).assertIsSelected()
    }

    @Test
    fun `with only a recurring bill left on the phone, home still leads to it`() {
        // Nothing to see yet, so nothing to see all of.
        compose.onAllNodesWithText("See all").assertCountEquals(0)

        // The rent recurs, and the bill it was made from is deleted, as a recurring bill outlives it.
        runBlocking {
            val koin = GlobalContext.get()
            val bills = koin.get<BillRepository>()
            val rent = bills.save(SpendingFixtures.finished("rent", "2026-11-01", "Rent", BillCategory.RENT, 9_000_000, listOf("Minh")))
            // Its first draft is a month away, so none is due while the test runs.
            val firstDraft = LocalDate.now().plusMonths(1)
            koin.get<RecurringBillRepository>().create(rent, "Rent", RecurrenceSchedule(Frequency.MONTHLY, firstDraft), firstDraft)
            bills.delete(rent.id)
        }
        // Home lists no bill, and still offers to see them all.
        await { shown(NO_BILLS) && shown("See all") }
        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithText("All bills").assertIsOn()
        val choice = compose.onNodeWithText("Recurring").fetchSemanticsNode().id
        compose.onNodeWithText("Recurring").performClick()

        compose.onNodeWithText("Recurring").assertIsOn()
        compose.onNodeWithText("All bills").assertIsOff()
        // The choice of tab is still the element it was, so a screen reader and the keyboard keep their place on it.
        assertEquals(choice, compose.onNodeWithText("Recurring").fetchSemanticsNode().id)
        await { shown("Rent") }
        compose.onNodeWithText("Rent").assertIsDisplayed()

        // The open tab is still open once the system made the activity again.
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Recurring").assertIsOn()
        await { shown("Rent") }
        compose.onNodeWithText("Rent").assertIsDisplayed()
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Waits for work that finishes on a background thread, such as the database, and reports back on the main looper. */
    private fun await(condition: () -> Boolean) = compose.waitUntil(TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

/** The tabs of the navigation bar, as a screen reader names them. */
private val TABS = listOf("Home", "Groups", "Requests", "Insights")

/** The tab of the navigation bar called [name], as opposed to the title of the screen it opens. */
private fun tab(name: String) = hasContentDescription(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/** What home says in place of the bills when the phone keeps none. */
private const val NO_BILLS = "Bills you split are kept here, also without a connection."
private const val TIMEOUT_MS = 60_000L
