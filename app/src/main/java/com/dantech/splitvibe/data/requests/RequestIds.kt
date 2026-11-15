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
    val methodJson = ProxyCache.json.encodeToString(PaymentMethod.serializer(), method)
    val methodHash = MessageDigest.getInstance("SHA-256").digest(methodJson.toByteArray()).joinToString("") { "%02x".format(it) }
    return UUID.nameUUIDFromBytes("$purpose/$amountMinor/${currency.code}/${currency.fractionDigits}/$methodHash".toByteArray()).toString()
}
