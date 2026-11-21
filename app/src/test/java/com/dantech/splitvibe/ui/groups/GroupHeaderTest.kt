package com.dantech.splitvibe.ui.groups

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.groups.MemberDto
import com.dantech.splitvibe.data.groups.SettlementDto
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.designsystem.components.SvHeroKind
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.ui.common.ProxyWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The block at the top of a group says where the user stands in it, in words and with the amount
 * without its sign: what they get back on lime, what they owe in the colour of the floating bar,
 * and that they are settled up on violet. Where the group's numbers cannot be added up there is no
 * block, as nothing is known of the balance then, and the title of the balances says what they are in.
 * Nor does any member show a standing then, and Settle up says that the payments cannot be worked
 * out, not that everyone is square.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class GroupHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    /** The trip, as Dana sees it: she gets back 35.43 baht. */
    private val trip: GroupState = ProxyCache.json.decodeFromString(GroupState.serializer(), GroupFixtures.groupState())

    @Test
    fun `a user who gets money back reads so on lime, with what they get back`() {
        val standing = GroupStanding.of(trip)

        assertEquals(GroupStanding(3_543), standing)
        assertEquals(SvHeroKind.Accent, standing?.kind)
        show(trip)
        compose.onNode(hasText(YOU_GET_BACK) and hasText(THB.format(3_543))).assertExists()
        assertNoOtherStanding(but = YOU_GET_BACK)
    }

    @Test
    fun `a user who owes reads so in the colour of the bar, with what they owe and no minus sign`() {
        val lan = trip.copy(myMemberId = GroupFixtures.LAN)

        assertEquals(SvHeroKind.Bar, GroupStanding.of(lan)?.kind)
        show(lan)
        compose.onNode(hasText(YOU_OWE) and hasText(THB.format(126_250))).assertExists()
        compose.onAllNodesWithText(THB.format(-126_250), substring = true).assertCountEquals(0)
        assertNoOtherStanding(but = YOU_OWE)
    }

    @Test
    fun `a user who neither gets back nor owes reads that they are settled up, on violet`() {
        val bao = MemberDto("m-bao", "Bao", hasAccount = true, isOwner = false, joinedAt = 1_763_190_500_000)
        val settled = trip.copy(myMemberId = bao.id, members = trip.members + bao)

        assertEquals(SvHeroKind.Brand, GroupStanding.of(settled)?.kind)
        show(settled)
        compose.onNode(hasText(SETTLED_UP) and hasText(THB.format(0))).assertExists()
        assertNoOtherStanding(but = SETTLED_UP)
    }

    @Test
    fun `numbers beyond adding up show no block, no member's standing and no payments, and the balances say what they are in`() {
        // Two payments back as large as a number can be, which no sum can hold.
        val huge = SettlementDto("s9", GroupFixtures.AN, GroupFixtures.ME, Long.MAX_VALUE, GroupFixtures.ME, createdAt = 1_763_190_000_000)
        val beyond = trip.copy(settlements = trip.settlements + huge + huge.copy(id = "s10"))

        assertNull(beyond.balances)
        assertNull(GroupStanding.of(beyond))
        show(beyond)
        assertNoOtherStanding(but = null)
        // Nothing is known of anyone's balance, which is not the same as being settled up.
        for (words in listOf(GETS_BACK, OWES)) compose.onAllNodesWithText(words, substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText(EVERYONE_IS_SQUARE, substring = true, ignoreCase = true).assertCountEquals(0)
        compose.onNodeWithText("These numbers are too large to add up.").assertExists()
        compose.onNodeWithText(CURRENCY_HINT).assertExists()
        // Settle up comes after every member's balance, so finding it shows that all of them were drawn.
        compose.onNodeWithText(PAYMENTS_UNKNOWN).assertExists()
    }

    @Test
    fun `with the block there, the balances leave saying what they are in to it`() {
        show(trip)

        // Once, in the block, and not again under the title of the balances.
        compose.onAllNodesWithText(CURRENCY_HINT).assertCountEquals(1)
        compose.onNode(hasText(YOU_GET_BACK) and hasText(CURRENCY_HINT)).assertExists()
    }

    private fun show(state: GroupState) = compose.setContent { SplitVibeTheme { GroupScreen(state, ProxyWork(), NO_ACTIONS) } }

    /**
     * Fails if the screen says the user stands any other way than [but], or any way at all when it is
     * null, in any case and in any longer text, as a member's "settled up" is.
     */
    private fun assertNoOtherStanding(but: String?) {
        for (words in listOf(YOU_GET_BACK, YOU_OWE, SETTLED_UP).filter { it != but }) {
            compose.onAllNodesWithText(words, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    private companion object {
        const val YOU_GET_BACK = "You get back"
        const val YOU_OWE = "You owe"
        const val SETTLED_UP = "Settled up"
        const val GETS_BACK = "gets back"
        const val OWES = "owes"
        const val EVERYONE_IS_SQUARE = "Everyone is square."
        const val PAYMENTS_UNKNOWN = "The payments that settle the group cannot be worked out yet."
        const val CURRENCY_HINT = "In THB, across every bill and payment back."
        val THB = BillCurrency("THB", 2)

        val NO_ACTIONS = GroupActions(
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
    }
}
