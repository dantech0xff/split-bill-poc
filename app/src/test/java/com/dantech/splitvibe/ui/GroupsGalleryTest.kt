package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.GroupSummary
import com.dantech.splitvibe.data.groups.Invitation
import com.dantech.splitvibe.data.payments.SavedPaymentMethod
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.data.requests.RequestView
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.payments.PaymentMethod
import com.dantech.splitvibe.ui.common.ProxyWork
import com.dantech.splitvibe.ui.groups.GroupActions
import com.dantech.splitvibe.ui.groups.GroupScreen
import com.dantech.splitvibe.ui.groups.GroupsScreen
import com.dantech.splitvibe.ui.groups.JoinGroupScreen
import com.dantech.splitvibe.ui.groups.JoinState
import com.dantech.splitvibe.ui.payments.PaymentMethodsScreen
import com.dantech.splitvibe.ui.requests.RequestActions
import com.dantech.splitvibe.ui.requests.RequestLinkScreen
import com.dantech.splitvibe.ui.requests.RequestLinkState
import com.dantech.splitvibe.ui.requests.RequestsScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screens of groups, requests and payment methods with the fixtures' data, and compares
 * them with the reference images in `src/test/screenshots`, like [ScreenGalleryTest] does for the
 * split flow. Record new images after an intended change with `-Pscreenshots=record`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1600dp-xhdpi")
class GroupsGalleryTest {

    @get:Rule
    val compose = createComposeRule()

    private val group: GroupState = ProxyCache.json.decodeFromString(
        GroupState.serializer(),
        GroupFixtures.groupState(claims = """[{"memberId":"${GroupFixtures.AN}","claimantName":"An Pham","requestedAt":1763195000000}]"""),
    )
    private val requests = listOf(
        request(GroupFixtures.request(id = "r1", role = "debtor").replace("\"payerName\":\"Minh\"", "\"payerName\":\"Dana\"")),
        request(GroupFixtures.request(id = "r2", reportedPaidAt = 1_763_200_000_000)),
        request(GroupFixtures.request(id = "r3", reportedPaidAt = 1_763_200_000_000, confirmedPaidAt = 1_763_210_000_000)),
    )

    @Test
    fun `the list of groups`() = shot("22-groups") {
        GroupsScreen(
            groups = listOf(
                GroupSummary(GroupFixtures.GROUP_ID, "Chiang Mai trip", group.group.currency, 9, GroupFixtures.ME, "member"),
                GroupSummary("g2", "Flat 3B", group.group.currency, 2, null, "pending"),
            ),
            signedIn = true,
            status = ProxyWork(),
            myName = "Dana",
            localCurrency = "THB",
            onCreate = { _, _, _ -> },
            onOpen = {},
            onOpenSettings = {},
            onDismissMessage = {},
            onBack = {},
        )
    }

    @Test
    fun `a group with its balances, transfers, people and bills`() = shot("23-group") { GroupScreen(group, ProxyWork(), NO_GROUP_ACTIONS) }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h1600dp-night-xhdpi")
    fun `a group in the dark theme`() = shot("23-group-dark") { GroupScreen(group, ProxyWork(), NO_GROUP_ACTIONS) }

    @Test
    fun `an invite link with a place kept for someone`() = shot("24-join-group") {
        JoinGroupScreen(
            state = JoinState(
                invitation = Invitation(
                    Invitation.InvitedGroup(GroupFixtures.GROUP_ID, "Chiang Mai trip", group.group.currency),
                    placeholders = listOf(Invitation.Placeholder(GroupFixtures.AN, "An")),
                ),
            ),
            signedIn = true,
            myName = "An Pham",
            onJoin = {},
            onClaim = { _, _ -> },
            onOpenSettings = {},
            onBack = {},
        )
    }

    @Test
    fun `requests to pay and to confirm`() =
        shot("25-requests") { RequestsScreen(requests, signedIn = true, status = ProxyWork(), actions = NO_REQUEST_ACTIONS) }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h1600dp-night-xhdpi")
    fun `requests in the dark theme`() =
        shot("25-requests-dark") { RequestsScreen(requests, signedIn = true, status = ProxyWork(), actions = NO_REQUEST_ACTIONS) }

    @Test
    fun `a request link with its payment code`() = shot("26-request-link") {
        RequestLinkScreen(
            state = RequestLinkState(request = requests.first()),
            signedIn = true,
            onAccept = {},
            onReportPaid = {},
            onOpenSettings = {},
            onBack = {},
        )
    }

    @Test
    fun `the ways to get paid`() = shot("27-payment-methods") {
        PaymentMethodsScreen(
            methods = listOf(
                SavedPaymentMethod("1", PaymentMethod.VietQr("970416", "257678859", "DANA NGUYEN"), isMain = true),
                SavedPaymentMethod("2", PaymentMethod.PromptPay("081-234-5678"), isMain = false),
                SavedPaymentMethod("3", PaymentMethod.SepaQr("Dana Nguyen", "DE89370400440532013000"), isMain = false),
                SavedPaymentMethod("4", PaymentMethod.PastedLink("https://revolut.me/dana"), isMain = false),
            ),
            onSave = { _, _ -> },
            onMakeMain = {},
            onRemove = {},
            onBack = {},
        )
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

    private fun request(json: String): RequestView = ProxyCache.json.decodeFromString(RequestView.serializer(), json)

    private companion object {
        val NO_GROUP_ACTIONS = GroupActions(
            onInvite = {},
            onRevokeInvites = {},
            onRename = {},
            onAddPlaceholder = {},
            onApprove = {},
            onReject = {},
            onDeleteBill = {},
            onMarkReceived = {},
            onAskFor = {},
            onLeave = {},
            onRefresh = {},
            onDismissMessage = {},
            onBack = {},
        )
        val NO_REQUEST_ACTIONS = RequestActions(
            onReportPaid = {},
            onConfirmPaid = {},
            onRemind = {},
            onSetReminders = { _, _ -> },
            onDelete = {},
            onRefresh = {},
            onOpenSettings = {},
            onDismissMessage = {},
            onBack = {},
        )
    }
}
