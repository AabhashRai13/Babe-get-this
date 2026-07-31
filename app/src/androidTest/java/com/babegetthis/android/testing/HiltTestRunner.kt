package com.babegetthis.android.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

// Swaps in HiltTestApplication for the real BabeGetThisApp, which is what lets
// @TestInstallIn modules replace production ones. Wired up via
// testInstrumentationRunner in app/build.gradle.kts.
//
// Note this also means BabeGetThisApp.onCreate never runs during instrumented
// tests — so the Supabase sessionStatus collector it installs is absent, and the
// tests are not racing a background auth refresh.
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
