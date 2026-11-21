package com.dantech.splitvibe.ui.requests

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.RequestFixtures
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A line of the details to pay by hand is never hidden by what it is called: the name of the line
 * and its value stand side by side while they fit, and the value goes below its name when they do
 * not, as with the long name a person gives a way to pay of their own, with text at twice its size.
 * The value then still shows whole.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PaymentDetailsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `with text at twice the size, the value of another way to pay with a long name shows whole below the name`() {
        RuntimeEnvironment.setFontScale(2f)
        show(RequestFixtures.view(GroupFixtures.request(role = RequestView.DEBTOR, method = OTHER_WAY)))

        val name = compose.onNode(hasText(LONG_NAME), useUnmergedTree = true).getBoundsInRoot()
        val value = compose.onNode(hasText(EMAIL), useUnmergedTree = true)
        value.assertIsDisplayed()
        val layout = layoutOf(value)
        assertEquals("The value breaks onto ${layout.lineCount} lines", 1, layout.lineCount)
        // Its one line is no wider than the value's own place, so none of it is cut off. The layout a test
        // reads lays the line out across the whole width it could take, so it is measured by its ends.
        val line = layout.getLineRight(0) - layout.getLineLeft(0)
        val place = value.fetchSemanticsNode().size.width
        assertTrue("The value's line of $line px is cut to $place px", line <= place + 1)
        val shown = value.getBoundsInRoot()
        assertTrue("The value at $shown runs off the screen", shown.left >= 0.dp && shown.right <= compose.onRoot().getBoundsInRoot().right)
        assertTrue("The value at $shown is not below the name at $name", shown.top >= name.bottom)
    }

    @Test
    fun `a short name and its value share a line, the value at its end`() {
        show(RequestFixtures.view(GroupFixtures.request(role = RequestView.DEBTOR)))

        val name = compose.onNode(hasText("Account"), useUnmergedTree = true).getBoundsInRoot()
        val value = compose.onNode(hasText("257678859"), useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The value at $value is not beside the name at $name", value.overlapsVertically(name) && value.left > name.right)
    }

    /** How to pay [request], as the request link and the requests screen show it. */
    private fun show(request: RequestView) = compose.setContent {
        SplitVibeTheme {
            PayPanel(
                request,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SvSpacing.screen),
            )
        }
    }

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val getLayout = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
        checkNotNull(getLayout?.action) { "The value has no text layout" }.invoke(results)
        return results.single()
    }

    private fun DpRect.overlapsVertically(other: DpRect) = top < other.bottom && other.top < bottom

    private companion object {
        /** A name as long as a way of the user's own can have, 38 of its 40 characters. */
        const val LONG_NAME = "Zelle, to the email of my credit union"
        const val EMAIL = "dana@example.com"
        const val OTHER_WAY = """{"type":"details","label":"$LONG_NAME","details":"$EMAIL"}"""
    }
}
