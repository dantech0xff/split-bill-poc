package com.dantech.splitvibe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.serializer

/**
 * The app's back stack, which starts at Home and comes back after the system ends the app, kept as
 * Navigation 3 keeps it but restored by [SavedBackStackSerializer].
 */
@Composable
internal fun rememberAppBackStack(): NavBackStack<NavKey> = rememberSerializable(serializer = SavedBackStackSerializer) {
    NavBackStack(HomeKey)
}

/**
 * Keeps the back stack as Navigation 3 keeps it, each screen by the name of its key's class with the
 * key's fields, and restores it forgivingly: a screen whose key this version of the app no longer
 * has, or can no longer read, such as one that was removed, is left out, and a stack with no screen
 * left starts again from Home. An app updated while its saved state held such a screen then opens
 * on what it can show, rather than crashing as it starts.
 */
internal object SavedBackStackSerializer : KSerializer<NavBackStack<NavKey>> {
    private val stack = NavBackStackSerializer(ForgivingKeySerializer)

    override val descriptor: SerialDescriptor = stack.descriptor

    override fun serialize(encoder: Encoder, value: NavBackStack<NavKey>) = stack.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): NavBackStack<NavKey> = stack.deserialize(decoder).apply {
        removeAll { it == UnknownKey }
        if (isEmpty()) add(HomeKey)
    }
}

/** A screen that a restored state names but this version of the app cannot show; it never stays in the stack. */
private data object UnknownKey : NavKey

/**
 * Navigation 3's [NavKeySerializer], in the same form, but a key whose class is gone, or whose fields
 * cannot be read, comes back as [UnknownKey] rather than failing the whole restore.
 */
private object ForgivingKeySerializer : KSerializer<NavKey> {
    private val keys = NavKeySerializer<NavKey>()

    override val descriptor: SerialDescriptor = keys.descriptor

    override fun serialize(encoder: Encoder, value: NavKey) = keys.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): NavKey = decoder.decodeStructure(descriptor) {
        var serializer: KSerializer<Any>? = null
        var key: NavKey = UnknownKey
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CLASS_NAME -> serializer = serializerOf(decodeStringElement(descriptor, index))

                // The fields of a key whose class is gone are left unread.
                VALUE -> key = serializer?.let { keyOf(it) } ?: UnknownKey

                CompositeDecoder.DECODE_DONE -> break

                else -> throw SerializationException("A saved screen has no element $index")
            }
        }
        key
    }

    /** The serializer of the key class named [className], or null when this version has no such class or cannot read it. */
    private fun serializerOf(className: String): KSerializer<Any>? = try {
        serializer(Class.forName(className))
    } catch (e: ClassNotFoundException) {
        null
    } catch (e: SerializationException) {
        null
    }

    /** The key the value element holds, read with [serializer], or null when its fields cannot be read. */
    private fun CompositeDecoder.keyOf(serializer: KSerializer<Any>): NavKey? = try {
        decodeSerializableElement(descriptor, VALUE, serializer) as? NavKey
    } catch (e: SerializationException) {
        null
    }

    /** The elements of a saved key, in the order of Navigation 3's [NavKeySerializer]: the class's name, then its fields. */
    private const val CLASS_NAME = 0
    private const val VALUE = 1
}
