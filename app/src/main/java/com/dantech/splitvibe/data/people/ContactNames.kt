package com.dantech.splitvibe.data.people

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the name of a contact picked in the system's contact picker. The picker lets the app read
 * that one contact; the app holds no permission to read the others.
 */
class ContactNames(private val resolver: ContentResolver) {

    /**
     * The contact's name, or null when it has none, can no longer be read, or [contact] is not a
     * contact at all, which another app answering the picker could hand back.
     */
    suspend fun nameOf(contact: Uri): String? = withContext(Dispatchers.IO) {
        if (contact.scheme != ContentResolver.SCHEME_CONTENT || contact.userInfo != null || contact.host != ContactsContract.AUTHORITY) {
            return@withContext null
        }
        try {
            resolver.query(contact, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (column < 0 || !cursor.moveToFirst()) return@use null
                cursor.getString(column)?.trim()?.take(MAX_NAME_CHARS)?.takeIf { it.isNotEmpty() }
            }
        } catch (e: RuntimeException) {
            // A contact that went away while it was read, or a provider that answers oddly.
            null
        }
    }

    private companion object {
        /** As long as a name typed into the app can be. */
        const val MAX_NAME_CHARS = 80
    }
}
