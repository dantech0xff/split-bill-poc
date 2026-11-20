package com.dantech.splitvibe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvFabMenu
import com.dantech.splitvibe.designsystem.components.SvFabMenuItem
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvPersonChip
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSmallAction
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the design system components do when they are tapped, how a screen reader reads them, and what they leave in view. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComponentBehaviourTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a choice group selects the tapped option and reads as radio buttons`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            SplitVibeTheme { SvChoiceGroup(options = listOf("Equally", "By item"), selected = selected, onSelect = { selected = it }) }
        }

        compose.onNodeWithText("By item").performClick()

        assertEquals(1, selected)
        compose.onNodeWithText("By item")
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `choice chips select the tapped option and read as radio buttons`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            SplitVibeTheme {
                SvChoiceChips(
                    options = listOf(
                        "Equally",
                        "By item",
                        "By shares",
                        "By percent",
                        "By amount",
                    ),
                    selected = selected,
                    onSelect = {
                        selected =
                            it
                    },
                )
            }
        }

        compose.onNodeWithText("By percent").performClick()

        assertEquals(3, selected)
        compose.onNodeWithText("By percent")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `a person choice row is one radio button that chooses the person`() {
        var chosen by mutableStateOf("Minh")
        compose.setContent {
            SplitVibeTheme {
                Column {
                    listOf("Minh", "Lan").forEach { name ->
                        SvPersonChoiceRow(name = name, selected = name == chosen, onSelect = {
                            chosen =
                                name
                        })
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Lan").performClick()

        assertEquals("Lan", chosen)
        compose.onNodeWithContentDescription("Lan")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `a person chip that opens what can be done reads as one button with its action`() {
        var opened = false
        compose.setContent { SplitVibeTheme { SvPersonChip(name = "Minh", onClick = { opened = true }, onClickLabel = "Change Minh") } }

        compose.onNodeWithContentDescription("Minh")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("its action says what it does") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Change Minh"
                },
            )
            .performClick()

        assertTrue(opened)
    }

    @Test
    fun `the arrow of a split button opens its menu, and an option runs and closes it`() {
        var ran = ""
        compose.setContent {
            SplitVibeTheme {
                SvSplitButton(
                    text = "Request all",
                    onClick = { ran = "all" },
                    moreLabel = "More ways to request",
                    options = listOf(SvMenuOption("One by one") { ran = "one" }),
                )
            }
        }
        compose.onNodeWithText("One by one").assertDoesNotExist()

        compose.onNodeWithContentDescription("More ways to request").performClick()
        compose.onNodeWithText("One by one").performClick()

        assertEquals("one", ran)
        compose.onNodeWithText("One by one").assertDoesNotExist()
        compose.onNodeWithText("Request all").performClick()
        assertEquals("all", ran)
    }

    @Test
    fun `a floating menu says whether it is open, and closes when an action is chosen`() {
        var expanded by mutableStateOf(false)
        var tookPhoto = false
        compose.setContent {
            SplitVibeTheme {
                SvFabMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    label = "Start a split",
                    icon = Icons.Default.Add,
                    items = listOf(SvFabMenuItem("Take photo", Icons.Default.Add) { tookPhoto = true }),
                )
            }
        }
        val button = compose.onNodeWithContentDescription("Start a split")
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

        button.performClick()
        assertTrue(expanded)
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Take photo").performClick()

        assertTrue(tookPhoto)
        assertFalse(expanded)
    }

    @Test
    fun `an added person reads as one button that removes them`() {
        var removed = false
        compose.setContent {
            SplitVibeTheme { SvPersonChip(name = "Lê Thị Lan", removeLabel = "Remove Lê Thị Lan", onRemove = { removed = true }) }
        }

        compose.onNodeWithContentDescription("Remove Lê Thị Lan")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertTrue(removed)
    }

    @Test
    fun `the floating bottom bar never covers the end of the list above it`() {
        compose.setContent {
            SplitVibeTheme {
                Scaffold(bottomBar = { SvBottomBar { SvPrimaryButton("Done", onClick = {}) } }) { padding ->
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                        items(ROWS) { index -> Text("Row $index", modifier = Modifier.padding(SvSpacing.lg)) }
                    }
                }
            }
        }

        // Scrolling only brings the row into the list's own frame, which reaches under the bar; where
        // it ends up is what shows whether the bar covers it.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(LAST_ROW))

        val screen = compose.onRoot().getBoundsInRoot()
        val button = compose.onNodeWithText("Done").getBoundsInRoot()
        // The bar is the surface that holds its content, so the parent of the button in it.
        val bar = compose.onNodeWithText("Done").onParent().getBoundsInRoot()
        assertTrue("The bar was not found around its button", bar.top < button.top && bar.bottom > button.bottom)
        assertTrue("The bar does not float clear of the sides of the screen", bar.left > screen.left && bar.right < screen.right)
        val last = compose.onNodeWithText(LAST_ROW).getBoundsInRoot()
        assertTrue("The last row ends at ${last.bottom}, below the top of the bar at ${bar.top}", last.bottom <= bar.top)
    }

    @Test
    fun `the secondary buttons and the back button of a top bar read as buttons a finger can hit`() {
        val tapped = mutableListOf<String>()
        compose.setContent {
            SplitVibeTheme {
                Column {
                    SvTopBar(title = "Edit bill", backLabel = "Back", onBack = { tapped += "back" })
                    SvQuietButton("Sign out", onClick = { tapped += "quiet" })
                    SvSmallAction("Send", onClick = { tapped += "small" })
                    SvTextAction("See all", onClick = { tapped += "text" })
                }
            }
        }

        compose.onNode(hasText("Edit bill") and isHeading()).assertExists()
        for (button in listOf(hasContentDescription("Back"), hasText("Sign out"), hasText("Send"), hasText("See all"))) {
            val node = compose.onNode(button).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            val touch = with(compose.density) { node.fetchSemanticsNode().touchBoundsInRoot.size.toDpSize() }
            assertTrue("$button can be touched on only $touch", touch.width >= TOUCH_TARGET && touch.height >= TOUCH_TARGET)
            node.performClick()
        }

        assertEquals(listOf("back", "quiet", "small", "text"), tapped)
    }

    @Test
    fun `a tappable row reads as one button with its own label`() {
        var tapped = false
        compose.setContent {
            SplitVibeTheme {
                SvListRow(
                    position = SvRowPosition.Single,
                    label = "Bia Saigon Special, 132,000, Minh",
                    onClickLabel = "Choose who shares Bia Saigon Special",
                    onClick = { tapped = true },
                ) { Text("Bia Saigon Special") }
            }
        }

        compose.onNodeWithContentDescription("Bia Saigon Special, 132,000, Minh")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertTrue(tapped)
    }
}

private const val ROWS = 40
private const val LAST_ROW = "Row ${ROWS - 1}"
private val TOUCH_TARGET = 48.dp
