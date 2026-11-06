package com.babegetthis.android.core.error

// Where SafeCall offers handled errors to crash reporting.
//
// Kept in core/error rather than core/telemetry so the data layer's one
// dependency on reporting is this interface, not the crash vendor. Telemetry
// binds it (see TelemetryModule); unit tests pass a lambda and assert on what
// it received.
fun interface ErrorReporter {
    fun report(throwable: Throwable, error: AppError)
}
