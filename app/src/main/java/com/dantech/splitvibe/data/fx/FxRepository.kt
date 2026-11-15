package com.dantech.splitvibe.data.fx

import com.dantech.splitvibe.data.proxy.ProxyApi
import com.dantech.splitvibe.data.proxy.ProxyException
import com.dantech.splitvibe.domain.BigDecimalAsString
import com.dantech.splitvibe.domain.ExchangeRate
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One currency's rate in a [FxSnapshot]: how many units of it one unit of the base buys, and the day of that rate. */
@Serializable
data class DayRate(@Serializable(with = BigDecimalAsString::class) val rate: BigDecimal, val date: String)

/** The exchange rates the proxy hands out, all against one [base] currency. */
@Serializable
data class FxSnapshot(
    val base: String,
    /** Who published the rates, such as "Frankfurter". */
    val source: String,
    /** When the proxy fetched them from [source]. */
    val fetchedAt: Long,
    val rates: Map<String, DayRate>,
) {
    /** The rate from [from] to [to], or null when [source] has no rate for either. */
    fun rate(from: String, to: String): ExchangeRate? =
        ExchangeRate.between(from, to, rates.mapValues { (_, day) -> day.rate to day.date }, source)
}

/**
 * The day's exchange rates, from the app's proxy. The last ones fetched are kept on the phone, so a
 * bill can still be converted without a connection, with the day of the rates it used.
 */
class FxRepository(private val api: ProxyApi, private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    private var kept: Kept? = null

    /** When fetching last failed, so that a phone without a connection does not try on every bill it opens. */
    private var failedAt: Long? = null

    /** The rate from [from] to [to]: from fresh rates when they can be fetched, else from the last ones kept. */
    suspend fun rate(from: String, to: String): ExchangeRate? = snapshot()?.rate(from, to)

    /** The rates, fetched again when those kept are more than [MAX_AGE_MS] old; null when there have never been any. */
    suspend fun snapshot(): FxSnapshot? = lock.withLock {
        val held = kept ?: read()?.also { kept = it }
        if (held != null && clock() - held.savedAt < MAX_AGE_MS) return held.snapshot
        if (failedAt?.let { clock() - it < RETRY_AFTER_MS } == true) return held?.snapshot
        val fresh = fetch()
        if (fresh == null) {
            failedAt = clock()
            return held?.snapshot
        }
        failedAt = null
        Kept(clock(), fresh).also {
            kept = it
            write(it)
        }.snapshot
    }

    private suspend fun fetch(): FxSnapshot? {
        if (!api.isSetUp) return null
        return try {
            api.fxRates()
        } catch (e: IOException) {
            null
        } catch (e: ProxyException) {
            null
        }
    }

    private suspend fun read(): Kept? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(Kept.serializer(), file.readText())
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            // A copy this version cannot read is fetched again.
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private suspend fun write(kept: Kept) = withContext(Dispatchers.IO) {
        try {
            // Written beside the file and moved over it, so a copy that is cut off never replaces a whole one.
            val partial = File(file.parentFile, "${file.name}.partial")
            partial.writeText(json.encodeToString(Kept.serializer(), kept))
            if (!partial.renameTo(file)) partial.delete()
        } catch (e: IOException) {
            // Keeping the rates only saves a fetch next time.
        }
    }

    @Serializable
    private data class Kept(val savedAt: Long, val snapshot: FxSnapshot)

    private companion object {
        /** The proxy refreshes the rates once a day; a copy younger than this is used as it is. */
        const val MAX_AGE_MS = 60 * 60 * 1000L
        const val RETRY_AFTER_MS = 5 * 60 * 1000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
