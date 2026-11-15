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

    /** The contact's name, or null when it has none or can no longer be read. */
    suspend fun nameOf(contact: Uri): String? = withContext(Dispatchers.IO) {
        try {
            resolver.query(contact, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
