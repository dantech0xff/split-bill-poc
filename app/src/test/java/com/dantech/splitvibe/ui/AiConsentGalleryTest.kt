package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.common.LegalLinks
import com.dantech.splitvibe.ui.split.AiConsentScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the consent screen asked before the first call to the AI, as a bill read shows it, in the
 * light and the dark theme, with text at twice its size, where the answers must stay in view, and in
 * the pseudolocale en-XA, and compares the images with the references in `src/test/screenshots`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class AiConsentGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the consent to the AI`() = shot("55-ai-consent")

    @Test
    @Config(sdk = [34], qualifiers = PHONE_AT_NIGHT)
    fun `the consent to the AI in the dark theme`() = shot("55-ai-consent-dark")

    @Test
    fun `the consent to the AI with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("55-ai-consent-large-text")
    }

    @Test
    @Config(sdk = [34], qualifiers = PSEUDO_PHONE)
    fun `the consent to the AI in en-XA`() = shot("55-ai-consent-en-xa")

    private fun shot(name: String) {
        compose.setContent {
            SplitVibeTheme {
                // No ripples, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    AiConsentScreen(
                        declineLabel = stringResource(R.string.split_type_by_hand),
                        legalLinks = LEGAL_LINKS,
                        onAgree = {},
                        onDecline = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    private companion object {
        val LEGAL_LINKS = LegalLinks.of("https://split-vibe-proxy.example.workers.dev")
    }
}

private const val PHONE = "w412dp-h915dp-xhdpi"
private const val PHONE_AT_NIGHT = "w412dp-h915dp-night-xhdpi"
private const val PSEUDO_PHONE = "en-rXA-w412dp-h915dp-xhdpi"
