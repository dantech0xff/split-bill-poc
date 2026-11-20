package com.dantech.splitvibe.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.dantech.splitvibe.data.account.Account
import java.security.MessageDigest

/*
 * How the navigation bar, back and a change of account move the one stack of open screens. Home is
 * always at the bottom of the stack, and at most one of the other main screens stands right above
 * it: the bar shows only on the main screens, so a tab never has a history of its own to keep.
 */

/** The main screens, in the order of the navigation bar. */
internal val MAIN_SCREENS: List<NavKey> = listOf(HomeKey, GroupsKey, RequestsKey, InsightsKey)

/**
 * The stack once [tab] is chosen on the navigation bar: Home alone for Home, and any other main
 * screen right above Home, in place of the one that was there.
 */
internal fun stackAfterChoosing(tab: NavKey): List<NavKey> {
    require(tab in MAIN_SCREENS) { "$tab is not on the navigation bar" }
    return if (tab == HomeKey) listOf(HomeKey) else listOf(HomeKey, tab)
}

/**
 * The stack once the account has left: signed out, deleted, or replaced by another account that
 * signed in. Home stays, and so does Settings, where that is done, with what was opened from it, such
 * as Pro or the ways to get paid. Every other screen was opened for the account and goes with it, so
 * without Settings in the stack, as when a deletion ended after Settings was left, Home stays alone.
 */
internal fun stackAfterAccountLeft(stack: List<NavKey>): List<NavKey> {
    val settings = stack.lastIndexOf(SettingsKey)
    return if (settings < 0) listOf(HomeKey) else listOf(HomeKey) + stack.drop(settings)
}

/**
 * Who holds the place of the account on this phone, as far as the open screens need to know: null
 * while nobody is signed in, and otherwise a value that stays the same while one Google account is
 * signed in, also once its sign-in ran out, and differs for another account. It is a hash of the
 * account's address, as the open screens keep it with their saved state, which is no place for an
 * address.
 */
internal fun accountHolderOf(account: Account): String? = when {
    account.session == null && !account.signInExpired -> null
    else -> account.signedInAs?.let(::hashOf) ?: UNKNOWN_HOLDER
}

/** Whether the account that held the place as [before] has left by [after]: signed out, deleted, or replaced by another. */
internal fun accountLeft(before: String?, after: String?): Boolean = before != null && before != after

/** Opens the main screen [tab] that the navigation bar chose. */
internal fun NavBackStack<NavKey>.choose(tab: NavKey) = showOnly(stackAfterChoosing(tab))

/**
 * Goes back one screen, but never from Home, which stays at the bottom: a back swipe that was held
 * while the navigation bar chose Home would otherwise take Home itself away when it is let go.
 */
internal fun NavBackStack<NavKey>.goBack() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * Makes this stack [target] in one change, so the screens see a single move from the stack before to
 * the one after, never a stack halfway between, which would show as a step back and then forward.
 * The screens at the bottom that both stacks share stay as they are, with their state; a stack that
 * is [target] already is not changed at all.
 */
internal fun NavBackStack<NavKey>.showOnly(target: List<NavKey>) {
    require(target.isNotEmpty()) { "A stack of screens is never empty" }
    val common = minOf(size, target.size)
    val shared = (0 until common).firstOrNull { this[it] != target[it] } ?: common
    if (shared == size && shared == target.size) return
    // Applied together when the snapshot ends: whatever watches the stack sees only its new state.
    Snapshot.withMutableSnapshot {
        repeat(size - shared) { removeAt(lastIndex) }
        addAll(target.subList(shared, target.size))
    }
}

/** A sign-in kept from a version of the app that did not keep the account's address. */
private const val UNKNOWN_HOLDER = "unknown"

private fun hashOf(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
}
