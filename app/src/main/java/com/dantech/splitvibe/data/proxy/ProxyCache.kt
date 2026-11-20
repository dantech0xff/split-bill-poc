package com.dantech.splitvibe.data.proxy

import com.dantech.splitvibe.data.bills.ProxyCacheDao
import com.dantech.splitvibe.data.bills.ProxyCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The proxy's last answers about groups and requests, kept on the phone so they can be shown
 * without a connection. It holds only what the account signed in now may read: it is emptied on
 * every sign-in, sign-out and account deletion, and an answer to a call made before that is never
 * kept, however late it arrives.
 */
class ProxyCache(private val dao: ProxyCacheDao, private val clock: () -> Long = System::currentTimeMillis) {

    private val lock = Mutex()

    /** Moves on at every [clear]; a call notes it before it goes out, and keeps its answer only if it still holds. */
    @Volatile
    var generation: Long = 0
        private set

    /** What is kept under [key], read and decoded away from the main thread; null when nothing usable is. */
    fun <T> watch(key: String, serializer: KSerializer<T>): Flow<T?> = dao.watch(key)
        .map { it?.let { entry -> decode(entry, serializer) } }
        // A row the database cannot read back, such as one grown too large, shows as nothing kept.
        .catch { emit(null) }
        .flowOn(Dispatchers.Default)

    /**
     * The last thing kept under a key that starts with [prefix], read and decoded away from the main
     * thread; null when nothing is kept under such a key, or the last thing kept cannot be read. It
     * comes again only when that row changes, not on every write to the cache, which the database
     * would otherwise report each time.
     */
    fun <T> watchLatest(prefix: String, serializer: KSerializer<T>): Flow<T?> = dao.watchLatest(prefix)
        .distinctUntilChanged()
        .map { it?.let { entry -> decode(entry, serializer) } }
        .catch { emit(null) }
        .flowOn(Dispatchers.Default)

    suspend fun <T> get(key: String, serializer: KSerializer<T>): T? = runCatching {
        dao.get(key)
    }.getOrNull()?.let { decode(it, serializer) }

    /** Keeps [value] under [key], unless the cache was emptied since [since], when it belongs to whoever was signed in then. */
    suspend fun <T> put(key: String, serializer: KSerializer<T>, value: T, since: Long) = lock.withLock {
        if (since == generation) dao.put(ProxyCacheEntity(key, json.encodeToString(serializer, value), clock()))
    }

    suspend fun remove(key: String) = dao.remove(key)

    /**
     * Removes what is kept under the keys that start with [prefix] but those in [keep], unless the
     * cache was emptied since [since]: what it keeps then belongs to whoever signed in after, which an
     * answer to a call made before has nothing to say about.
     */
    suspend fun removeUnder(prefix: String, keep: Set<String>, since: Long) = lock.withLock {
        if (since == generation) dao.removeUnder(prefix, keep.toList())
    }

    suspend fun clear() = lock.withLock {
        generation++
        dao.clear()
    }

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
