package com.dantech.splitvibe.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvFabMenu
import com.dantech.splitvibe.designsystem.components.SvFabMenuItem
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvPersonChip
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the design system components that hold a choice or open a menu do when they are tapped. */
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
