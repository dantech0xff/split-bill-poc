package com.dantech.splitvibe.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The app under test. The build passes the id, so the dev and prod flavors are each measured as themselves. */
internal val targetAppId: String
    get() = checkNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
        "The targetAppId argument is missing. Run these tests through Gradle."
    }

/**
 * The first steps of a split: open the split screen, pick the bundled sample bill, go back home.
 * Reading the bill needs the model, so this stops before that step.
 */
internal fun MacrobenchmarkScope.startASplit() {
    device.wait(Until.findObject(By.text("Split a bill")), TIMEOUT_MS).click()
    device.wait(Until.findObject(By.text("Use the sample bill")), TIMEOUT_MS).click()
    device.wait(Until.hasObject(By.desc("Bill photo")), TIMEOUT_MS)
    device.pressBack()
    device.wait(Until.hasObject(By.text("Contacts")), TIMEOUT_MS)
}

private const val TIMEOUT_MS = 10_000L
