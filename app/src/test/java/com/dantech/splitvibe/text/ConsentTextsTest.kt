package com.dantech.splitvibe.text

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The consent to the AI, and what Settings says the user agreed to, say word for word what users
 * agreed to: that the text goes to Claude, made by Anthropic, in the United States, and what is sent.
 * Elsewhere the app speaks of "the AI" in its own voice; these texts never change with it. A change
 * to one of them is a change to what users consent to, and comes with a new copy recorded here.
 */
@RunWith(AndroidJUnit4::class)
class ConsentTextsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every text of the consent to the AI is the one recorded, word for word`() {
        // Also a consent text added or taken away: each one in the resources has its copy here.
        val inResources = CONSENT.findAll(File("src/main/res/values/strings.xml").readText()).map { it.groupValues[2] }.toSet()
        assertEquals("the consent's texts", RECORDED.keys.filter { it.startsWith(CONSENT_PREFIX) }.toSet(), inResources)

        for ((name, recorded) in RECORDED) {
            val id = context.resources.getIdentifier(name, "string", context.packageName)
            assertNotEquals("$name is in the resources", 0, id)
            assertEquals(name, recorded, context.getString(id))
        }
    }

    private companion object {
        const val CONSENT_PREFIX = "ai_consent_"

        /** A string or plurals resource whose name starts with [CONSENT_PREFIX]. */
        val CONSENT = Regex("""<(string|plurals) name="($CONSENT_PREFIX[^"]*)"""")

        /** The texts as users read them when they agreed, recorded on 20 November 2026. */
        val RECORDED = mapOf(
            "ai_consent_title" to "Use the AI for your bills?",
            "ai_consent_where" to "Split Vibe's AI is Claude, made by Anthropic. To use it, Split Vibe sends text through its own server " +
                "to Anthropic in the United States, which works on it and sends back the answer.",
            "ai_consent_bill_read" to "Reading a bill sends the text this phone read from its photo, the instruction on how to split it, " +
                "and the currency you count in. The photo stays on this phone.",
            "ai_consent_split_edit" to "Changing a split by a sentence sends what you typed or said, with the bill: the shop, its items, " +
                "charges and amounts, the names of the people on it, yours too, how it is split and who paid.",
            "ai_consent_choice" to
                "You can take this back in Settings at any time. Without it, you type bills in by hand, and everything " +
                "else in Split Vibe works the same.",
            "ai_consent_agree" to "Agree",
            "ai_consent_keep_split" to "Keep the split as it is",
            "settings_ai_consent_given" to "You agreed that Split Vibe sends the text of your bills, and the changes you ask for, " +
                "to Anthropic in the United States.",
        )
    }
}
