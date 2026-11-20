package com.dantech.splitvibe.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.dantech.splitvibe.data.account.Account
import com.dantech.splitvibe.data.account.AiStatus
import com.dantech.splitvibe.data.account.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the navigation bar, back and a change of account move the stack of open screens. */
class TabNavigationTest {

    @Test
    fun `choosing Home leaves Home alone`() {
        assertEquals(listOf(HomeKey), stackAfterChoosing(HomeKey))
        assertEquals(listOf(listOf<NavKey>(HomeKey)), changesOf(stackOf(HomeKey, GroupsKey)) { it.choose(HomeKey) })
    }

    @Test
    fun `choosing a tab on Home opens it right above Home`() {
        assertEquals(listOf(listOf(HomeKey, RequestsKey)), changesOf(stackOf(HomeKey)) { it.choose(RequestsKey) })
    }

    @Test
    fun `choosing another tab puts it in the place of the open one, in a single change`() {
        // Seen halfway, as Home alone, the change would show as a step back to Home and then a step forward.
        assertEquals(listOf(listOf(HomeKey, InsightsKey)), changesOf(stackOf(HomeKey, GroupsKey)) { it.choose(InsightsKey) })
        assertEquals(listOf(HomeKey, InsightsKey), stackAfterChoosing(InsightsKey))
    }

    @Test
    fun `choosing the tab that is open changes nothing`() {
        val stack = stackOf(HomeKey, GroupsKey)

        assertEquals(emptyList<List<NavKey>>(), changesOf(stack) { it.choose(GroupsKey) })
        assertEquals(listOf(HomeKey, GroupsKey), stack.toList())
    }

    @Test
    fun `a tab chosen from deeper in a tab leaves only Home under it`() {
        val stack = stackOf(HomeKey, GroupsKey, GroupKey("g1"), SettingsKey)

        stack.choose(RequestsKey)

        assertEquals(listOf(HomeKey, RequestsKey), stack.toList())
    }

    @Test
    fun `only the main screens are on the bar`() {
        assertThrows(IllegalArgumentException::class.java) { stackAfterChoosing(SettingsKey) }
    }

    @Test
    fun `back goes back one screen, but never from Home`() {
        val stack = stackOf(HomeKey, GroupsKey)

        stack.goBack()
        assertEquals(listOf(HomeKey), stack.toList())
        // As when a back swipe was held while the bar chose Home, and then let go.
        stack.goBack()
        assertEquals(listOf(HomeKey), stack.toList())
    }

    @Test
    fun `once the account leaves from Settings, Home and Settings stay, with what was opened from Settings`() {
        assertEquals(
            listOf(HomeKey, SettingsKey),
            stackAfterAccountLeft(listOf(HomeKey, RequestsKey, RequestLinkKey("token-1234567890abcdef"), SettingsKey)),
        )
        assertEquals(listOf(HomeKey, SettingsKey), stackAfterAccountLeft(listOf(HomeKey, GroupsKey, GroupKey("g1"), SettingsKey)))
        // Pro, or the ways to get paid, opened from Settings, where a sign-out can take a few seconds.
        assertEquals(listOf(HomeKey, SettingsKey, ProKey), stackAfterAccountLeft(listOf(HomeKey, SplitKey, SettingsKey, ProKey)))
        assertEquals(
            listOf(HomeKey, SettingsKey, PaymentMethodsKey),
            stackAfterAccountLeft(listOf(HomeKey, SettingsKey, PaymentMethodsKey)),
        )
    }

    @Test
    fun `once the account leaves while Settings is no longer open, Home stays alone`() {
        // A deletion goes on after Settings is left; here it ended on a group of the deleted account.
        assertEquals(listOf(HomeKey), stackAfterAccountLeft(listOf(HomeKey, GroupsKey, GroupKey("g1"))))
        assertEquals(listOf(HomeKey), stackAfterAccountLeft(listOf(HomeKey, RequestLinkKey("token-1234567890abcdef"))))
        assertEquals(listOf(HomeKey), stackAfterAccountLeft(listOf(HomeKey)))
    }

    @Test
    fun `the stack after the account left is shown in a single change`() {
        val stack = stackOf(HomeKey, GroupsKey, GroupKey("g1"), SettingsKey)

        val changes = changesOf(stack) { it.showOnly(stackAfterAccountLeft(it)) }

        assertEquals(listOf(listOf(HomeKey, SettingsKey)), changes)
    }

    @Test
    fun `a stack is never left empty`() {
        assertThrows(IllegalArgumentException::class.java) { stackOf(HomeKey).showOnly(emptyList()) }
    }

    @Test
    fun `an account keeps its place while its sign-in runs out, and a new sign-in of the same account is no change`() {
        val signedIn = accountHolderOf(MINH)
        val expired = accountHolderOf(MINH_EXPIRED)

        assertNotNull(signedIn)
        assertEquals(signedIn, expired)
        assertFalse("A sign-in that ran out counts as leaving", accountLeft(signedIn, expired))
        val signedInAgain = accountHolderOf(MINH.copy(session = Session("token-2", FAR_FUTURE)))
        assertFalse("Signing in again counts as leaving", accountLeft(expired, signedInAgain))
    }

    @Test
    fun `an account leaves when it signs out or is deleted, or when another account signs in in its place`() {
        val minh = accountHolderOf(MINH)
        val expired = accountHolderOf(MINH_EXPIRED)
        val lan = accountHolderOf(LAN)

        assertNull(accountHolderOf(NOBODY))
        assertTrue("Signing out is no leaving", accountLeft(minh, accountHolderOf(NOBODY)))
        assertTrue("Signing out after the sign-in ran out is no leaving", accountLeft(expired, accountHolderOf(NOBODY)))
        // Minh's sign-in ran out, and Lan signed in on the phone.
        assertTrue("Another account signing in is no leaving", accountLeft(expired, lan))
        // A sign-in kept from a version that did not keep the address cannot be told from another account.
        val withoutAddress = accountHolderOf(MINH_EXPIRED.copy(signedInAs = null))
        assertTrue("A sign-in without an address is taken for the account after it", accountLeft(withoutAddress, lan))
    }

    @Test
    fun `a first sign-in is no account leaving, and the address itself is not kept with the open screens`() {
        assertFalse("A first sign-in counts as leaving", accountLeft(accountHolderOf(NOBODY), accountHolderOf(LAN)))
        assertFalse(checkNotNull(accountHolderOf(LAN)).contains("lan@example.com"))
    }

    private fun stackOf(vararg keys: NavKey) = NavBackStack(*keys)

    /**
     * Runs [change] on [stack] and returns each state of the stack that what watches it, such as the
     * screens, was told of, in order: one for a change made in one go, none for no change.
     */
    private fun changesOf(stack: NavBackStack<NavKey>, change: (NavBackStack<NavKey>) -> Unit): List<List<NavKey>> {
        // The stack is made in the current global snapshot; once that is applied, every write to the
        // stack is one that what watches it is told of, as on the main thread of the app.
        Snapshot.sendApplyNotifications()
        val seen = mutableListOf<List<NavKey>>()
        val observer = Snapshot.registerApplyObserver { _, _ -> seen += stack.toList() }
        try {
            change(stack)
            // Changes made outside a snapshot of their own are told of here, all at once.
            Snapshot.sendApplyNotifications()
        } finally {
            observer.dispose()
        }
        return seen
    }

    private companion object {
        const val FAR_FUTURE = 4_102_444_800_000L
        val NOBODY = Account(installId = "install", session = null, callsLeft = null, ai = AiStatus.AVAILABLE)
        val MINH = NOBODY.copy(session = Session("token-1", FAR_FUTURE), signedInAs = "minh.tran@example.com")
        val MINH_EXPIRED = MINH.copy(session = null, signInExpired = true)
        val LAN = NOBODY.copy(session = Session("token-3", FAR_FUTURE), signedInAs = "lan@example.com")
    }
}
