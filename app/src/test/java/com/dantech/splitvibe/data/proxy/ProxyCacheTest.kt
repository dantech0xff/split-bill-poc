package com.dantech.splitvibe.data.proxy

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.TestStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the cache keeps, such as the state of a large group, is read back as it was kept, and turned
 * into text and back away from the main thread, also when a screen asks for it from that thread.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProxyCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = TestStorage(context)
    private val cache = ProxyCache(storage.database.proxyCache())

    @After
    fun tearDown() = storage.close()

    @Test
    fun `what is kept is read back as it was, encoded and decoded away from the main thread`() {
        val kept = listOf("Chiang Mai trip", "Flat 3B")
        val noting = NotingThreads(ListSerializer(String.serializer()))
        // Under Robolectric the test runs on the main thread, the one a screen asks from.
        val main = Thread.currentThread()
        assertTrue("The test does not run on the main thread", Looper.getMainLooper().isCurrentThread)

        val read = runBlocking {
            cache.put(KEY, noting, kept, since = cache.generation)
            cache.get(KEY, noting)
        }

        assertEquals(kept, read)
        assertNotSame("Encoded on the main thread", main, checkNotNull(noting.encodedOn) { "Never encoded" })
        assertNotSame("Decoded on the main thread", main, checkNotNull(noting.decodedOn) { "Never decoded" })
    }

    @Test
    fun `nothing kept under a key reads as nothing, without decoding anything`() {
        val noting = NotingThreads(String.serializer())

        assertNull(runBlocking { cache.get(KEY, noting) })
        assertNull(noting.decodedOn)
    }

    /** [serializer] that notes the thread it last encoded and decoded on. */
    private class NotingThreads<T>(private val serializer: KSerializer<T>) : KSerializer<T> by serializer {
        @Volatile
        var encodedOn: Thread? = null

        @Volatile
        var decodedOn: Thread? = null

        override fun serialize(encoder: Encoder, value: T) {
            encodedOn = Thread.currentThread()
            serializer.serialize(encoder, value)
        }

        override fun deserialize(decoder: Decoder): T {
            decodedOn = Thread.currentThread()
            return serializer.deserialize(decoder)
        }
    }

    private companion object {
        const val KEY = "groups"
    }
}
