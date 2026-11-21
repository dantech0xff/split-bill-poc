package com.dantech.splitvibe.ui

import android.os.Bundle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The back stack the app saves comes back as it was, and a saved stack that names a screen this
 * version of the app no longer has, as one saved before the update that removed the screen would,
 * comes back without that screen instead of crashing the app as it starts.
 */
@RunWith(AndroidJUnit4::class)
class SavedBackStackTest {

    @Test
    fun `a saved stack that names a screen this version no longer has comes back without it`() = inASnapshotOfItsOwn {
        val restored = decodeFromSavedState(SavedBackStackSerializer, savedByAnEarlierVersion(HomeKey, HistoryKey, GoneKey))

        assertEquals(listOf(HomeKey, HistoryKey), restored.toList())
    }

    @Test
    fun `a saved stack that holds nothing but such a screen starts again from Home`() = inASnapshotOfItsOwn {
        val restored = decodeFromSavedState(SavedBackStackSerializer, savedByAnEarlierVersion(GoneKey))

        assertEquals(listOf<NavKey>(HomeKey), restored.toList())
    }

    @Test
    fun `a stack comes back whole, and in the form Navigation 3 itself saves and reads`() = inASnapshotOfItsOwn {
        val stack = NavBackStack<NavKey>(HomeKey, GroupKey("8f9a2c1e"), RequestLinkKey("token-1"), SplitKey)

        val saved = encodeToSavedState(SavedBackStackSerializer, stack)

        assertEquals(stack.toList(), decodeFromSavedState(SavedBackStackSerializer, saved).toList())
        assertEquals(stack.toList(), decodeFromSavedState(NAVIGATION_3, saved).toList())
        assertEquals(stack.toList(), decodeFromSavedState(SavedBackStackSerializer, encodeToSavedState(NAVIGATION_3, stack)).toList())
    }

    /**
     * [keys] saved as Navigation 3 saves a back stack, with [GoneKey] named as the screen of the
     * recurring bills that an earlier version had, whose class this version no longer has.
     */
    private fun savedByAnEarlierVersion(vararg keys: NavKey): Bundle {
        val saved = encodeToSavedState(NAVIGATION_3, NavBackStack(*keys))
        val renamed = rename(saved, from = GoneKey::class.java.name, to = REMOVED_SCREEN)
        assertEquals("the screens renamed", keys.count { it == GoneKey }, renamed)
        return saved
    }

    /**
     * Runs [block] in a snapshot of its own, as a back stack is a list of Compose's state, which the
     * app restores inside a composition. Changed outside one, it would be changed in the global snapshot
     * of the test process, whose changes Compose then tells later tests of other classes about late.
     */
    private fun inASnapshotOfItsOwn(block: () -> Unit) = Snapshot.withMutableSnapshot(block)

    /** Replaces the text [from] with [to] wherever [state], or a state within it, holds it; returns how often. */
    private fun rename(state: Bundle, from: String, to: String): Int = state.keySet().sumOf { name ->
        @Suppress("DEPRECATION") // The type of each value is not known in advance here.
        when (val value = state.get(name)) {
            from -> 1.also { state.putString(name, to) }
            is Bundle -> rename(value, from, to)
            is Iterable<*> -> value.filterIsInstance<Bundle>().sumOf { rename(it, from, to) }
            is Array<*> -> value.filterIsInstance<Bundle>().sumOf { rename(it, from, to) }
            else -> 0
        }
    }

    private companion object {
        /** How Navigation 3 saves and reads a back stack on its own. */
        val NAVIGATION_3 = NavBackStackSerializer(NavKeySerializer<NavKey>())

        /** The class of the screen of the recurring bills that an earlier version of the app had. */
        const val REMOVED_SCREEN = "com.dantech.splitvibe.ui.RecurringKey"
    }
}

/** A screen of an earlier version of the app, which [SavedBackStackTest] renames to a class that no longer exists. */
@Serializable
internal data object GoneKey : NavKey
