package com.dantech.splitvibe.text

import android.content.res.Configuration
import android.content.res.Resources
import android.icu.text.MessageFormat
import android.icu.util.ULocale
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.dantech.splitvibe.R
import com.dantech.splitvibe.domain.TEXT_LOCALE

/**
 * Words for the person, decided where no resources are read: in view models, repositories and the
 * proxy's client. It names a string resource and the values that go into it, and the screen reads it
 * in the phone's language. Text that is not the app's own, such as a name someone typed, what the
 * model wrote or a message of `:core:domain`, is [Raw].
 *
 * The values go into the text as [TEXT_LOCALE] writes them, never as the phone's locale does: a
 * count is "300" on a phone set to Persian too, like every other number in the English texts, and a
 * plural takes its form by the rules of English, "1 bill" on a phone set to Vietnamese too. Every
 * text with a count is read through here, and plurals only through here.
 */
sealed interface UiText {
    /** The string resource [id], with [args] for its placeholders. An argument that is a [UiText] is read first. */
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** The plurals resource [id] in the form for [count], with [args] for its placeholders. */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any>) : UiText

    /**
     * The string resource [id], an ICU message such as "on the {0,selectordinal,one{#st} other{#th}}",
     * with [args] for its arguments, read with the rules of [TEXT_LOCALE]: grammar a plural cannot
     * hold, such as the ending of an ordinal, stays in the resources.
     */
    data class Message(@param:StringRes val id: Int, val args: List<Any>) : UiText

    /** Text shown as it is. */
    data class Raw(val text: String) : UiText

    /** [parts] one to a line, as in a message to send. */
    data class Lines(val parts: List<UiText>) : UiText

    /** [parts] one after the other with [separator] between them, such as the commas of a list. */
    data class Joined(val parts: List<UiText>, val separator: String) : UiText

    /** The text in the language of [resources]. */
    fun resolve(resources: Resources): String = when (this) {
        is Res -> if (args.isEmpty()) resources.getString(id) else formatted(resources.getString(id), args, resources)
        is Plural -> formatted(pluralsOf(resources).getQuantityString(id, count), args, resources)
        is Message -> MessageFormat(resources.getString(id), ULocale.forLocale(TEXT_LOCALE)).format(resolved(args, resources))
        is Raw -> text
        is Lines -> parts.joinToString("\n") { it.resolve(resources) }
        is Joined -> parts.joinToString(separator) { it.resolve(resources) }
    }
}

/** The string resource [id] with [args] for its placeholders. */
fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** The plurals resource [id] for [count], whose only placeholder is the count itself unless [args] are given. */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList().ifEmpty { listOf(count) })

/** The ICU message of the string resource [id] with [args] for its arguments. */
fun message(@StringRes id: Int, vararg args: Any): UiText = UiText.Message(id, args.toList())

/** Two sentences one after the other, the second left out when it is null. */
fun sentences(first: UiText, second: UiText?): UiText = if (second == null) first else text(R.string.two_sentences, first, second)

/** The text in the language the screen shows. */
@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = resolve(LocalResources.current)

/** [pattern] with [args] in its placeholders, as [Resources.getString] fills them in but in [TEXT_LOCALE]. */
private fun formatted(pattern: String, args: List<Any>, resources: Resources): String =
    String.format(TEXT_LOCALE, pattern, *resolved(args, resources))

private fun resolved(args: List<Any>, resources: Resources): Array<Any> =
    args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray()

/**
 * The resources that pick the form of a plural of the app's texts, which are English on every phone,
 * so their plurals follow English: "1 bill" and "2 bills". In its own language a phone set to
 * Vietnamese has one form for every count, and one set to French takes the singular for 0 too, so
 * [resources] then give way to the same resources read in [TEXT_LOCALE]. Only the choice of form
 * changes that way: the texts are the same, and the amounts and dates that go into them stay as the
 * phone's region writes them. A phone set to English keeps [resources], and so do the pseudolocales of
 * a debug build, English texts in another dress whose rules give the singular to 1 alone, as English does.
 */
private fun pluralsOf(resources: Resources): Resources {
    val locale = resources.configuration.locales[0]
    val english = locale.language == TEXT_LOCALE.language || locale.country in PSEUDOLOCALE_REGIONS
    return if (english) resources else EnglishResources.of(resources)
}

/** The app's resources read in [TEXT_LOCALE], made again only when the phone's configuration changes. */
private object EnglishResources {
    private var made: Pair<Configuration, Resources>? = null

    @Synchronized
    fun of(resources: Resources): Resources {
        val phone = resources.configuration
        made?.let { (from, english) -> if (from == phone) return english }
        // Texts are read where no context is at hand, such as in a notification or in the text of a share,
        // so these resources are made from the app's own assets with only their locale changed.
        @Suppress("DEPRECATION")
        val english = Resources(resources.assets, resources.displayMetrics, Configuration(phone).apply { setLocale(TEXT_LOCALE) })
        made = Configuration(phone) to english
        return english
    }
}

/** The regions of the pseudolocales en-XA and ar-XB that debug builds carry. */
private val PSEUDOLOCALE_REGIONS = setOf("XA", "XB")
