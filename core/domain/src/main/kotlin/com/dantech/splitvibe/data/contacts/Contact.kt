package com.dantech.splitvibe.data.contacts

/**
 * Another user of the payment app.
 *
 * @param name full display name, for example "Trần Quang Minh".
 * @param givenName the name friends use, for example "Minh". It is the last word of a Vietnamese
 *   name and the first word of an English one, so it is stored rather than guessed.
 * @param handle in-app username without the leading "@".
 */
data class Contact(
    val id: String,
    val name: String,
    val givenName: String,
    val handle: String,
)
