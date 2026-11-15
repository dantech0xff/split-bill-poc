package com.dantech.splitvibe.data.requests

import com.dantech.splitvibe.data.proxy.ProxyCache
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.domain.payments.PaymentMethod
import java.security.MessageDigest
import java.util.UUID

/**
 * The id of a request for [purpose], such as one person's share of one bill, asking [amountMinor] of
 * [currency] to be paid with [method]. Asking again for exactly the same thing gives the same id, so
 * a second tap or a retry makes no second request; a changed amount or a corrected account gives a
 * new one, which then takes the old one's place.
 */
fun requestIdFor(purpose: String, amountMinor: Long, currency: BillCurrency, method: PaymentMethod): String {
    val methodHash = fingerprintOf(method)
    return UUID.nameUUIDFromBytes("$purpose/$amountMinor/${currency.code}/${currency.fractionDigits}/$methodHash".toByteArray()).toString()
}

/** A SHA-256 of [method] as it is kept, which changes when any of its details do, such as a corrected account. */
fun fingerprintOf(method: PaymentMethod): String = sha256Of(ProxyCache.json.encodeToString(PaymentMethod.serializer(), method))

/** The SHA-256 of [text] in UTF-8, in hex. */
fun sha256Of(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
