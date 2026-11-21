package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.data.bills.ShareLink
import com.dantech.splitvibe.data.bills.ShareLink.Companion.NEW_PERSON
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.share.ShareFixtures
import com.dantech.splitvibe.data.share.ShareView
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.ui.claim.ClaimActions
import com.dantech.splitvibe.ui.claim.ClaimPayment
import com.dantech.splitvibe.ui.claim.ClaimScreen
import com.dantech.splitvibe.ui.claim.ClaimState
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screen of a bill's link with the fixtures' data, while people pick and once picking is
 * closed, with its dialogs, and compares it with the reference images in `src/test/screenshots`.
 * Record new images after an intended change with `-Pscreenshots=record`.
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
        matches = mapOf("claim-minh" to "p1", "claim-x" to NEW_PERSON),
        connected = true,
    )

    private val payment = ClaimPayment(
        methods = listOf(main),
        chosen = main,
        changed = false,
    )

    @Test
    fun `a bill's link with what people picked so far`() = shot("28-bill-link") {
        ClaimScreen(state(ShareView.OPEN), saved, payment, NO_ACTIONS, Locale.US)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h1600dp-night-xhdpi")
    fun `a bill's link in the dark theme`() = shot("28-bill-link-dark") {
        ClaimScreen(state(ShareView.OPEN), saved, payment, NO_ACTIONS, Locale.US)
    }

    @Test
    fun `a bill's link once picking is closed`() = shot("29-bill-link-settling") {
        ClaimScreen(state(ShareView.LOCKING), saved, payment, NO_ACTIONS, Locale.US)
    }

    @Test
    fun `a bill's link with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("28-bill-link-large-text") { ClaimScreen(state(ShareView.OPEN), saved, payment, NO_ACTIONS, Locale.US) }
    }

    @Test
    fun `a bill's link once picking is closed, with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("29-bill-link-settling-large-text") { ClaimScreen(state(ShareView.LOCKING), saved, payment, NO_ACTIONS, Locale.US) }
    }

    /** The link's owner stops sharing it, which ends it for everyone who has it. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `stopping the sharing of a bill's link`() = shot(
        "28-bill-link-stop-sharing",
        then = { pick("More for the link", "Stop sharing the link") },
    ) { ClaimScreen(state(ShareView.OPEN), saved, payment, NO_ACTIONS, Locale.US) }

    /** What minh picked is taken off the link. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `taking someone off a bill's link`() = shot(
        "28-bill-link-take-off",
        then = {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Who is minh"))
            pick("Who is minh", "Take minh off the link")
        },
    ) { ClaimScreen(state(ShareView.OPEN), saved, payment, NO_ACTIONS, Locale.US) }

    /** Once picking is closed, people may be let pick again. */
    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `letting people pick again once picking is closed`() = shot(
        "29-bill-link-pick-again",
        then = { pick("More for the picks", "Let people pick again") },
    ) { ClaimScreen(state(ShareView.LOCKING), saved, payment, NO_ACTIONS, Locale.US) }

    /** Draws [content], does [then] on it, such as opening a dialog, and compares the screen with the reference image [name]. */
    private fun shot(name: String, then: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            SplitVibeTheme {
                // Rippling is left out, so the pictures do not depend on where a test last touched.
                CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
            }
        }
        then()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    /** Opens the menu described as [menu] and picks its [option]. */
    private fun pick(menu: String, option: String) {
        compose.onNodeWithContentDescription(menu).performClick()
        compose.onNodeWithText(option).performClick()
    }

    private companion object {
        val main = SavedPaymentMethod("m1", PaymentMethod.VietQr("970416", "000000000", "DANA NGUYEN"), isMain = true)

        val NO_ACTIONS = ClaimActions(
            onMatch = { _, _ -> },
            onRemove = {},
            onLock = {},
            onSend = {},
            onReopen = {},
            onRevoke = {},
            onRetry = {},
            onMakeNewLink = {},
            onChooseMethod = {},
            onAddMethod = {},
            onOpenSettings = {},
            onDismissMessage = {},
            onBack = {},
        )
    }
}

/** A phone screen, as large as a dialog shows on. */
private const val PHONE = "w412dp-h915dp-xhdpi"
