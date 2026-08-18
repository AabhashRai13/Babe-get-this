package com.babegetthis.android.core.error

// Where handled errors get offered to crash reporting.
//
// safeCall is the single funnel every repository failure in the app passes
// through, and it holds both the original Throwable and the AppError it mapped
// to. That makes it the one place worth hooking: reporting from ViewModels
// instead would mean a call site per screen and would still miss any repository
// whose Result is consumed somewhere else.
//
// ponytail: deliberate service locator. safeCall is a top-level function, so it
// cannot take an injected CrashReporter without adding a parameter to every one
// of its callers — which is most of the data layer. Bounded to this one
// property, @Volatile, and null until BabeGetThisApp assigns it. Upgrade path:
// if safeCall ever becomes a class (an ErrorMapper with an @Inject
// constructor), this goes away and the dependency becomes ordinary Hilt wiring.
//
// Null by default matters for tests: unit and Robolectric runs report nothing
// without any setup, and a test that cares assigns a lambda and asserts on it.
object ErrorReportingHook {

    // Assigned once on the main thread at startup, read from whichever thread a
    // repository happens to fail on. @Volatile is what makes that safe.
    @Volatile
    var report: ((Throwable, AppError) -> Unit)? = null

    // Tests set `report` directly; this is the paired teardown so one test's
    // hook cannot leak into the next.
    fun reset() {
        report = null
    }
}
