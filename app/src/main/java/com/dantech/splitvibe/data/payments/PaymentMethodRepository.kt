package com.dantech.splitvibe.data.payments

import com.dantech.splitvibe.data.bills.PaymentMethodEntity
import com.dantech.splitvibe.data.bills.SplitVibeDatabase
import com.dantech.splitvibe.domain.payments.PaymentMethod
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A payment method the user saved, and whether requests use it unless another one is picked. */
data class SavedPaymentMethod(val id: String, val method: PaymentMethod, val isMain: Boolean)

/**
 * The ways the user takes money, kept on the phone only. A payment request carries a copy of the one
 * it is sent with, so nobody else ever sees the list, and deleting the account leaves nothing of it
 * on the server.
 */
class PaymentMethodRepository(database: SplitVibeDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val methods = database.paymentMethods()

    /** The main method first, then the others in the order they were added. */
    val all: Flow<List<SavedPaymentMethod>> = methods.all().map { rows -> rows.mapNotNull(::savedOf) }

    suspend fun get(id: String): SavedPaymentMethod? = methods.get(id)?.let(::savedOf)

    /** Saves [method] as a new one, which becomes the main one when it is the first. */
    suspend fun add(method: PaymentMethod): String {
        require(method.problem() == null) { "A method with a problem cannot be saved" }
        val id = UUID.randomUUID().toString()
        methods.upsert(PaymentMethodEntity(id, json.encodeToString(PaymentMethod.serializer(), method), clock(), isMain = false))
        // The first method is the main one; a later one leaves the main one as it is.
        methods.makeMainIfNone(id)
        return id
    }

    /** Puts [method] in place of the method saved as [id], keeping whether it is the main one. */
    suspend fun replace(id: String, method: PaymentMethod) {
        require(method.problem() == null) { "A method with a problem cannot be saved" }
        val stored = methods.get(id) ?: return
        methods.upsert(stored.copy(document = json.encodeToString(PaymentMethod.serializer(), method)))
    }

    suspend fun makeMain(id: String) = methods.makeMain(id)

    suspend fun remove(id: String) = methods.delete(id)

    // A method this version of the app cannot read, such as one saved by a newer version, is left out.
    private fun savedOf(row: PaymentMethodEntity): SavedPaymentMethod? = try {
        SavedPaymentMethod(row.id, json.decodeFromString(PaymentMethod.serializer(), row.document), row.isMain)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
