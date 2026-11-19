package com.dantech.splitvibe.ui.common

import android.app.Application
import android.content.Intent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The links to the privacy policy and the terms of use, on a phone with a browser and on one without. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LegalLinksRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a phone without an app for web pages says so, and TalkBack reads it out as it appears`() {
        // Starting an activity nothing on the phone handles then fails, as on a phone without a browser.
        shadowOf(app).checkActivities(true)
        show()

        compose.onNodeWithText("Privacy policy").performClick()

        val announced = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        compose.onNode(hasText(CANNOT_OPEN) and announced).assertExists()
    }

    @Test
    fun `a phone with a browser opens the page and says nothing`() {
        show()

        compose.onNodeWithText("Terms of use").performClick()

        val opened = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("https://split-vibe-proxy.example.workers.dev/terms", opened.dataString)
        compose.onNodeWithText(CANNOT_OPEN).assertDoesNotExist()
    }

    private fun show() = compose.setContent {
        SplitVibeTheme { LegalLinksRow(checkNotNull(LegalLinks.of("https://split-vibe-proxy.example.workers.dev"))) }
    }

    private companion object {
        const val CANNOT_OPEN = "No app on this phone opens web pages."
    }
}
