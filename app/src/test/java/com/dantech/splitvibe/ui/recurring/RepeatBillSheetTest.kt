package com.dantech.splitvibe.ui.recurring

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.Frequency
import com.dantech.splitvibe.domain.RecurrenceSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The sheet that makes a bill recur, as someone who hears the screen uses it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepeatBillSheetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the button that changes the first draft says what it changes, and repeating keeps the bill's own day`() {
        var repeated: Triple<String, RecurrenceSchedule, LocalDate>? = null
        compose.setContent {
            SplitVibeTheme {
                RepeatBillSheet(
                    name = "Rent",
                    date = "2026-10-31",
                    today = LocalDate.of(2026, 11, 16),
                    onRepeat = { name, schedule, firstDraft -> repeated = Triple(name, schedule, firstDraft) },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Change the day of the first draft").assertHasClickAction()
        compose.onNodeWithText("Repeat").performClick()

        // Every month on the 31st, as the rent is, with the first draft on the last day of November.
        val rent = RecurrenceSchedule(Frequency.MONTHLY, LocalDate.of(2026, 10, 31))
        assertEquals(Triple("Rent", rent, LocalDate.of(2026, 11, 30)), repeated)
    }
}
