package com.dantech.splitvibe.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long frames take while a split begins: opening the screen, loading the photo, going back.
 * Run it on a real phone, as with [StartupBenchmark].
 */
@RunWith(AndroidJUnit4::class)
class SplitFlowBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun firstStepsOfASplit() = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        startASplit()
    }
}
