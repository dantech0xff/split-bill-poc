package com.dantech.splitvibe.data.account

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** How an account deletion ended, once it changed something; Settings says it the next time it shows. */
enum class DeletionOutcome {
    /** The proxy deleted the account, and the phone forgot it. */
    DELETED,

    /** The same, and what only the phone keeps was deleted with it, as the person asked. */
    DELETED_WITH_PHONE_DATA,

    /** The proxy deleted the account, but the groups and requests the phone kept of it could not all be deleted. */
    ACCOUNT_DATA_NOT_DELETED,

    /** The proxy deleted the account, but what only the phone keeps could not all be deleted with it. */
    PHONE_DATA_NOT_DELETED,

    /**
     * A deletion that the app was ended in the middle of, before the proxy's answer came, could not
     * be finished: the proxy no longer took the session, which is how it answers once it deleted the
     * account, and also once a sign-in ran out. Nothing was deleted on the phone.
     */
    NOT_CONFIRMED,
}

/** A deletion that has started and not ended: what the person chose, and whether the proxy has deleted the account yet. */
data class PendingDeletion(val withPhoneData: Boolean, val proxyDeleted: Boolean)

/**
 * What the phone remembers of an account deletion: the person's choice about what only the phone
 * keeps, before the request goes out; that the proxy has deleted the account, once it said so; and
 * how the deletion ended, until Settings has said it. Each step is on disk before the next one
 * starts, so a deletion the app is ended in the middle of is finished when it starts again.
 */
class DeletionStore(context: Context) {
    private val preferences = context.getSharedPreferences("account_deletion", Context.MODE_PRIVATE)

    /** The deletion that has started and not ended, or null. */
    val pending: PendingDeletion?
        get() = if (preferences.contains(KEY_WITH_PHONE_DATA)) {
            PendingDeletion(preferences.getBoolean(KEY_WITH_PHONE_DATA, false), preferences.getBoolean(KEY_PROXY_DELETED, false))
        } else {
            null
        }

    /** How the last deletion ended, until [outcomeShown]. */
    val outcome: DeletionOutcome?
        get() = preferences.getString(KEY_OUTCOME, null)?.let { name -> DeletionOutcome.entries.firstOrNull { it.name == name } }

    /** A deletion starts, with or without what only the phone keeps; an outcome not said yet belongs to the one before. */
    fun begin(withPhoneData: Boolean) = write {
        putBoolean(KEY_WITH_PHONE_DATA, withPhoneData)
        remove(KEY_PROXY_DELETED)
        remove(KEY_OUTCOME)
    }

    /** The proxy answered that it deleted the account; what is left to do is on the phone. */
    fun proxyDeleted() = write { putBoolean(KEY_PROXY_DELETED, true) }

    /** The deletion ended: as [outcome] when it changed something, or with nothing to say when it did not. */
    fun end(outcome: DeletionOutcome?) = write {
        remove(KEY_WITH_PHONE_DATA)
        remove(KEY_PROXY_DELETED)
        if (outcome == null) remove(KEY_OUTCOME) else putString(KEY_OUTCOME, outcome.name)
    }

    /** Settings has said how the last deletion ended. */
    fun outcomeShown() = write { remove(KEY_OUTCOME) }

    // On disk before the call returns: the next step may be the network, or the process may end.
    // When the disk refuses the write, the deletion goes on all the same, as it did before it was
    // written down; only finishing it after the app is ended then cannot happen.
    private fun write(changes: SharedPreferences.Editor.() -> Unit) = preferences.edit(commit = true, action = changes)

    private companion object {
        const val KEY_WITH_PHONE_DATA = "with_phone_data"
        const val KEY_PROXY_DELETED = "proxy_deleted"
        const val KEY_OUTCOME = "outcome"
    }
}
