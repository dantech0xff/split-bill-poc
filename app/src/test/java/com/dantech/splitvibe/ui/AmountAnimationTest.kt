package com.dantech.splitvibe.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.motion.SvAmount
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The amount that runs through the numbers in between when its value changes. The tests move the
 * animation clock by hand and read the numbers the amount asks to be formatted.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AmountAnimationTest {

    @get:Rule
    val compose = createComposeRule()

    private var amount by mutableLongStateOf(0L)
    private val formatted = mutableListOf<Long>()

    @Test
    fun `a changed amount runs through the numbers in between`() {
        show(countUp = false)
        change(FIRST)
        compose.mainClock.advanceTimeBy(PART_OF_A_RUN_MS)
        assertTrue("The amount jumped straight to its new value", numbersInBetween().last() in 1 until FIRST)

        compose.mainClock.advanceTimeBy(WHOLE_RUN_MS)
        assertEquals(FIRST, formatted.last())
    }

    @Test
    fun `an amount that changes in the middle of its run goes on from the number on screen`() {
        show(countUp = false)
        change(FIRST)
        compose.mainClock.advanceTimeBy(PART_OF_A_RUN_MS)
        val beforeChange = numbersInBetween().last()

        change(SECOND)
        compose.mainClock.advanceTimeByFrame()
        val afterChange = numbersInBetween().last()

        assertTrue("The amount jumped from $beforeChange to $afterChange", abs(afterChange - beforeChange) < FIRST)
        compose.mainClock.advanceTimeBy(WHOLE_RUN_MS)
        assertEquals(SECOND, formatted.last())
    }

    @Test
    fun `a screen reader gets the real amount while the number is still running`() {
        amount = FIRST
        show(countUp = true)
        compose.mainClock.advanceTimeBy(PART_OF_A_RUN_MS)

        assertTrue("The count was not under way, so this proves nothing", numbersInBetween().last() in 1 until FIRST)
        compose.onNodeWithTag(AMOUNT).assertTextEquals(FIRST.toString())
    }

    private fun show(countUp: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SplitVibeTheme {
                SvAmount(amount, format = ::format, style = TextStyle.Default, modifier = Modifier.testTag(AMOUNT), countUp = countUp)
            }
        }
    }

    /** Changes the amount the way a screen's state changes: the composition learns of it at once, not on a later message. */
    private fun change(to: Long) = Snapshot.withMutableSnapshot { amount = to }

    private fun format(value: Long): String {
        formatted += value
        return value.toString()
    }

    /** The numbers the run drew. A screen reader may ask for the real amount at any moment, so the real amounts are left out. */
    private fun numbersInBetween(): List<Long> = formatted.filter { it != FIRST && it != SECOND }
}

private const val AMOUNT = "amount"
private const val FIRST = 1_000L
private const val SECOND = 100_000L
private const val PART_OF_A_RUN_MS = 80L
private const val WHOLE_RUN_MS = 2_000L
