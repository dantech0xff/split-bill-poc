package com.dantech.splitvibe.ui

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.components.SV_TEXT_FIELD_MAX_LENGTH
import com.dantech.splitvibe.designsystem.components.SvAccountButton
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvActionTile
import com.dantech.splitvibe.designsystem.components.SvAvatarStack
import com.dantech.splitvibe.designsystem.components.SvBarChart
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvBrandTopBar
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvChartBar
import com.dantech.splitvibe.designsystem.components.SvCheckbox
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvFabMenu
import com.dantech.splitvibe.designsystem.components.SvFabMenuItem
import com.dantech.splitvibe.designsystem.components.SvFieldButton
import com.dantech.splitvibe.designsystem.components.SvFieldRow
import com.dantech.splitvibe.designsystem.components.SvFieldWidth
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvMeter
import com.dantech.splitvibe.designsystem.components.SvNavBar
import com.dantech.splitvibe.designsystem.components.SvNavItem
import com.dantech.splitvibe.designsystem.components.SvPersonChip
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRadio
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSmallAction
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.components.SvStatBar
import com.dantech.splitvibe.designsystem.components.SvSwitch
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTile
import com.dantech.splitvibe.designsystem.components.SvTileKind
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.motion.LocalReducedMotion
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.Bill
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.BillItem
import com.dantech.splitvibe.ui.common.CategoryStyle
import com.dantech.splitvibe.ui.common.NumberField
import com.dantech.splitvibe.ui.common.style
import com.dantech.splitvibe.ui.editor.BillEditorContent
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    // Whether a chip lies within the row depends on how wide its label really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `a row of chips that scrolls sideways shows the chosen chip whole, when it appears and when the choice changes`() {
        var selected by mutableIntStateOf(WAYS.lastIndex)
        compose.setContent { SplitVibeTheme { WaysInOneRow(selected = selected, onSelect = { selected = it }) } }
        assertShownWhole(WAYS.last())

        selected = 0
        compose.waitForIdle()

        assertShownWhole(WAYS.first())
    }

    @Test
    // Whether a chip lies within the row depends on how wide its label really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `with animations off, a row of chips that scrolls sideways moves to the chosen chip at once`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            SplitVibeTheme {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    WaysInOneRow(selected = selected, onSelect = { selected = it })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        selected = WAYS.lastIndex
        // Hands the change to the composition, which then waits for a frame. A few frames are enough
        // to compose and lay out the row again, and far too few for the row to glide there.
        compose.waitForIdle()
        repeat(FEW_FRAMES) { compose.mainClock.advanceTimeByFrame() }

        assertShownWhole(WAYS.last())
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
    fun `nothing in the list scrolls under the floating bottom bar`() {
        compose.setContent {
            SplitVibeTheme {
                Scaffold(bottomBar = { SvBottomBar { SvPrimaryButton("Done", onClick = {}) } }) { padding ->
                    ListOfRows(Modifier.padding(padding))
                }
            }
        }

        val screen = compose.onRoot().getBoundsInRoot()
        val button = compose.onNodeWithText("Done").getBoundsInRoot()
        // The bar is the surface that holds its content, so the parent of the button in it.
        val bar = compose.onNodeWithText("Done").onParent().getBoundsInRoot()
        assertTrue("The bar was not found around its button", bar.top < button.top && bar.bottom > button.bottom)
        assertTrue("The bar does not float clear of the sides of the screen", bar.left > screen.left && bar.right < screen.right)
        assertListEndsAbove(bar)
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
            assertRoomForAFinger(node, button.description)
            node.performClick()
        }

        assertEquals(listOf("back", "quiet", "small", "text"), tapped)
    }

    @Test
    fun `the account button reads as its label, with a letter or with a person, and a finger can hit it`() {
        val tapped = mutableListOf<String>()
        compose.setContent {
            SplitVibeTheme {
                Column {
                    SvBrandTopBar(name = "Split Vibe", actions = {
                        SvAccountButton(name = "Dana", label = "Settings", onClick = {
                            tapped +=
                                "letter"
                        })
                    })
                    SvAccountButton(name = " ", label = "Your account", onClick = { tapped += "person" })
                }
            }
        }

        compose.onNode(hasText("Split Vibe") and isHeading()).assertExists()
        // The letter says whose the account is, which is no word for a screen reader to say: the label is.
        compose.onAllNodesWithText("D").assertCountEquals(0)
        for (label in listOf("Settings", "Your account")) {
            val node = compose.onNodeWithContentDescription(
                label,
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            assertRoomForAFinger(node, label)
            node.performClick()
        }

        assertEquals(listOf("letter", "person"), tapped)
    }

    @Test
    fun `the arrow of a split button on the bottom bar still opens its menu`() {
        var finished = false
        compose.setContent {
            SplitVibeTheme {
                SvBottomBar {
                    SvSplitButton(
                        text = "Request ₫651,671 from 2 people",
                        onClick = {},
                        moreLabel = "Other ways to share the split",
                        options = listOf(SvMenuOption("Finish without asking") { finished = true }),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Other ways to share the split").performClick()
        compose.onNodeWithText("Finish without asking").performClick()

        assertTrue(finished)
    }

    @Test
    // Whether a label fits depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `the navigation bar reads as tabs, says which is selected and adds a count to its label`() {
        var selected by mutableIntStateOf(0)
        var splitting = false
        compose.setContent { SplitVibeTheme { NavBar(selected = selected, onSelect = { selected = it }, onSplit = { splitting = true }) } }

        compose.onAllNodes(hasRole(Role.Tab)).assertCountEquals(4)
        compose.onNode(tab("Home")).assertIsSelected()
        // The labels show under the icons, and each tab carries its own name.
        compose.onAllNodesWithText("Requests", useUnmergedTree = true).assertCountEquals(1)
        for (name in listOf("Home", "Groups", "Requests, 2", "Insights")) assertRoomForAFinger(compose.onNode(tab(name)), name)

        compose.onNode(tab("Requests, 2")).assertIsNotSelected().performClick()

        assertEquals(2, selected)
        compose.onNode(tab("Requests, 2")).assertIsSelected()
        compose.onNode(tab("Home")).assertIsNotSelected()
        compose.onNodeWithContentDescription("Split a bill").assert(hasRole(Role.Button)).performClick()
        assertTrue(splitting)
    }

    @Test
    // Whether a label fits depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `with text too large for its labels the navigation bar shows icons only and keeps the labels as names`() {
        compose.setContent {
            SplitVibeTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    NavBar(selected = 0, onSelect = {}, onSplit = {})
                }
            }
        }

        for (label in listOf("Home", "Groups", "Requests", "Insights")) {
            compose.onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(0)
        }
        compose.onNode(tab("Home")).assertIsSelected()
        for (name in listOf("Groups", "Requests, 2", "Insights")) compose.onNode(tab(name)).assertIsNotSelected()
    }

    @Test
    fun `nothing in the list of a main screen scrolls under the floating navigation bar`() {
        compose.setContent {
            SplitVibeTheme {
                Scaffold(bottomBar = { NavBar(selected = 0, onSelect = {}, onSplit = {}) }) { padding ->
                    ListOfRows(Modifier.padding(padding))
                }
            }
        }

        val screen = compose.onRoot().getBoundsInRoot()
        val home = compose.onNode(tab("Home")).getBoundsInRoot()
        // The bar is the pill that holds the tabs, so the parent of a tab.
        val bar = compose.onNode(tab("Home")).onParent().getBoundsInRoot()
        assertTrue("The bar was not found around its tabs", bar.top <= home.top && bar.bottom >= home.bottom)
        assertTrue("The bar does not float clear of the edges of the screen", bar.left > screen.left && bar.right < screen.right)
        assertTrue("The bar does not float clear of the bottom of the screen", bar.bottom < screen.bottom)
        assertListEndsAbove(bar)
    }

    @Test
    // Whether an amount fits depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `a hero amount says its spoken text, and shrinks to one line before it goes on to a second`() {
        compose.setContent {
            SplitVibeTheme {
                Column {
                    SvHeroAmount(
                        prefix = "₫",
                        number = "651,671",
                        suffix = "",
                        spokenText = "651,671 dong",
                        modifier = Modifier.width(360.dp),
                    )
                    SvHeroAmount(
                        prefix = "",
                        number = "1.902.664.000",
                        suffix = " ₫",
                        spokenText = "1,902,664,000 dong",
                        modifier = Modifier.width(240.dp),
                    )
                    SvHeroAmount(
                        prefix = "₫",
                        number = "1,234,567,890,123,456,789",
                        suffix = "",
                        spokenText = "a very large amount",
                        modifier = Modifier.width(200.dp),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("651,671 dong").assert(hasText("₫651,671"))
        val short = layoutOf(compose.onNodeWithContentDescription("651,671 dong"))
        val shrunk = layoutOf(compose.onNodeWithContentDescription("1,902,664,000 dong"))
        val wrapped = layoutOf(compose.onNodeWithContentDescription("a very large amount"))
        // A line is as tall as the size its amount is set in, so its height shows what the amount shrank to.
        assertEquals(1, short.lineCount)
        assertEquals(1, shrunk.lineCount)
        assertTrue("A long amount kept its size", shrunk.firstLineHeight() < short.firstLineHeight())
        assertEquals(2, wrapped.lineCount)
        val smallest = short.firstLineHeight() * MIN_HERO_SP / MAX_HERO_SP
        assertEquals("An amount on two lines is not at the smallest size", smallest, wrapped.firstLineHeight(), smallest * 0.1f)
        for (layout in listOf(short, shrunk, wrapped)) assertFalse("An amount is cut off", layout.hasVisualOverflow)
    }

    @Test
    fun `a meter, a stat bar and a bar chart read as the words that describe them`() {
        compose.setContent {
            SplitVibeTheme {
                Column {
                    SvMeter(value = 9f, max = 10f, description = "9 of 10 calls left")
                    SvStatBar(fraction = 0.5f, description = "Half of the most spent on one category")
                    SvBarChart(
                        bars = listOf(SvChartBar("O", 9_762_024f), SvChartBar("N", 11_749_409f)),
                        description = "November is the highest, then October.",
                        highlighted = 1,
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("9 of 10 calls left").assertExists()
        compose.onNodeWithContentDescription("Half of the most spent on one category").assertExists()
        compose.onNodeWithContentDescription("November is the highest, then October.").assertExists()
        // The chart reads as one description, not as its labels one by one.
        compose.onNodeWithText("N").assertDoesNotExist()
    }

    @Test
    fun `a tick box, a radio button and a switch change state when tapped`() {
        var ticked by mutableStateOf(false)
        var chosen by mutableStateOf(false)
        var on by mutableStateOf(false)
        compose.setContent {
            SplitVibeTheme {
                Row {
                    SvCheckbox(checked = ticked, onCheckedChange = { ticked = it })
                    SvRadio(selected = chosen, onClick = { chosen = true })
                    SvSwitch(checked = on, onCheckedChange = { on = it })
                }
            }
        }

        val checkbox = compose.onNode(hasRole(Role.Checkbox)).assertIsOff()
        val radio = compose.onNode(hasRole(Role.RadioButton)).assertIsNotSelected()
        val switch = compose.onNode(hasRole(Role.Switch)).assertIsOff()
        for ((name, control) in listOf("The tick box" to checkbox, "The radio button" to radio, "The switch" to switch)) {
            assertRoomForAFinger(control, name)
            control.performClick()
        }

        assertTrue(ticked && chosen && on)
        checkbox.assertIsOn()
        radio.assertIsSelected()
        switch.assertIsOn()
    }

    @Test
    fun `a tick box in a row that is ticked as a whole adds no control of its own`() {
        var ticked by mutableStateOf(false)
        compose.setContent {
            SplitVibeTheme {
                Row(modifier = Modifier.toggleable(value = ticked, role = Role.Checkbox, onValueChange = { ticked = it })) {
                    SvCheckbox(checked = ticked, onCheckedChange = null)
                    Text("Also delete the bills on this phone")
                }
            }
        }

        compose.onAllNodes(hasRole(Role.Checkbox)).assertCountEquals(1)
        compose.onNode(hasRole(Role.Checkbox) and hasText("Also delete the bills on this phone")).performClick().assertIsOn()
        assertTrue(ticked)
    }

    @Test
    fun `a dialog asks its question as a heading and runs each of its two buttons`() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            SplitVibeTheme {
                SvDialog(
                    title = "Leave the group?",
                    text = "You can join again with a new invite.",
                    confirmLabel = "Leave",
                    onConfirm = { confirmed++ },
                    dismissLabel = "Cancel",
                    onDismiss = { dismissed++ },
                    irreversible = true,
                )
            }
        }

        compose.onNode(hasText("Leave the group?") and isHeading()).assertExists()
        compose.onNodeWithText("You can join again with a new invite.").assertExists()
        for (label in listOf("Cancel", "Leave")) assertRoomForAFinger(compose.onNodeWithText(label).assert(hasRole(Role.Button)), label)

        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        val leave = compose.onNodeWithText("Leave").getBoundsInRoot()
        assertTrue("The buttons are not side by side", cancel.right <= leave.left && cancel.top == leave.top)

        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1 to 0, dismissed to confirmed)
        compose.onNodeWithText("Leave").performClick()
        assertEquals(1 to 1, dismissed to confirmed)
    }

    @Test
    // Whether a label fits depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `a dialog puts its buttons one above the other when a label does not fit beside the other`() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            SplitVibeTheme {
                SvDialog(
                    title = "Take Lan out of the group?",
                    confirmLabel = "Take out of the group",
                    onConfirm = {},
                    dismissLabel = "Cancel",
                    onDismiss = {},
                )
            }
        }

        val confirm = compose.onNodeWithText("Take out of the group").getBoundsInRoot()
        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        assertTrue("The button that goes ahead is not above the other", confirm.bottom <= cancel.top)
        assertEquals("The buttons are not as wide as each other", confirm.right - confirm.left, cancel.right - cancel.left)
    }

    @Test
    fun `a text field passes its keyboard options on as they are and cuts what is typed at its longest`() {
        var number by mutableStateOf("")
        var name by mutableStateOf("")
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            SplitVibeTheme {
                Column {
                    SvTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = "Account number",
                        maxLength = 6,
                        // A password keyboard is what keeps a keyboard from suggesting and learning the number.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    SvTextField(value = name, onValueChange = { name = it }, label = "Name")
                }
            }
        }

        val account = keyboardOf(view, compose.onNode(hasSetTextAction() and hasText("Account number")))
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, account.inputType and TYPE_AND_VARIATION)
        assertTrue("Capitals were not asked for", account.inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0)
        assertEquals("The keyboard may correct the account number", 0, account.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertEquals(EditorInfo.IME_ACTION_DONE, account.imeOptions and EditorInfo.IME_MASK_ACTION)

        // Without options of its own, a field asks for no less than the app's fields always did.
        val plain = keyboardOf(view, compose.onNode(hasSetTextAction() and hasText("Name")))
        assertEquals(InputType.TYPE_CLASS_TEXT, plain.inputType and TYPE_AND_VARIATION)
        assertTrue("Sentences do not start with a capital", plain.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
        assertEquals("The field takes more than one line", 0, plain.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertEquals(EditorInfo.IME_ACTION_NEXT, plain.imeOptions and EditorInfo.IME_MASK_ACTION)

        compose.onNode(hasSetTextAction() and hasText("Account number")).performTextInput("DE89370400")
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("x".repeat(SV_TEXT_FIELD_MAX_LENGTH + 20))

        assertEquals("DE8937", number)
        // The password keyboard does not hide the number: the field shows it as typed.
        compose.onNode(hasSetTextAction() and hasText("Account number")).assert(hasText("DE8937"))
        assertEquals(SV_TEXT_FIELD_MAX_LENGTH, name.length)
    }

    @Test
    fun `the currency field of the bill editor takes three letters and passes on a code it knows`() {
        var bill by mutableStateOf(EDITED_BILL)
        compose.setContent { SplitVibeTheme { BillEditorContent(bill = bill, onEdit = { change -> bill = change(bill) }, onDone = {}) } }

        // One letter too many: cut at three, what is left is a code of its own.
        compose.onNode(hasSetTextAction() and hasText("Currency")).performTextReplacement("USDX")

        compose.onNode(hasSetTextAction() and hasText("Currency")).assert(hasText("USD"))
        assertEquals("USD", bill.currency.code)
    }

    @Test
    fun `a number field takes twenty characters at most`() {
        var typed = ""
        compose.setContent {
            SplitVibeTheme {
                NumberField(
                    label = "Amount",
                    text = "",
                    parse = { text -> text.toBigDecimalOrNull() },
                    onValue = { amount -> typed = amount.toPlainString() },
                )
            }
        }

        compose.onNode(hasSetTextAction() and hasText("Amount")).performTextInput("1".repeat(MAX_NUMBER_CHARACTERS + 5))

        assertEquals("1".repeat(MAX_NUMBER_CHARACTERS), typed)
        compose.onNode(hasSetTextAction() and hasText("Amount")).assert(hasText("1".repeat(MAX_NUMBER_CHARACTERS)))
    }

    @Test
    fun `a tile that can be tapped reads as one button with all of its text`() {
        var opened = false
        compose.setContent {
            SplitVibeTheme {
                SvTile(
                    label = "November",
                    value = "₫3,823,038",
                    kind = SvTileKind.Accent,
                    caption = "Your share of 6 bills",
                    onClick = { opened = true },
                    onClickLabel = "Open the insights",
                )
            }
        }

        compose.onNode(hasText("November") and hasText("₫3,823,038") and hasText("Your share of 6 bills"))
            .assert(hasRole(Role.Button))
            .assert(
                SemanticsMatcher("its action says what it does") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                        "Open the insights"
                },
            )
            .performClick()

        assertTrue(opened)
    }

    @Test
    // Whether a word fits depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `with large text a tile shrinks its label rather than break a word in two`() {
        compose.setContent {
            SplitVibeTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    // Two tiles side by side across the phone, the first with an icon beside its label.
                    Row(
                        modifier = Modifier.padding(horizontal = SvSpacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
                    ) {
                        SvTile(
                            label = "November",
                            value = "₫3,823,038",
                            kind = SvTileKind.Accent,
                            modifier = Modifier.weight(1f),
                            trailing = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        )
                        SvTile(label = "Friends owe you", value = "₫651,671", kind = SvTileKind.Brand, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        for (label in listOf("November", "Friends owe you")) {
            val layout = layoutOf(compose.onNodeWithText(label, useUnmergedTree = true))
            val cuts = (0 until layout.lineCount - 1).map { line -> layout.getLineEnd(line) }
                .filter { end -> label[end - 1].isLetterOrDigit() && label[end].isLetterOrDigit() }
            assertTrue("\"$label\" breaks a word at $cuts", cuts.isEmpty())
        }
    }

    @Test
    // The colours are read off the drawn picture.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `the rings of an avatar stack have the colour of the dark card it lies on, and are white on a violet block`() {
        compose.setContent {
            SplitVibeTheme(darkTheme = true) {
                Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.lg)) {
                    SvCard(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(SvSpacing.lg).testTag("on a card")) { SvAvatarStack(names = listOf("Minh", "Lan")) }
                    }
                    SvTile(
                        label = "Chiang Mai trip",
                        value = "THB 35.43",
                        kind = SvTileKind.Brand,
                        leading = { Box(modifier = Modifier.testTag("on violet")) { SvAvatarStack(names = listOf("Minh", "Lan")) } },
                    )
                }
            }
        }

        val picture = compose.onRoot().captureToImage().toPixelMap()

        // The colour of the first tile's ring, halfway down its left edge, and of what lies just left of it.
        fun ringAndBehind(tag: String): Pair<Color, Color> {
            val stack = compose.onNodeWithTag(tag).getBoundsInRoot()
            return with(compose.density) {
                val y = ((stack.top + stack.bottom) / 2).roundToPx()
                picture[(stack.left + RING_PROBE).roundToPx(), y] to picture[(stack.left - RING_PROBE).roundToPx(), y]
            }
        }
        val (cardRing, card) = ringAndBehind("on a card")
        assertEquals("The ring on a dark card", card, cardRing)
        val (violetRing, violet) = ringAndBehind("on violet")
        assertEquals("The ring on a violet block", Color.White, violetRing)
        assertNotEquals("The violet block itself is not white", Color.White, violet)
    }

    @Test
    fun `every bill category has an icon and a colour of its own`() {
        var styles = emptyList<CategoryStyle>()
        compose.setContent { SplitVibeTheme { styles = BillCategory.entries.map { it.style } } }
        compose.waitForIdle()

        assertEquals(BillCategory.entries.size, styles.map { it.icon }.toSet().size)
        assertEquals(BillCategory.entries.size, styles.map { it.color }.toSet().size)
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

    @Test
    fun `a row that switches reads as one switch with its state in words, and the part beside it is a field of its own`() {
        var sharing by mutableStateOf(true)
        var part = ""
        compose.setContent {
            SplitVibeTheme {
                SvListRow(
                    position = SvRowPosition.Single,
                    label = "You, ₫225,666",
                    checked = sharing,
                    onCheckedChange = { sharing = it },
                    stateDescription = if (sharing) "You share the bill" else "You do not share the bill",
                    trailing = { SvTextField("25", { part = it }, label = "Percent for You", suffix = "%") },
                    trailingWidth = NUMBER_WIDTH,
                ) { Text("You") }
            }
        }
        val row = compose.onNodeWithContentDescription("You, ₫225,666")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "You share the bill"))

        // The field is not part of the row's tap, and typing in it switches nothing.
        val field = compose.onNode(hasSetTextAction() and hasText("Percent for You"))
        field.assert(!hasAnyAncestor(hasContentDescription("You, ₫225,666")))
        field.performTextReplacement("50")
        assertEquals("50", part)
        assertTrue(sharing)

        row.performClick()
        assertFalse(sharing)
        row.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "You do not share the bill"))
    }

    @Test
    // Whether a number fits depends on how wide its text really is, which only the native graphics measure.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `fields stand side by side while their numbers fit, their boxes lined up at the bottom`() {
        showQuantityPriceAndAmount()

        val (quantity, price, amount) = listOf("Qty", "Price each", "Amount").map { fieldBounds(it) }
        assertTrue("The price is not beside the quantity", price.left >= quantity.right && price.top < quantity.bottom)
        assertTrue("The amount is not beside the price", amount.left >= price.right && amount.top < price.bottom)
        assertEquals("The boxes do not end at one height", quantity.bottom.value, price.bottom.value, BOUNDS_SLACK)
        assertEquals("The boxes do not end at one height", price.bottom.value, amount.bottom.value, BOUNDS_SLACK)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    fun `with text at twice the size, fields too narrow for their numbers go one above the other across the row`() {
        RuntimeEnvironment.setFontScale(2f)
        showQuantityPriceAndAmount()

        val (quantity, price, amount) = listOf("Qty", "Price each", "Amount").map { fieldBounds(it) }
        assertTrue("The price is not below the quantity", price.top >= quantity.bottom)
        assertTrue("The amount is not below the price", amount.top >= price.bottom)
        val width = { field: DpRect -> (field.right - field.left).value }
        assertEquals("The fields do not take the whole row", width(quantity), width(amount), BOUNDS_SLACK)
        assertEquals(FIELD_ROW_WIDTH.value, width(price), BOUNDS_SLACK)
    }

    @Test
    fun `an action that shows only an icon says its label and leaves room for a finger`() {
        val tapped = mutableListOf<String>()
        compose.setContent {
            SplitVibeTheme {
                Row {
                    SvSmallAction(SvActionIcons.Ask, label = "Ask the AI", onClick = { tapped += "ask" })
                    SvFieldButton(SvActionIcons.Bin, label = "Remove Bia", onClick = { tapped += "remove" }, destructive = true)
                    SvActionTile("Take photo", SvActionIcons.Camera, onClick = { tapped += "photo" })
                    SvActionTile("Choose photos", SvActionIcons.Photos, onClick = { tapped += "never" }, enabled = false)
                }
            }
        }

        for ((matcher, name) in listOf(hasContentDescription("Ask the AI") to "ask", hasContentDescription("Remove Bia") to "remove")) {
            val node = compose.onNode(matcher).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            assertRoomForAFinger(node, name)
            node.performClick()
        }
        compose.onNode(hasText("Take photo")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        compose.onNode(hasText("Choose photos")).assertIsNotEnabled().performClick()

        assertEquals(listOf("ask", "remove", "photo"), tapped)
    }

    @Test
    // The colours are read off the drawn picture.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a notice stands out from what it lies on, on the page and on a card`() {
        compose.setContent {
            SplitVibeTheme {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(SvSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(SvSpacing.lg),
                    ) {
                        SvMessageCard(text = "On the page", isError = false)
                        SvCard(modifier = Modifier.fillMaxWidth()) {
                            SvMessageCard(text = "On a card", isError = true, modifier = Modifier.padding(SvSpacing.lg))
                        }
                    }
                }
            }
        }

        val picture = compose.onRoot().captureToImage().toPixelMap()
        for (text in listOf("On the page", "On a card")) {
            // The notice is the surface that holds its text. Both points lie clear of its round corners
            // and of its tile: one just inside its top edge, one just above it, on what it lies on.
            val notice = compose.onNodeWithText(text).onParent().getBoundsInRoot()
            val (inside, outside) = with(compose.density) {
                val x = (notice.left + COLOUR_PROBE_INSET).roundToPx()
                picture[x, (notice.top + COLOUR_PROBE_DEPTH).roundToPx()] to picture[x, (notice.top - COLOUR_PROBE_DEPTH).roundToPx()]
            }
            assertNotEquals("The notice ${text.replaceFirstChar { it.lowercase() }} has the colour of what it lies on", outside, inside)
        }
    }

    /** What [field] asks of the keyboard once it has the cursor, as the keyboard is told it. */
    private fun keyboardOf(view: View, field: SemanticsNodeInteraction): EditorInfo {
        field.performClick()
        val info = EditorInfo()
        compose.runOnIdle { assertNotNull("The field started no input", view.onCreateInputConnection(info)) }
        return info
    }

    /** Shows an item's quantity, price each and amount in a row of fields as wide as an open row of the bill editor. */
    private fun showQuantityPriceAndAmount() = compose.setContent {
        SplitVibeTheme {
            SvFieldRow(
                widths = listOf(SvFieldWidth.Fixed(80.dp), SvFieldWidth.Share(1f), SvFieldWidth.Share(1f)),
                modifier = Modifier.width(FIELD_ROW_WIDTH),
            ) {
                SvTextField("1", {}, label = "Qty")
                SvTextField("165000", {}, label = "Price each")
                SvTextField("165000", {}, label = "Amount")
            }
        }
    }

    private fun fieldBounds(label: String): DpRect = compose.onNode(hasSetTextAction() and hasText(label)).getBoundsInRoot()

    /** Fails unless the chip of [option] lies whole within the row of chips around it. */
    private fun assertShownWhole(option: String) {
        val row = compose.onNode(hasScrollAction()).getBoundsInRoot()
        // Where the chip is, whether or not the row shows it: its bounds would be cut at the edge of the row.
        val chip = compose.onNodeWithText(option).fetchSemanticsNode()
        val (start, end) = with(compose.density) { chip.positionInRoot.x.toDp().let { it to it + chip.size.width.toDp() } }
        assertTrue(
            "$option lies from $start to $end, beyond the row from ${row.left} to ${row.right}",
            start >= row.left && end <= row.right,
        )
    }

    /**
     * Fails unless the list on screen ends above [bar]. Its own frame has to end there: padding inside
     * the frame would only lift its last row above the bar, while every other row still scrolls under
     * the bar. The last row must still be reachable above the bar too.
     */
    private fun assertListEndsAbove(bar: DpRect) {
        val list = compose.onNode(hasScrollAction())
        val frame = list.getBoundsInRoot()
        assertTrue("The list runs on to ${frame.bottom}, under the top of the bar at ${bar.top}", frame.bottom <= bar.top)
        list.performScrollToNode(hasText(LAST_ROW))
        val last = compose.onNodeWithText(LAST_ROW).getBoundsInRoot()
        assertTrue("The last row ends at ${last.bottom}, below the top of the bar at ${bar.top}", last.bottom <= bar.top)
    }

    /**
     * Fails unless [node], called [name], keeps room for a finger: the space it takes in the layout,
     * which includes what Material reserves around a control drawn smaller, is at least 48dp each way.
     * Its touch bounds cannot tell, as they grow to 48dp around anything that can be clicked.
     */
    private fun assertRoomForAFinger(node: SemanticsNodeInteraction, name: String) {
        val layout = node.fetchSemanticsNode().layoutInfo
        val size = with(compose.density) { DpSize(layout.width.toDp(), layout.height.toDp()) }
        assertTrue("$name takes only $size", size.width >= TOUCH_TARGET && size.height >= TOUCH_TARGET)
    }
}

/** The ways to split a bill as chips in one row that scrolls sideways, narrower than the chips. */
@Composable
private fun WaysInOneRow(selected: Int, onSelect: (Int) -> Unit) = SvChoiceChips(
    options = WAYS,
    selected = selected,
    onSelect = onSelect,
    modifier = Modifier.width(ROW_WIDTH),
    singleRow = true,
)

/** A list longer than the screen, as a screen with a bar at its bottom shows it. */
@Composable
private fun ListOfRows(modifier: Modifier) = LazyColumn(modifier = modifier.fillMaxSize()) {
    items(ROWS) { index -> Text("Row $index", modifier = Modifier.padding(SvSpacing.lg)) }
}

/** The navigation bar of the main screens, as the app fills it. */
@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit, onSplit: () -> Unit) = SvNavBar(
    items = listOf(
        SvNavItem("Home", Icons.Default.Home),
        SvNavItem("Groups", Icons.Default.Person),
        SvNavItem("Requests", Icons.Default.Notifications, count = 2),
        SvNavItem("Insights", Icons.AutoMirrored.Filled.List),
    ),
    selected = selected,
    onSelect = onSelect,
    centerLabel = "Split a bill",
    centerIcon = Icons.Default.Add,
    onCenterClick = onSplit,
)

private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

private fun tab(name: String) = hasContentDescription(name) and hasRole(Role.Tab)

/** How the text of [node] is laid out. */
private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    return results.single()
}

private fun TextLayoutResult.firstLineHeight(): Float = getLineBottom(0) - getLineTop(0)

/** As wide as the fields of an open row of the bill editor on a phone 412dp wide. */
private val FIELD_ROW_WIDTH = 332.dp
private val NUMBER_WIDTH = 148.dp

/** How far two edges may differ and still count as one, for rounding to whole pixels. */
private const val BOUNDS_SLACK = 1f

private const val ROWS = 40
private const val LAST_ROW = "Row ${ROWS - 1}"
private val TOUCH_TARGET = 48.dp

/** The ways to split a bill, more than fit in [ROW_WIDTH]. */
private val WAYS = listOf("Equally", "By item", "By shares", "By percent", "By amount")
private val ROW_WIDTH = 240.dp

/** A few frames, 64ms: enough to lay something out again, too short for anything to glide. */
private const val FEW_FRAMES = 4

/** Where a colour is read: this far in from the start of a surface, and this far inside or outside its top edge. */
private val COLOUR_PROBE_INSET = 32.dp
private val COLOUR_PROBE_DEPTH = 8.dp

/** Half the width of an avatar's ring: inside it on one side of the tile's edge, clear of it on the other. */
private val RING_PROBE = 1.dp

/** The bill the editor shows: one item, in dong. */
private val EDITED_BILL = Bill(
    merchant = "Pho 24",
    date = "2026-11-13",
    currency = BillCurrency("VND", 0),
    items = listOf(BillItem("i1", "Phở bò", BigDecimal.ONE, 65_000, 65_000)),
    charges = emptyList(),
    printedTotalMinor = null,
)

/** The longest number the app's number fields take. */
private const val MAX_NUMBER_CHARACTERS = 20

/** A phone of common size, as the screen galleries draw. */
private const val PHONE = "w412dp-h915dp-xhdpi"

/** The kind of text a field asks for, without the flags that go with it. */
private const val TYPE_AND_VARIATION = InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION

/** The sizes a hero amount shrinks between. */
private const val MAX_HERO_SP = 56f
private const val MIN_HERO_SP = 20f
