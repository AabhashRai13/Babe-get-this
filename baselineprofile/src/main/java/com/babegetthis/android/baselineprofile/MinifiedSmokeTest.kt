package com.babegetthis.android.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The R8 verification run: launches the minified, obfuscated app and drives it
 * through UiAutomator.
 *
 * This exists because the app's own androidTest suite cannot do the job. Those
 * tests call app internals, and R8's optimizer legitimately removes members no
 * shipping code path reaches, so they fail against an optimized build for
 * reasons that are not defects. This test has no compile-time reference to
 * anything inside the app — it only reads what is on screen — which is exactly
 * what lets it run against the real shipped shape.
 *
 * It is deliberately NOT a Macrobenchmark. Macrobenchmark refuses to run on an
 * emulator, correctly, because timing numbers from one are meaningless. This
 * test measures nothing, so it runs anywhere, including CI.
 *
 * What it proves: the app starts, Hilt builds its graph, Room opens the
 * database, and the first screen composes — under full shrinking and
 * obfuscation. What it does not prove: the network, auth, voice, and realtime
 * paths, which need conditions a smoke test cannot create. Those are checked by
 * hand before a release; see design.md.
 *
 * Run with:
 *   ./gradlew :baselineprofile:connectedProdBenchmarkReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MinifiedSmokeTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun minifiedAppStartsAndRendersItsFirstScreen() {
        device.pressHome()

        val context = InstrumentationRegistry.getInstrumentation().context
        val launchIntent = requireNotNull(
            context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME),
        ) { "$PACKAGE_NAME is not installed" }
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)

        // A crash from a missing keep-rule shows up here: the process dies
        // during Application or Activity creation and nothing from the app's
        // package ever appears.
        val appAppeared = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)),
            LAUNCH_TIMEOUT_MS,
        )
        assertTrue(
            "The minified app did not render a window within ${LAUNCH_TIMEOUT_MS}ms — " +
                "check logcat for a ClassNotFoundException or NoSuchMethodError, " +
                "which means a keep-rule is missing from proguard-rules.pro.",
            appAppeared,
        )

        // Startup alone can pass while the UI layer is broken, so assert that
        // something was actually composed rather than just that a window exists.
        device.wait(Until.hasObject(By.clickable(true)), UI_TIMEOUT_MS)
        assertTrue(
            "The app window appeared but composed no interactive content.",
            device.hasObject(By.clickable(true)),
        )
    }

    private companion object {
        const val PACKAGE_NAME = "com.babegetthis.android"
        const val LAUNCH_TIMEOUT_MS = 15_000L
        const val UI_TIMEOUT_MS = 10_000L
    }
}
