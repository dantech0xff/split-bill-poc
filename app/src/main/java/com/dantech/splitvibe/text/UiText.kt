package com.dantech.splitvibe.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.dantech.splitvibe.R

/**
 * Words for the person, decided where no resources are read: in view models, repositories and the
 * proxy's client. It names a string resource and the values that go into it, and the screen reads it
 * in the phone's language. Text that is not the app's own, such as a name someone typed, what the
 * model wrote or a message of `:core:domain`, is [Raw].
 */
sealed interface UiText {
    /** The string resource [id], with [args] for its placeholders. An argument that is a [UiText] is read first. */
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** The plurals resource [id] in the form for [count], with [args] for its placeholders. */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any>) : UiText

    /** Text shown as it is. */
    data class Raw(val text: String) : UiText

    /** [parts] one to a line, as in a message to send. */
    data class Lines(val parts: List<UiText>) : UiText

    /** [parts] one after the other with [separator] between them, such as the commas of a list. */
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    /** The text in the language of [resources]. */
    fun resolve(resources: Resources): String = when (this) {
        is Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *resolved(args, resources))
        is Plural -> resources.getQuantityString(id, count, *resolved(args, resources))
        is Raw -> text
        is Lines -> parts.joinToString("\n") { it.resolve(resources) }
        is Joined -> parts.joinToString(separator) { it.resolve(resources) }
    }
}

/** The string resource [id] with [args] for its placeholders. */
fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** The plurals resource [id] for [count], whose only placeholder is the count itself unless [args] are given. */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList().ifEmpty { listOf(count) })

/** Two sentences one after the other, the second left out when it is null. */
fun sentences(first: UiText, second: UiText?): UiText = if (second == null) first else text(R.string.two_sentences, first, second)

/** The text in the language the screen shows. */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = resolve(LocalResources.current)

private fun resolved(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()
