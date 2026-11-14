package com.dantech.splitvibe.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code runs while the app starts and while a split begins. The release ships that
 * list, and Android compiles those paths ahead of time on install, so the first launch is faster.
 *
 *     ./gradlew :app:generateProdReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndFirstStepsOfASplit() = rule.collect(
        packageName = targetAppId,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        startASplit()
    }
}
