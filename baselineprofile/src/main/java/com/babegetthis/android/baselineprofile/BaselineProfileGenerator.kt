package com.babegetthis.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Generates the baseline profile that ships with the release build, and doubles
 * as the verification that the minified app actually works.
 *
 * It drives the app entirely through UiAutomator, from a separate process, with
 * no compile-time reference to anything inside the app. That is deliberate and
 * load-bearing: the instrumented androidTest suite cannot run against a minified
 * build, because it calls app internals that R8's optimizer legitimately removes.
 * This journey only ever looks at what is on screen, so it works against a fully
 * optimized APK — which makes it the one automated thing that proves the
 * keep-rules are complete enough for the app to start and be used.
 *
 * Regenerate when the startup path changes materially — a new first screen, a
 * new blocking initialisation, a change to what the list screen renders. A stale
 * profile is not harmful, only less effective.
 *
 * Run with:
 *   ./gradlew :app:generateProdBenchmarkBaselineProfile
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowseLists() = rule.collect(
        packageName = PACKAGE_NAME,
        // Compile-aware filtering off: this app's startup path runs through
        // Compose and Hilt, and the default filter drops most of it.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Wait for the first real frame rather than a fixed sleep — on a cold
        // start behind a splash screen, a sleep either flakes or wastes time.
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), LAUNCH_TIMEOUT_MS)

        // Scroll the list catalog. Even with no lists present this exercises the
        // lazy list's composition and layout path, which is what the profile is
        // for. With lists present it also covers item composition.
        device.findObject(By.scrollable(true))?.let { scrollable ->
            scrollable.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(SCROLL_PASSES) {
                scrollable.fling(androidx.test.uiautomator.Direction.DOWN)
                device.waitForIdle()
            }
            scrollable.fling(androidx.test.uiautomator.Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.babegetthis.android"
        const val LAUNCH_TIMEOUT_MS = 10_000L

        // Keeps the fling gesture off the screen edges, where it would trigger
        // the system back and home gestures instead of scrolling the list.
        const val GESTURE_MARGIN_DIVISOR = 5
        const val SCROLL_PASSES = 2
    }
}
