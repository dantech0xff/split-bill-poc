package com.dantech.splitvibe.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app under test. The build passes the id, so the dev and prod flavors are each measured as themselves. */
internal val targetAppId: String
    get() = checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
        "The targetAppId argument is missing. Run these tests through Gradle."
    }

/**
 * The first steps of a split: open the split screen from the round button in the middle of the
 * navigation bar, which shows no text and is found by what it says to a screen reader, pick the
 * bundled sample bill, go back home. Reading the bill needs the model, so this stops before that step.
 */
internal fun MacrobenchmarkScope.startASplit() {
    device.wait(Until.findObject(By.desc("Split a bill")), TIMEOUT_MS).click()
    device.wait(Until.findObject(By.text("Use the sample bill")), TIMEOUT_MS).click()
    device.wait(Until.hasObject(By.desc("Bill photo")), TIMEOUT_MS)
    device.pressBack()
    device.wait(Until.hasObject(By.text("Recent bills")), TIMEOUT_MS)
}

/**
 * Goes round the four tabs of the navigation bar, the main way round the app: Groups, Requests and
 * Insights, then Home again. Each tab is found as the device tests find it, as a screen reader does,
 * by what it says, and the round goes on once that tab shows as selected.
 */
internal fun MacrobenchmarkScope.goRoundTheTabs() {
    for (label in listOf("Groups", "Requests", "Insights", "Home")) {
        device.wait(Until.findObject(tab(label)), TIMEOUT_MS).click()
        device.wait(Until.hasObject(tab(label).selected(true)), TIMEOUT_MS)
    }
    device.wait(Until.hasObject(By.text("Recent bills")), TIMEOUT_MS)
}

/** The tab of the navigation bar called [label], also with the count it may carry, as in "Requests, 2". */
private fun tab(label: String): BySelector = By.desc(Pattern.compile(Pattern.quote(label) + "(, .*)?"))

private const val TIMEOUT_MS = 10_000L
