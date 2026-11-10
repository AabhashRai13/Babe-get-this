package com.babegetthis.android.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import com.babegetthis.android.core.pin.data.PinStore
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

// Wipes everything a previous test could have left behind.
//
// The in-memory database is a @Singleton, so it lives for the whole instrumented
// PROCESS, not per test — every journey in a class shares it. Without this, tests
// pass or fail depending on what ran before them: a test that expects the
// "Create your first list" empty state finds a leftover list and takes the FAB
// branch instead. That showed up as the same code producing 4 failures on one run
// and 5 on the next, which is the worst kind of test suite.
//
// Explicitly NOT solved by making the database non-singleton: the app's
// repositories are singletons too and would keep holding the old instance.
// Must run at order 1 — AFTER HiltAndroidRule so injection is possible, but
// BEFORE the Activity rule, so the app never composes against another test's
// leftovers. It performs the injection itself rather than relying on an @Before,
// because @Before runs after every rule, by which point the Activity is already
// on screen and the fields this needs are still uninitialised.
class ResetAppStateRule(
    private val hilt: HiltAndroidRule,
    private val database: () -> AppDatabase,
    private val authStateManager: () -> AuthStateManager,
    private val pinStore: () -> PinStore,
    // Journeys start on the lists screen unless they are about Welcome itself.
    private val welcomeDone: Boolean = true,
) : ExternalResource() {

    override fun before() {
        hilt.inject()
        database().clearAllTables()
        // clearAllTables takes the seeded categories with it, and the add-item
        // dialog expects them to exist.
        runBlocking { database().categoryDao().insertAll(DEFAULT_CATEGORIES) }
        authStateManager().logout()
        pinStore().clearAll()
        clearPrefs()
        setWelcomeDone()
    }

    // A fresh test install would otherwise open on the Welcome screen
    // (WelcomeRepository): its first-launch flag lives in its own prefs file.
    private fun setWelcomeDone() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("welcome", Context.MODE_PRIVATE)
            .edit().putBoolean("welcome_done", welcomeDone).commit()
    }

    // Plain SharedPreferences outlive the in-memory database and everything
    // else wiped above, and each of these files makes a later test start where
    // an earlier one left off — a suite that passes alone fails in company,
    // which is exactly the flakiness this rule exists to stop:
    //
    //   telemetry_markers / telemetry_consent — once-per-user and once-per-list
    //     facts; the FIRST test to add an item claims the activation marker and
    //     every later test sees "already fired".
    //   list_ownership — the adoption notice (a journey that signs in with
    //     guest lists leaves it > 0, so the next test opens behind the dialog),
    //     remembered locks that re-lock lists in later tests, the first-run
    //     flag (only the first test in the process would take the first-claim
    //     path) and the unverified claims.
    //   sync_points — high-water marks, so every test catches up from scratch.
    //   got_this — holdout draw, kill switch copy, hint and suggestion switches
    //     and the "Not now" timer; journeys that need the feature visible set
    //     them again after this rule runs.
    //
    // Cleared through the files rather than clear() methods on the stores: the
    // app has no production reason to wipe them, and adding API purely for
    // tests is how production classes grow test-shaped holes.
    private fun clearPrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("telemetry_markers", "telemetry_consent", "list_ownership", "sync_points", "got_this").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }
}
