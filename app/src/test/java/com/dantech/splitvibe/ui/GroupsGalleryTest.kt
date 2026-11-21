package com.dantech.splitvibe.ui

import android.os.Looper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.RequestFixtures
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.GroupSummary
import com.dantech.splitvibe.data.groups.Invitation
import com.dantech.splitvibe.data.groups.MemberDto
import com.dantech.splitvibe.data.groups.SettlementDto
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
import com.dantech.splitvibe.ui.requests.RequestTotals
import com.dantech.splitvibe.ui.requests.RequestsScreen
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the screens of groups, requests and payment methods with the fixtures' data, the data of
 * the approved designs, and compares them with the reference images in `src/test/screenshots`, like
 * [ScreenGalleryTest] does for the split flow. Record new images after an intended change with
 * `-Pscreenshots=record`. The states the designs leave out, and every screen with text at twice its
 * size, are drawn too, for the owner to approve.
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
    private val requests = RequestFixtures.board

    @Test
    fun `the list of groups`() {
        shot("22-groups") { Groups() }

        // The list of a main screen ends above the navigation bar: its own frame does, not only its
        // last card, which padding at the end of the list would lift above the bar while every other
        // card still passed under it.
        assertListEndsAboveTheBar(lastRow = hasText(PENDING))
        // A group the user is in opens; one that still waits for its owner is a plain card, as there is
        // nothing to open until the owner lets the user in.
        compose.onNode(hasText("Chiang Mai trip") and hasClickAction()).assertExists()
        compose.onNode(hasText(PENDING)).assertHasNoClickAction()
    }

    @Test
    fun `the list of groups with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("22-groups-large-text") { Groups() }

        assertTitleWhole()
    }

    /** On a narrower phone the button leaves the large title too little room, so it goes below the title. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h1600dp-xhdpi")
    fun `the list of groups on a narrow phone with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("22-groups-large-text-narrow") { Groups() }

        val title = assertTitleWhole()
        val button = compose.onNode(hasText(NEW_GROUP) and hasClickAction()).getBoundsInRoot()
        assertTrue("The button at $button is not below the title at $title", button.top >= title.bottom)
    }

    @Test
    fun `a group with its balances, transfers, people and bills`() = shot("23-group") { GroupScreen(group, ProxyWork(), NO_GROUP_ACTIONS) }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h1600dp-night-xhdpi")
    fun `a group in the dark theme`() = shot("23-group-dark") { GroupScreen(group, ProxyWork(), NO_GROUP_ACTIONS) }

    /** Lan's view of the trip: she owes, so the block at the top takes the colour of the floating bar. Only the owner sees who asks to join. */
    @Test
    fun `a group in which the user owes`() = shot("23-group-owing") {
        GroupScreen(group.copy(myMemberId = GroupFixtures.LAN, claims = emptyList()), ProxyWork(), NO_GROUP_ACTIONS)
    }

    /** Bao, who joined after every bill, neither gets back nor owes anything. */
    @Test
    fun `a group in which the user is settled up`() = shot("23-group-settled-up") {
        val bao = MemberDto("m-bao", "Bao", hasAccount = true, isOwner = false, joinedAt = 1_763_190_500_000)
        GroupScreen(group.copy(myMemberId = bao.id, members = group.members + bao, claims = emptyList()), ProxyWork(), NO_GROUP_ACTIONS)
    }

    /** Payments back too large to add up: nobody's standing is known, so no member shows one, and no payment is suggested. */
    @Test
    fun `a group whose numbers are beyond adding up`() = shot("23-group-beyond-adding-up") {
        val huge = SettlementDto("s9", GroupFixtures.AN, GroupFixtures.ME, Long.MAX_VALUE, GroupFixtures.ME, createdAt = 1_763_190_000_000)
        GroupScreen(group.copy(settlements = group.settlements + huge + huge.copy(id = "s10")), ProxyWork(), NO_GROUP_ACTIONS)
    }

    @Test
    fun `a group with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("23-group-large-text") { GroupScreen(group, ProxyWork(), NO_GROUP_ACTIONS) }
    }

    @Test
    fun `an invite link with a place kept for someone`() = shot("24-join-group") { JoinGroup() }

    @Test
    fun `an invite link with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("24-join-group-large-text") { JoinGroup() }
    }

    @Test
    fun `requests to pay and to confirm`() {
        shot("25-requests") { Requests() }

        assertListEndsAboveTheBar(lastRow = hasText(RequestFixtures.PAID))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w412dp-h1600dp-night-xhdpi")
    fun `requests in the dark theme`() = shot("25-requests-dark") { Requests() }

    @Test
    fun `requests with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("25-requests-large-text") { Requests() }
    }

    /** Signed out there is nothing to add up, so no tile shows; the screen asks to sign in. */
    @Test
    fun `requests while nobody is signed in`() = shot("25-requests-signed-out") {
        Requests(requests = null, signedIn = false, totals = RequestTotals.SignedOut)
    }

    /** Right after a sign-in, before the requests come: nothing known is not nothing owed, so no tile shows. */
    @Test
    fun `requests on their way`() = shot("25-requests-loading") {
        Requests(requests = null, totals = RequestTotals.Unknown, status = ProxyWork(working = true))
    }

    @Test
    fun `no request yet`() = shot("25-requests-empty") {
        Requests(requests = emptyList(), totals = RequestTotals.of(signedIn = true, requests = emptyList()))
    }

    @Test
    fun `a request link with its payment code`() {
        shot("26-request-link", waitFor = "Payment code for VietQR") { RequestLink() }

        // The details to type, line by line, each with what it is.
        compose.onNode(hasText("Account") and hasText("257678859")).assertExists()
        compose.onNode(hasText("Bank") and hasText("ACB (970416)")).assertExists()
    }

    @Test
    fun `a request link with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("26-request-link-large-text", waitFor = "Payment code for VietQR") { RequestLink() }
    }

    @Test
    fun `the ways to get paid`() = shot("27-payment-methods") { PaymentMethods() }

    @Test
    fun `the ways to get paid with text at twice the size`() {
        RuntimeEnvironment.setFontScale(2f)
        shot("27-payment-methods-large-text") { PaymentMethods() }
    }

    /** The sheet that adds a way to get paid, open on its first kind. */
    @Test
    fun `adding a way to get paid`() {
        show { PaymentMethods() }
        compose.onNode(hasText("Add") and hasClickAction()).performClick()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/27-payment-method-sheet.png"))
    }

    @Composable
    private fun Groups() = GroupsScreen(
        groups = listOf(
            GroupSummary(GroupFixtures.GROUP_ID, "Chiang Mai trip", group.group.currency, 9, GroupFixtures.ME, "member"),
            GroupSummary("g2", PENDING, group.group.currency, 2, null, "pending"),
        ),
        signedIn = true,
        status = ProxyWork(),
        myName = "Dana",
        localCurrency = "THB",
        onCreate = { _, _, _ -> },
        onOpen = {},
        onOpenSettings = {},
        onDismissMessage = {},
        bottomBar = { NavBar(GroupsKey) },
    )

    @Composable
    private fun JoinGroup() = JoinGroupScreen(
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
        onRetry = {},
        onBack = {},
    )

    /** The requests screen as the app shows it, its tiles and its navigation bar reading the same totals. */
    @Composable
    private fun Requests(
        requests: List<RequestView>? = this.requests,
        signedIn: Boolean = true,
        totals: RequestTotals = RequestTotals.of(signedIn = true, this.requests),
        status: ProxyWork = ProxyWork(),
    ) = RequestsScreen(
        requests = requests,
        signedIn = signedIn,
        status = status,
        actions = NO_REQUEST_ACTIONS,
        totals = totals,
        localCurrency = "VND",
        bottomBar = { AppNavBar(selected = RequestsKey, requests = totals, onSelect = {}, onSplitBill = {}) },
    )

    @Composable
    private fun RequestLink() = RequestLinkScreen(
        state = RequestLinkState(request = requests.first()),
        signedIn = true,
        onAccept = {},
        onReportPaid = {},
        onOpenSettings = {},
        onRetry = {},
        onBack = {},
    )

    @Composable
    private fun PaymentMethods() = PaymentMethodsScreen(
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

    /** The navigation bar on the main screen [tab], counting the requests of the fixtures that wait for the user. */
    @Composable
    private fun NavBar(tab: NavKey) =
        AppNavBar(selected = tab, requests = RequestTotals.of(signedIn = true, requests), onSelect = {}, onSplitBill = {})

    /**
     * Fails unless the large title of the groups shows on one line, as a title of one word must: it
     * is never broken in the middle beside the button that makes a group. Returns where it lies.
     */
    private fun assertTitleWhole(): DpRect {
        val title = compose.onNode(hasText("Groups") and isHeading())
        val layouts = mutableListOf<TextLayoutResult>()
        checkNotNull(title.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action) {
            "The title has no text layout"
        }.invoke(layouts)
        assertEquals("The title breaks onto ${layouts.single().lineCount} lines", 1, layouts.single().lineCount)
        compose.onNode(hasText(NEW_GROUP) and hasClickAction()).assertIsDisplayed()
        return title.getBoundsInRoot()
    }

    /**
     * Fails unless the list of the main screen ends above its navigation bar, and the row that
     * [lastRow] finds, its last, still shows whole above the bar once the list is scrolled to it.
     */
    private fun assertListEndsAboveTheBar(lastRow: SemanticsMatcher) {
        // The bar is the pill that holds the tabs, so the parent of a tab.
        val bar = compose.onNode(tab("Home")).onParent().getBoundsInRoot()
        val list = compose.onNode(hasScrollToIndexAction())
        val frame = list.getBoundsInRoot()
        assertTrue("The list runs on to ${frame.bottom}, under the top of the bar at ${bar.top}", frame.bottom <= bar.top)
        list.performScrollToNode(lastRow)
        val last = compose.onNode(lastRow).getBoundsInRoot()
        assertTrue("The last row ends at ${last.bottom}, below the top of the bar at ${bar.top}", last.bottom <= bar.top)
    }

    private fun shot(name: String, waitFor: String? = null, content: @Composable () -> Unit) {
        show(content)
        // Something drawn away from the main thread, such as a payment code, is waited for.
        waitFor?.let { label ->
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        SplitVibeTheme {
            // Rippling is left out, so the pictures do not depend on where a test last touched.
            CompositionLocalProvider(LocalRippleConfiguration provides null) { content() }
        }
    }

    private companion object {
        val NO_GROUP_ACTIONS = GroupActions(
            onInvite = {},
            onRevokeInvites = {},
            onRename = {},
            onAddPlaceholder = {},
            onApprove = {},
            onRemoveMember = {},
            onReject = {},
            onDeleteBill = {},
            onMarkReceived = { _, _ -> },
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
        )
    }
}

private fun tab(name: String) = hasContentDescription(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/** The group that still waits for its owner to let the user in. */
private const val PENDING = "Flat 3B"
private const val NEW_GROUP = "New group"
