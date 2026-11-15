package com.dantech.splitvibe.data.proxy

import com.dantech.splitvibe.data.bills.ProxyCacheDao
import com.dantech.splitvibe.data.bills.ProxyCacheEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The proxy's last answers about groups and requests, kept on the phone so they can be shown
 * without a connection. It holds only what the account signed in now may read: it is emptied on
 * every sign-in, sign-out and account deletion.
 */
class ProxyCache(private val dao: ProxyCacheDao, private val clock: () -> Long = System::currentTimeMillis) {

    fun <T> watch(key: String, serializer: KSerializer<T>): Flow<T?> = dao.watch(key).map { it?.let { entry -> decode(entry, serializer) } }

    suspend fun <T> get(key: String, serializer: KSerializer<T>): T? = dao.get(key)?.let { decode(it, serializer) }

    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T) =
        dao.put(ProxyCacheEntity(key, json.encodeToString(serializer, value), clock()))

    suspend fun remove(key: String) = dao.remove(key)

    suspend fun clear() = dao.clear()

    // What an older version of the app kept in another shape is simply not shown; the next answer replaces it.
    private fun <T> decode(entry: ProxyCacheEntity, serializer: KSerializer<T>): T? = try {
        json.decodeFromString(serializer, entry.document)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /** Reads the proxy's answers, which may carry fields a newer proxy added. */
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
