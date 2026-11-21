package com.dantech.splitvibe.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.dantech.splitvibe.data.settings.SettingsStore
import com.dantech.splitvibe.domain.BillCurrency
import java.util.Locale
import org.koin.core.context.GlobalContext

/*
 * What a person does on the way through a split, shared by the journeys. Every element is found by
 * the English text the person sees on it, by what a screen reader says for a button that shows no
 * text, or by what it is, such as a tab, a text field or a list.
 */

/** How long work off the main thread may take, such as reading the bill's text or an answer of the proxy. */
internal const val TIMEOUT_MS = 60_000L

/** Waits until something matching [matcher] is on screen. */
fun ComposeTestRule.awaitNode(matcher: SemanticsMatcher) = waitUntil(TIMEOUT_MS) {
    onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
}

/** Waits until nothing matching [matcher] is left on screen, such as a sheet that closed. */
fun ComposeTestRule.awaitNoNode(matcher: SemanticsMatcher) = waitUntil(TIMEOUT_MS) {
    onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
}

/** The text field labelled [label]. */
fun ComposeTestRule.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))

/**
 * The tab of the navigation bar called [label], also with the count it may carry, as in "Requests, 2".
 * The screen a tab opens has the same name as its title, which is no tab.
 */
fun tab(label: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasContentDescription(label, substring = true)

/**
 * Taps the element through its click action rather than a touch at its place on the screen. Once
 * something was typed, the keyboard may still be moving the screen, and a touch lands on whatever
 * slid under it, as it did on an Android 8.0 emulator.
 */
fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick)

/** The list that scrolls the screen; each screen of the journeys has one while no sheet is open. */
fun ComposeTestRule.list(): SemanticsNodeInteraction = onNode(hasScrollToIndexAction())

/**
 * Waits until the list holds the text field labelled [label], scrolled to it. A row that has just
 * opened may lie below what the list shows, as with the keyboard open, where the list does not make
 * its fields until it is scrolled there.
 */
fun ComposeTestRule.scrollToField(label: String): SemanticsNodeInteraction {
    val field = hasSetTextAction() and hasText(label)
    waitUntil(TIMEOUT_MS) { runCatching { list().performScrollToNode(field) }.isSuccess }
    return onNode(field)
}

/**
 * On the first step of a new split: types in a bill from [where] with one [item] of [amount], in
 * the currency the app works in, and goes on to the split, where only the user is on the bill.
 */
fun ComposeTestRule.typeBillByHand(where: String, item: String, amount: String) {
    onNodeWithText("Type it in by hand").performScrollTo().performClick()
    fillInTheBill(where, item, amount)
}

/** On the consent to the AI, which shows over the screen that asked for the AI: the answer on [button]. */
fun ComposeTestRule.answerTheAiConsent(button: String) {
    val answer = hasText(button) and hasClickAction() and hasAnyAncestor(isDialog())
    awaitNode(answer)
    onNode(answer).tap()
    awaitNoNode(isDialog())
}

/**
 * From the home: the bundled sample bill, read through the AI once the consent to it is given, up to
 * its split, which shows the bill's total and what each person pays.
 */
fun ComposeTestRule.readTheSampleBill() {
    onNodeWithContentDescription("Split a bill").performClick()
    onNodeWithText("Use the sample bill").performScrollTo().performClick()
    awaitNode(hasText("Read and split the bill") and isEnabled())
    onNodeWithText("Read and split the bill").tap()
    answerTheAiConsent("Agree")
    awaitNode(hasText("Bill total"))
}

/**
 * In the editor of a bill typed in by hand: [where], one [item] of [amount], in the currency the app
 * works in, and on to the split, where only the user is on the bill. The item added opens itself, the
 * one row of the editor open, so its fields are the only ones of their names.
 *
 * "Add an item" is scrolled to and tapped in one go, again until the tap is made: the keyboard that
 * typing opened may still be shrinking the list, which then no longer makes a button it was scrolled
 * to a moment before, as on an Android 8.0 emulator.
 */
fun ComposeTestRule.fillInTheBill(where: String, item: String, amount: String) {
    awaitNode(hasText("Edit bill"))
    field("Where").performTextInput(where)
    val addAnItem = hasText("Add an item") and hasClickAction()
    waitUntil(TIMEOUT_MS) {
        runCatching {
            list().performScrollToNode(addAnItem)
            onNode(addAnItem).tap()
        }.isSuccess
    }
    scrollToField("Item").performTextInput(item)
    scrollToField("Amount").performTextInput(amount)
    onNodeWithText("Done").tap()
    awaitNode(hasText("Bill total"))
}

/** On the split: adds [name] to the bill through the sheet that "Add someone" opens. */
fun ComposeTestRule.addPerson(name: String) {
    val addSomeone = hasText("Add someone") and hasClickAction()
    list().performScrollToNode(addSomeone)
    onNode(addSomeone).tap()
    awaitNode(hasSetTextAction() and hasText("Name"))
    field("Name").performTextInput(name)
    // Done on the keyboard adds what was typed, as the sheet's own Add does. A suggestion of someone
    // split with before brings another Add button, so the keyboard's is the one to use.
    field("Name").performImeAction()
    awaitNoNode(hasSetTextAction() and hasText("Name"))
}

/** On the split: "Each person pays" shows [amount] in the row of [name], which is one element with all it says. */
fun ComposeTestRule.assertShare(name: String, amount: String) {
    val share = hasText(name) and hasText(amount)
    list().performScrollToNode(share)
    onNode(share).assertIsDisplayed()
}

/** On the split: the user's own row, which switches whether they share the bill. */
val myRow: SemanticsMatcher = hasText("You") and isToggleable()

/** The currency the app works in, from its settings, which a bill typed in by hand is in. */
fun appCurrency(): BillCurrency {
    val code = GlobalContext.get().get<SettingsStore>().settings.value.localCurrency
    return BillCurrency.resolve(code.trim().uppercase(Locale.ROOT), printedDecimals = null, amounts = emptyList())
}
