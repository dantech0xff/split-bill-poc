package com.dantech.splitvibe.ui.split

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.bills.SavedBill
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.ME_ID
import com.dantech.splitvibe.domain.Participant
import com.dantech.splitvibe.domain.Payment
import com.dantech.splitvibe.domain.Person
import com.dantech.splitvibe.domain.SplitMode
import com.dantech.splitvibe.domain.SplitPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Parts of the split screens on their own: the sheet that adds someone, the tiles that add a bill, a
 * person's row in a sheet, and the sheet of the rate.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SplitScreenPartsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each suggested person's button says to a screen reader whom it adds`() {
        var added: Pair<String, String?>? = null
        compose.setContent {
            SplitVibeTheme {
                AddPersonSheet(
                    people = listOf(Person("p1", "Bao Tran"), Person("p2", "Bao Ngoc")),
                    onAdd = { name, id -> added = name to id },
                    onPickContact = {},
                    contactsMissing = false,
                    onForget = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNode(hasContentDescription("Add Bao Ngoc") and hasClickAction()).assertExists()
        compose.onNode(hasContentDescription("Add Bao Tran") and hasClickAction()).performClick()

        assertEquals("Bao Tran" to "p1", added)
    }

    @Test
    // How many lines a label takes depends on how wide it is drawn, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = NARROW_PHONE)
    fun `the three ways to add a bill are tiles of one height, that of the tallest`() {
        compose.setContent {
            SplitVibeTheme {
                NewSplitContent(
                    state = SplitUiState(),
                    aiStatus = AiStatus.AVAILABLE,
                    newCameraTarget = { Uri.EMPTY },
                    scanIntent = { null },
                    onBack = {},
                    onPagesPicked = {},
                    onSamplePicked = {},
                    onManualEntry = {},
                    onInstructionChanged = {},
                    onAnalyze = {},
                    onCancel = {},
                    onOpenSettings = {},
                    onOpenPro = {},
                )
            }
        }

        val labels = listOf("Scan the bill", "Take photo", "Choose photos")
        // On a narrow phone some labels take two lines and some one, which is what would make the tiles differ.
        val lines = labels.map { label -> layoutOf(compose.onNode(hasText(label), useUnmergedTree = true)).lineCount }
        assertTrue("The labels take $lines lines, so this proves nothing", lines.distinct().size > 1)
        val heights = labels.map { label ->
            compose.onNode(hasText(label) and hasClickAction()).getBoundsInRoot().let { (it.bottom - it.top).value }
        }
        heights.forEach { height -> assertEquals("The tiles are $heights tall", heights.max(), height, BOUNDS_SLACK) }
    }

    @Test
    fun `a person's row of a sheet holds the name alone for a way of dividing without a part per person`() {
        compose.setContent {
            SplitVibeTheme {
                Column {
                    DivisionValueRow(name = "Lan", mode = SplitMode.EQUAL, value = 0, currency = VND, onValue = {})
                    DivisionValueRow(name = "Minh", mode = SplitMode.BY_ITEM, value = 0, currency = VND, onValue = {})
                }
            }
        }

        compose.onNodeWithText("Lan").assertExists()
        compose.onNodeWithText("Minh").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    /**
     * The sheet of the rate says what the bill comes to at the rate as it is typed, so that a rate
     * typed a thousand times off, as "25.412" for 25,412 dong to the dollar, shows at once.
     */
    @Test
    fun `the sheet of the rate says what the bill comes to at the rate being typed`() {
        val dinner = SplitResultState(
            SavedBill(
                id = "b1",
                createdAt = 1,
                updatedAt = 1,
                bill = Bill("Dinner", "2026-11-14", BillCurrency("USD", 2), emptyList(), emptyList(), printedTotalMinor = 4_200),
                plan = SplitPlan(participants = listOf(Participant.ME), payments = listOf(Payment(ME_ID, 4_200))),
            ),
            localCurrency = "VND",
        )
        compose.setContent {
            SplitVibeTheme {
                ExchangeRateSheet(
                    from = "USD",
                    to = "VND",
                    rate = null,
                    totalAt = { typed -> dinner.convertedAt(typed) },
                    onSave = {},
                    onUseDayRate = {},
                    onDismiss = {},
                )
            }
        }
        compose.onAllNodes(hasText("About", substring = true)).assertCountEquals(0)

        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("25.412")
        compose.onNodeWithText("About ${VND.format(1_067)}").assertExists()
        field.performTextReplacement("25412")
        compose.onNodeWithText("About ${VND.format(1_067_304)}").assertExists()
    }

    /** How the text of [node] is laid out: its lines as drawn. */
    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}

/** A phone narrower than most, on which the longest label of the tiles takes two lines. */
private const val NARROW_PHONE = "w320dp-h800dp-xhdpi"

private val VND = BillCurrency("VND", 0)

private const val BOUNDS_SLACK = 1f
