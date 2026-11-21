package com.dantech.splitvibe.e2e

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dantech.splitvibe.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Typing into the split with the on-screen keyboard open, as a finger does. The test images of the
 * Android 11, 14 and 16 emulators that the device tests run on show no keyboard, while the plain
 * image of Android 8.0 does, and that emulator runs only the journeys: this test is one of them so
 * that at least that run types with a keyboard. The sample bill comes through the fake proxy.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardOnDeviceTest {

    @get:Rule(order = 0)
    val fake = FakeProxyRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** From here on every tap also checks the screen it is made on for accessibility. */
    @Before
    fun checkAccessibilityOnEveryScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) compose.enableAccessibilityChecks()
    }

    /**
     * Types a person's part into their row of the split as a finger does, with the keyboard open: the
     * field being typed into stays in view above the bar at the bottom, and the bar above the keyboard.
     * The last row is the one nearest the bar. The emulators that run these tests have a hardware
     * keyboard, so the phone is set to show its own keyboard anyway while the test runs. On a phone that
     * still shows none, the test is skipped rather than passed, as it would prove nothing there.
     */
    @Test
    fun aPartTypedIntoTheLastRowStaysAboveTheBarAndTheKeyboard() = withTheOnScreenKeyboard {
        compose.readTheSampleBill()

        val list = compose.list()
        list.performScrollToNode(hasText("By percent"))
        // Through its action: the chip may lie partly beyond the edge of its row, which scrolls sideways.
        compose.onNodeWithText("By percent").performSemanticsAction(SemanticsActions.OnClick)
        val part = hasSetTextAction() and hasText("Percent for Lan")
        list.performScrollToNode(part)
        // A tap, as a finger gives it, puts the cursor in the field and opens the keyboard.
        compose.onNode(part).performClick()
        assumeTrue("The phone shows no keyboard", awaitKeyboard())
        compose.onNode(part).performTextReplacement("50")
        compose.waitForIdle()

        // Where the field and the bar are as a whole: bounds cut to the list would never reach below it.
        val field = compose.onNode(part).assertIsDisplayed().unclippedBounds()
        // Lan's 50% leaves the split over 100%, so its main action cannot be used yet.
        val bar = compose.onNodeWithText("Finish the split").onParent().unclippedBounds()
        assertTrue("The field ends at ${field.bottom}, under the top of the bar at ${bar.top}", field.bottom <= bar.top)
        val keyboard = keyboardTop()
        assertTrue("The bar ends at ${bar.bottom}, under the top of the keyboard at $keyboard", bar.bottom <= keyboard)
    }

    /**
     * Runs [block] with the phone set to show its on-screen keyboard even while a hardware keyboard is
     * attached, and puts the setting back as it was afterwards.
     */
    private fun withTheOnScreenKeyboard(block: () -> Unit) {
        val before = shell("settings get secure $SHOW_KEYBOARD_WITH_KEYS").trim()
        shell("settings put secure $SHOW_KEYBOARD_WITH_KEYS 1")
        try {
            block()
        } finally {
            shell("settings put secure $SHOW_KEYBOARD_WITH_KEYS ${if (before == "1") "1" else "0"}")
        }
    }

    /** Runs a shell [command] on the phone with the rights of the shell, and returns what it printed. */
    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    /** Waits for the keyboard to show; false when it does not, as on an emulator without one or a phone with keys of its own. */
    private fun awaitKeyboard(): Boolean = try {
        compose.waitUntil(KEYBOARD_TIMEOUT_MS) { keyboardShown() }
        true
    } catch (e: ComposeTimeoutException) {
        false
    }

    private fun keyboardShown(): Boolean {
        var shown = false
        compose.activityRule.scenario.onActivity { activity ->
            shown = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
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

    /** Where the keyboard starts on the screen, or the bottom of the screen while it shows none, as an emulator may not. */
    private fun keyboardTop(): Dp {
        var keyboard = 0
        compose.activityRule.scenario.onActivity { activity ->
            keyboard = ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        }
        val screen = compose.onRoot().getBoundsInRoot()
        return screen.bottom - with(compose.density) { keyboard.toDp() }
    }

    private companion object {
        /** Long enough for a keyboard to slide in, short enough not to hold up a run on a phone without one. */
        const val KEYBOARD_TIMEOUT_MS = 5_000L

        /** The setting that shows the on-screen keyboard while a hardware keyboard is attached, as on these emulators. */
        const val SHOW_KEYBOARD_WITH_KEYS = "show_ime_with_hard_keyboard"
    }
}
