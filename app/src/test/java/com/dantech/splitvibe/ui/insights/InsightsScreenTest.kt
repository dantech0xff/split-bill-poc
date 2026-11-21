package com.dantech.splitvibe.ui.insights

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.SpendingFixtures
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.text.UiText
import com.dantech.splitvibe.ui.AppNavBar
import com.dantech.splitvibe.ui.InsightsKey
import com.dantech.splitvibe.ui.requests.RequestTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The insights as a main screen with the navigation bar under them, on a phone: the export at the end
 * of the list and how it ended, the notes on what the totals leave out, the chart of the months, and
 * the lines of a breakdown with large text.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class InsightsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the export is a button at the end of the list, which never lies under the navigation bar`() {
        show(InsightsPeriod.LAST_12_MONTHS)
        // The bar is the pill that holds the round button in its middle.
        val bar = compose.onNodeWithContentDescription(SPLIT_A_BILL).onParent().getBoundsInRoot()
        // The list itself ends above the bar: padding inside it would only lift its last item above the bar,
        // while every other item still scrolled under it.
        val list = compose.onNode(hasScrollToIndexAction())
        val frame = list.getBoundsInRoot()
        assertTrue("The list runs on to ${frame.bottom}, under the top of the bar at ${bar.top}", frame.bottom <= bar.top)

        list.performScrollToNode(hasText(EXPORT))

        val export = compose.onNode(hasText(EXPORT) and hasClickAction())
        val button = export.getBoundsInRoot()
        assertTrue("The export ends at ${button.bottom}, below the top of the bar at ${bar.top}", button.bottom <= bar.top)
        export.performClick()
        compose.onNodeWithText("Save to files").assertExists()
    }

    @Test
    fun `how an export ended shows right above its button, and is read out as it appears`() {
        show(InsightsPeriod.LAST_12_MONTHS)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(EXPORT))

        export = ExportStatus(message = ExportMessage(UiText.Raw(NOT_SAVED), isError = true))

        compose.onNode(hasText(NOT_SAVED))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    // Whether a word fits beside an amount depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `with text at twice the size, an amount goes under a name whose longest word would not fit beside it`() {
        RuntimeEnvironment.setFontScale(2f)
        show(InsightsPeriod.LAST_12_MONTHS)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(ENTERTAINMENT))

        val name = compose.onNode(hasText(ENTERTAINMENT), useUnmergedTree = true)
        val amount = compose.onNode(hasText(SpendingFixtures.vnd.format(ENTERTAINMENT_TOTAL)), useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The amount is not under the name", amount.top >= name.getBoundsInRoot().bottom)
        assertEquals("The name is broken inside its word", 1, layoutOf(name).lineCount)
    }

    @Test
    fun `the notes on the bills the totals leave out stay`() {
        show(InsightsPeriod.LAST_12_MONTHS)

        compose.onNodeWithText("2 bills are left out of the totals", substring = true).assertExists()
        compose.onNodeWithText("2 bills have no date that can be used", substring = true).assertExists()
    }

    @Test
    fun `the chart of the months says in words which months were the highest`() {
        show(InsightsPeriod.LAST_12_MONTHS)

        compose.onNode(hasContentDescription(CHART_OF_THE_YEAR)).assertExists()
    }

    @Test
    fun `a period of one month has no chart, which would compare the month with nothing`() {
        show(InsightsPeriod.THIS_MONTH)

        compose.onNodeWithText("By month").assertExists()
        compose.onNode(hasContentDescription("Spending by month", substring = true)).assertDoesNotExist()
    }

    /** How the last export stands, which the screen shows as it changes. */
    private var export by mutableStateOf(ExportStatus())

    private fun show(period: InsightsPeriod) {
        val state = insightsOf(SpendingFixtures.year, "VND", period, SpendingFixtures.clock)
        compose.setContent {
            SplitVibeTheme {
                InsightsScreen(
                    state = state,
                    export = export,
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
            }
        }
    }

    /** How the text of [node] is laid out. */
    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private companion object {
        const val EXPORT = "Export these bills"
        const val NOT_SAVED = "The files could not be saved."
        const val ENTERTAINMENT = "Entertainment"

        /** What the bills of entertainment of the last 12 months add up to: the $30.00 of bill 4 at the day's rate. */
        const val ENTERTAINMENT_TOTAL = 762_024L
        const val SPLIT_A_BILL = "Split a bill"
        const val CHART_OF_THE_YEAR =
            "Spending by month from December 2025 to November 2026. November 2026 is the highest, then October 2026."
    }
}
