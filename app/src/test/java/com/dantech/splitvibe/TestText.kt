package com.dantech.splitvibe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dantech.splitvibe.text.UiText

/** The text as the app shows it in English, read from the app's resources; for tests that run under Robolectric. */
fun UiText?.inEnglish(): String? = this?.resolve(ApplicationProvider.getApplicationContext<Context>().resources)

/** The same, for a text that is there. */
fun UiText.english(): String = resolve(ApplicationProvider.getApplicationContext<Context>().resources)
