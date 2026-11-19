package com.dantech.splitvibe.ui.groups

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.GroupFixtures
import com.dantech.splitvibe.data.groups.GroupState
import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.ui.common.ProxyWork
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the group's owner reads before taking someone out, and a member before leaving: the proxy
 * ends every invite link of the group then, so a place comes back only through a new invite.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GroupDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val group: GroupState = ProxyCache.json.decodeFromString(GroupState.serializer(), GroupFixtures.groupState())

    @Test
    fun `taking someone out says that the group's invite links stop working, and that a new invite gives the place back`() {
        show()

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("More for Minh"))
        compose.onNodeWithContentDescription("More for Minh").performClick()
        compose.onNodeWithText("Take out of the group").performClick()

        compose.onNodeWithText(
            "They will no longer see the group. What they paid and owe stays under their name, so nobody's balance changes. " +
                "The group's current invite links stop working, and a new invite gives their place back.",
        ).assertExists()
    }

    @Test
    fun `leaving says that the group's invite links stop working, so coming back needs a new invite`() {
        show()

        compose.onNodeWithContentDescription("More for this group").performClick()
        compose.onNodeWithText("Leave the group").performClick()

        compose.onNodeWithText(
            "What you paid and owe stays in the group under your name, so the others' balances do not change. " +
                "The group's invite links stop working, so coming back needs a new invite from someone in the group.",
        ).assertExists()
    }

    private fun show() = compose.setContent { SplitVibeTheme { GroupScreen(group, ProxyWork(), NO_ACTIONS) } }

    private companion object {
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
