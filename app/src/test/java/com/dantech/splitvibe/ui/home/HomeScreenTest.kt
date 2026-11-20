package com.dantech.splitvibe.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.HomeFixtures
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.requests.RequestTotals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the parts of home do when tapped, how a screen reader reads its main block, and what home
 * shows while the bills are still being read.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tapped = mutableListOf<String>()

    @Test
    fun `the line of what the user owes, the month and the group lead where they say, the group by its own id`() {
        show(HomeFixtures.owed)

        compose.onNode(hasText(YOU_OWE_DANA) and hasClickAction()).performClick()
        compose.onNode(hasText("November") and hasClickAction()).performClick()
        compose.onNode(hasText("Chiang Mai trip") and hasClickAction()).performClick()

        assertEquals(listOf("requests", "insights", "group ${GroupFixtures.GROUP_ID}"), tapped)
    }

    @Test
    fun `the block of what friends owe reads as one element, and the line of what the user owes as a button of its own`() {
        show(HomeFixtures.owed)

        val block = hasText("Friends owe you") and hasText("2 requests") and hasContentDescription("₫651,671") and hasText("Minh and Lan")
        compose.onNode(block).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onAllNodes(hasText("Friends owe you") and hasText(YOU_OWE_DANA)).assertCountEquals(0)
        compose.onNode(hasText(YOU_OWE_DANA)).assert(hasRole(Role.Button))
    }

    @Test
    fun `when the user only owes, the whole block is one button to the requests, which says what a tap does`() {
        show(HomeFixtures.owing, group = null)

        val block = compose.onNode(hasText("You owe") and hasText("1 request") and hasContentDescription("₫150,000") and hasText("Dana"))
        block.assert(hasRole(Role.Button))
        block.assert(
            SemanticsMatcher("tapped to open the requests") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == OPEN_REQUESTS
            },
        )
        block.performClick()

        assertEquals(listOf("requests"), tapped)
    }

    @Test
    fun `while the bills are still being read, home says neither that there are none nor that there are more`() {
        var bills by mutableStateOf<List<BillListing>?>(null)
        compose.setContent {
            SplitVibeTheme {
                HomeScreen(
                    summary = HomeSummary.of(RequestTotals.SignedOut, bills, month = null, localCurrency = "VND"),
                    group = null,
                    bills = bills,
                    onOpenBill = {},
                    onSeeAllBills = {},
                    onOpenSettings = {},
                    onOpenRequests = {},
                    onOpenInsights = {},
                    onOpenGroup = {},
                    onSplitBill = {},
                    today = HomeFixtures.today,
                )
            }
        }

        compose.onNodeWithText("Recent bills").assertExists()
        compose.onAllNodesWithText(NO_BILLS).assertCountEquals(0)
        compose.onAllNodesWithText("See all").assertCountEquals(0)
        compose.onAllNodesWithText("Split a bill with AI").assertCountEquals(0)

        // Read, and none: now home says so, and invites to split a first one.
        bills = emptyList()
        compose.onNodeWithText(NO_BILLS).assertExists()
        compose.onNodeWithText("Split a bill with AI").assertExists()
        compose.onAllNodesWithText("See all").assertCountEquals(0)
    }

    /** Home with [requests] and the bills, month and group of the approved design, noting each tap in [tapped]. */
    private fun show(requests: RequestTotals, group: GroupTile? = HomeFixtures.group) {
        compose.setContent {
            SplitVibeTheme {
                HomeScreen(
                    summary = HomeSummary.of(requests, HomeFixtures.bills, HomeFixtures.month, localCurrency = "VND"),
                    group = group,
                    bills = HomeFixtures.bills,
                    onOpenBill = { tapped += "bill $it" },
                    onSeeAllBills = { tapped += "all bills" },
                    onOpenSettings = { tapped += "settings" },
                    onOpenRequests = { tapped += "requests" },
                    onOpenInsights = { tapped += "insights" },
                    onOpenGroup = { tapped += "group $it" },
                    onSplitBill = { tapped += "split" },
                    myName = HomeFixtures.MY_NAME,
                    today = HomeFixtures.today,
                )
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private companion object {
        const val YOU_OWE_DANA = "You owe ₫150,000 to Dana"
        const val OPEN_REQUESTS = "Open your requests"
        const val NO_BILLS = "Bills you split are kept here, also without a connection."
    }
}
