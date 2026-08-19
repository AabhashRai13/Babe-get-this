package com.babegetthis.android.core.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// safeCall is the funnel every repository failure passes through, so this hook
// is the one place that decides whether a handled error ever leaves the device.
// These tests are about restraint: the interesting assertions are the ones
// counting reports that must NOT happen.
class ErrorReportingHookTest {

    private val reported = mutableListOf<Pair<Throwable, AppError>>()

    @After
    fun tearDown() {
        // A hook left set here would leak into every other test in the module.
        ErrorReportingHook.reset()
    }

    private fun captureReports() {
        ErrorReportingHook.report = { throwable, error -> reported += throwable to error }
    }

    @Test
    fun `an unrecognised exception is offered to the reporter`() = runTest {
        captureReports()
        val boom = IllegalStateException("something nobody mapped")

        safeCall { throw boom }

        assertEquals(1, reported.size)
        assertEquals(boom, reported.single().first)
        assertTrue(reported.single().second is AppError.UnknownError)
    }

    @Test
    fun `the original throwable is offered, not the user-facing message`() = runTest {
        // AppError.message is deliberately scrubbed copy. The stack trace is
        // the part worth having in a report, so both are handed over and the
        // reporter picks.
        captureReports()
        val boom = IOException("/data/user/0/cache/voice.m4a (No such file)")

        safeCall { throw boom }

        assertEquals(boom, reported.single().first)
    }

    @Test
    fun `offline failures still reach the hook, where the policy drops them`() = runTest {
        // The hook does not filter — ErrorReportingPolicy does, behind
        // CrashReporter.recordNonFatal. Keeping the split means the policy is
        // one testable object rather than a condition buried in safeCall.
        captureReports()

        safeCall { throw UnknownHostException("no dns") }
        safeCall { throw SocketTimeoutException("timed out") }

        assertTrue(reported.all { it.second is AppError.NetworkError || it.second is AppError.TimeoutError })
    }

    @Test
    fun `a cancelled coroutine is never reported`() = runTest {
        // Dismissing the voice sheet cancels the in-flight transcribe job.
        // That is a user gesture. safeCall rethrows CancellationException
        // before the mapping branch, so the hook must never see it.
        captureReports()

        runCatching { safeCall { throw CancellationException("sheet dismissed") } }

        assertTrue("a cancellation was reported", reported.isEmpty())
    }

    @Test
    fun `a successful call reports nothing`() = runTest {
        captureReports()

        safeCall { "fine" }

        assertTrue(reported.isEmpty())
    }

    @Test
    fun `an unset hook is not an error`() = runTest {
        // The default. Unit and Robolectric runs must not need any setup, and
        // a release build that somehow never reached onAppStart must still
        // return Results rather than crash inside its own error handling.
        ErrorReportingHook.reset()

        val result = safeCall { throw IllegalStateException("boom") }

        assertTrue(result is Result.Error)
    }

    @Test
    fun `a reporter that throws cannot turn a handled error into a crash`() = runTest {
        // Telemetry that can break the app it is watching is worse than
        // telemetry that misses a report.
        ErrorReportingHook.report = { _, _ -> throw IllegalStateException("reporter down") }

        val result = safeCall { throw IllegalStateException("original") }

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.UnknownError)
    }

    @Test
    fun `a deliberately raised AppError reaches the hook with its own type`() = runTest {
        // AppErrorException is how repositories raise a specific AppError.
        // The policy needs the real type, not UnknownError.
        captureReports()

        safeCall { throw AppErrorException(AppError.DatabaseError()) }

        assertTrue(reported.single().second is AppError.DatabaseError)
    }
}
