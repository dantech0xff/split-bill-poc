package com.dantech.splitvibe.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Where the focus goes as the rows of the bill editor open: to the first field of a row a tap or an
 * addition opened, which the list brings into view first, so that a screen reader and the keyboard go
 * on with the person; and nowhere when the screen comes back with a row open.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class BillEditorFocusTest {

    @get:Rule
    val compose = createComposeRule()

    private var bill by mutableStateOf(BILL)
    private val restoration = StateRestorationTester(compose)

    @Before
    fun setUp() = restoration.setContent {
        SplitVibeTheme { BillEditorContent(bill = bill, onEdit = { change -> bill = change(bill) }, onDone = {}) }
    }

    @Test
    fun `a row opened with a tap gives its first field the focus`() {
        open("Trà đá")

        compose.onNode(ITEM_FIELD).assert(hasText("Trà đá")).assertIsFocused()
    }

    @Test
    fun `an item or a charge added opens with its first field focused`() {
        tap(hasText("Add an item") and hasClickAction())
        compose.onNode(ITEM_FIELD).assert(hasText("Item")).assertIsFocused()
        assertEquals("the new item", 4, bill.items.size)

        tap(hasText("Add tax") and hasClickAction())
        // A charge's first field is its label, which is labelled with the kind of charge.
        compose.onNode(hasSetTextAction() and isFocused()).assert(hasText("Tax"))
        assertEquals("the new tax", 1, bill.charges.size)
    }

    /**
     * With the keyboard open, what the list shows ends just below the row that adds an item, as on a
     * small phone. The whole new row comes into view, down to its amount, not only the field with the
     * focus, which a list would scroll to by itself.
     */
    @Test
    @Config(qualifiers = "w412dp-h480dp-xhdpi")
    fun `an item added below what the list shows is brought into view whole, with its name focused`() {
        val list = compose.onNode(hasScrollToIndexAction())
        val addItem = hasText("Add an item") and hasClickAction()
        list.performScrollToNode(addItem)
        // The row that adds an item ends where the list does, so the new row would open below it.
        val frame = list.unclippedBounds()
        val adding = compose.onNode(addItem).unclippedBounds()
        assertEquals("\"Add an item\" is not at the bottom of the list", frame.bottom.value, adding.bottom.value, BOUNDS_SLACK)

        compose.onNode(addItem).performSemanticsAction(SemanticsActions.OnClick)

        val name = compose.onNode(ITEM_FIELD).assertIsFocused().unclippedBounds()
        val amount = compose.onNode(hasSetTextAction() and hasText("Amount")).unclippedBounds()
        assertTrue("The new item's name lies at ${name.top} to ${name.bottom}, beyond the list", name.top >= frame.top)
        assertTrue("The new item's amount ends at ${amount.bottom}, below the list at ${frame.bottom}", amount.bottom <= frame.bottom)
    }

    @Test
    fun `a row open again once the screen comes back leaves the focus alone`() {
        open("Trà đá")
        compose.onNode(ITEM_FIELD).assertIsFocused()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNode(ITEM_FIELD).assert(hasText("Trà đá")).assertIsNotFocused()
    }

    /** Opens the closed row of the item called [name] with a tap. */
    private fun open(name: String) = tap(hasText(name) and hasClickAction())

    private fun tap(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
    }

    /** Where this node lies on the screen as large as it is, not cut to what clips it, such as the list it scrolls in. */
    private fun SemanticsNodeInteraction.unclippedBounds(): DpRect {
        val node = fetchSemanticsNode()
        return with(compose.density) {
            DpRect(
                left = node.positionInRoot.x.toDp(),
                top = node.positionInRoot.y.toDp(),
                right = (node.positionInRoot.x + node.size.width).toDp(),
                bottom = (node.positionInRoot.y + node.size.height).toDp(),
            )
        }
    }
}

/** The name field of the one item open in the editor. */
private val ITEM_FIELD = hasSetTextAction() and hasText("Item")

private const val BOUNDS_SLACK = 1f

private val BILL = Bill(
    merchant = "Quán Ốc Ba Miền",
    date = "2026-11-13",
    currency = BillCurrency("VND", 0),
    items = listOf(
        BillItem("i1", "Ốc hương rang muối", BigDecimal.ONE, 165_000, 165_000),
        BillItem("i2", "Nghêu hấp sả", BigDecimal.ONE, 95_000, 95_000),
        BillItem("i3", "Trà đá", BigDecimal(2), 5_000, 10_000),
    ),
    charges = emptyList(),
    printedTotalMinor = null,
)
