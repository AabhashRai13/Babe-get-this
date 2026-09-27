package com.babegetthis.android.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Measures cold start with and without the baseline profile, so the profile's
 * value is a number rather than an article someone read.
 *
 * These two tests are the same journey compiled two ways: [startupNoProfile]
 * with no ahead-of-time compilation at all, [startupWithProfile] with the
 * profile applied. The difference between their medians is what the profile
 * actually buys on this device.
 *
 * Run with:
 *   ./gradlew :baselineprofile:connectedProdBenchmarkReleaseAndroidTest
 *
 * Numbers are device-specific. Comparing a run on one phone against a run on
 * another says nothing; compare the two modes from the same run.
 */
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoProfile() = measure(CompilationMode.None())

    @Test
    fun startupWithProfile() = measure(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT_MS)
    }

    private companion object {
        const val PACKAGE_NAME = "com.babegetthis.android"
        const val LAUNCH_TIMEOUT_MS = 10_000L

        // Enough iterations for the median to be stable without making the run
        // long enough that people stop running it.
        const val ITERATIONS = 10
    }
}
