package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.share.ShareFixtures
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.claim.ClaimActions
import com.dantech.splitvibe.ui.claim.ClaimScreen
import com.dantech.splitvibe.ui.claim.ClaimState
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screen of a bill's link with the fixtures' data, and compares it with the reference
 * images in `src/test/screenshots`. Record new images after an intended change with `-Pscreenshots=record`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1600dp-xhdpi")
class ClaimGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val saved = ShareFixtures.saved.copy(share = ShareLink(ShareFixtures.SHARE_ID, ShareFixtures.URL))

    private fun state(status: String) = ClaimState(
        share = ProxyCache.json.decodeFromString(
            ShareView.serializer(),
            ShareFixtures.view(status, revision = 2, claims = ShareFixtures.CLAIMS),
        ),
        shareId = ShareFixtures.SHARE_ID,
        url = ShareFixtures.URL,
        matches = mapOf("claim-minh" to "p1", "claim-x" to ClaimState.NEW_PERSON),
    )

    @Test
    fun `a bill's link with what people picked so far`() = shot("28-bill-link") {
        ClaimScreen(state(ShareView.OPEN), saved, NO_ACTIONS, Locale.US)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h1600dp-night-xhdpi")
    fun `a bill's link in the dark theme`() = shot("28-bill-link-dark") { ClaimScreen(state(ShareView.OPEN), saved, NO_ACTIONS, Locale.US) }

    @Test
    fun `a bill's link once picking is closed`() = shot("29-bill-link-settling") {
        ClaimScreen(state(ShareView.LOCKING), saved, NO_ACTIONS, Locale.US)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    private companion object {
        val NO_ACTIONS = ClaimActions(
            onMatch = { _, _ -> },
            onRemove = {},
            onLock = {},
            onSend = {},
            onReopen = {},
            onRevoke = {},
            onRetry = {},
            onOpenSettings = {},
            onDismissMessage = {},
            onBack = {},
        )
    }
}
