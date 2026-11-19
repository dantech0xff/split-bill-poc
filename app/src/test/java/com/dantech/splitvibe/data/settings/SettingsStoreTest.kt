package com.dantech.splitvibe.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The consent to the AI as the phone keeps it: off until the person agrees, and taken back at once. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the AI is not allowed until the person agrees, and their answer is kept on the phone`() {
        val store = SettingsStore(context)
        assertFalse("a new install has not agreed", store.settings.value.aiAllowed)

        store.allowAi(true)

        assertTrue(store.settings.value.aiAllowed)
        assertTrue("as the app finds it when it starts again", SettingsStore(context).settings.value.aiAllowed)
        assertTrue(written().contains("""<boolean name="ai_allowed" value="true" />"""))
    }

    @Test
    fun `taking the consent back is on disk when it returns, whatever else the settings hold`() {
        val store = SettingsStore(context)
        store.allowAi(true)
        store.allowSpeechService(true)
        store.save("EUR")

        store.allowAi(false)

        assertFalse(store.settings.value.aiAllowed)
        assertTrue(written().contains("""<boolean name="ai_allowed" value="false" />"""))
        assertTrue("the other settings stay", store.settings.value.speechServiceAllowed)
    }

    /** The settings file as it is on disk now. */
    private fun written(): String = File(context.dataDir, "shared_prefs/settings.xml").readText()
}
