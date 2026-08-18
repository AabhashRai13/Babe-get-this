package com.babegetthis.android.core.telemetry.data

import com.babegetthis.android.core.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.babegetthis.android.core.error.ErrorReportingHook
import com.babegetthis.android.core.error.safeCall
import kotlinx.coroutines.test.runTest
import org.junit.After
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// The policy's whole job is to send less, so the test that matters is the one
// asserting what does NOT get reported. A regression here does not break the
// app — it floods Crashlytics until nobody reads it, which is a slower and
// more expensive failure.
class ErrorReportingPolicyTest {

    @Test
    fun `unexpected failures are reported`() {
        // UnknownError means safeCall met an exception it had no mapping for.
        assertTrue(ErrorReportingPolicy.shouldReport(AppError.UnknownError()))
        // DatabaseError means Room failed, which is never the user's fault.
        assertTrue(ErrorReportingPolicy.shouldReport(AppError.DatabaseError()))
    }

    @Test
    fun `offline-first failures are never reported`() {
        // This app is used on trains and in supermarket basements. If these
        // were reported, they would be nearly all of Crashlytics.
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.NetworkError()))
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.TimeoutError()))
    }

    @Test
    fun `user-facing conditions with user-facing recovery are not reported`() {
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.ValidationError("bad")))
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.AuthError()))
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.UnauthorizedError()))
        // A share code that matched no list is a typo, not a defect.
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.NotFoundError()))
    }

    @Test
    fun `server errors are left to the server's own monitoring`() {
        // One backend outage would otherwise arrive as thousands of client
        // reports for a problem no client change can fix.
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.ServerError(500)))
        assertFalse(ErrorReportingPolicy.shouldReport(AppError.ServerError(503)))
    }

    @Test
    fun `every AppError subtype has an explicit decision`() {
        // AppError is sealed and the policy's `when` has no `else`, so a new
        // subtype is a compile error rather than a silent default. This test
        // guards the other direction: that someone does not "fix" that
        // compile error by adding an `else` branch and dropping this file's
        // coverage of the real set.
        val decided = listOf(
            AppError.UnknownError(),
            AppError.DatabaseError(),
            AppError.NetworkError(),
            AppError.TimeoutError(),
            AppError.ValidationError("x"),
            AppError.AuthError(),
            AppError.UnauthorizedError(),
            AppError.NotFoundError(),
            AppError.ServerError(500),
        )

        assertEquals(
            "AppError gained or lost a subtype — decide about it in ErrorReportingPolicy",
            AppError::class.sealedSubclasses.size,
            decided.map { it::class }.distinct().size,
        )
    }

    @Test
    fun `the reported set stays small`() {
        // Not a style rule. Every type added here costs signal in the
        // dashboard, so widening the whitelist should be a deliberate edit
        // that trips this test and gets argued about in review.
        val all = listOf(
            AppError.UnknownError(),
            AppError.DatabaseError(),
            AppError.NetworkError(),
            AppError.TimeoutError(),
            AppError.ValidationError("x"),
            AppError.AuthError(),
            AppError.UnauthorizedError(),
            AppError.NotFoundError(),
            AppError.ServerError(500),
        )

        assertEquals(2, all.count { ErrorReportingPolicy.shouldReport(it) })
    }

    // -- The composition, end to end --
    //
    // The policy is only worth anything if it sits on the path real failures
    // take. These drive actual exceptions through safeCall with the same
    // wiring TelemetryContext.onAppStart installs, and count what survives.

    @Test
    fun `a session offline produces no non-fatals at all`() = runTest {
        val transmitted = mutableListOf<AppError>()
        ErrorReportingHook.report = { _, error ->
            if (ErrorReportingPolicy.shouldReport(error)) transmitted += error
        }

        // A user on a train: every call fails, over and over.
        repeat(20) {
            safeCall { throw UnknownHostException("no dns") }
            safeCall { throw SocketTimeoutException("timed out") }
        }

        assertTrue("offline noise reached Crashlytics: $transmitted", transmitted.isEmpty())
    }

    @Test
    fun `one unmapped exception produces exactly one non-fatal`() = runTest {
        val transmitted = mutableListOf<AppError>()
        ErrorReportingHook.report = { _, error ->
            if (ErrorReportingPolicy.shouldReport(error)) transmitted += error
        }

        safeCall { throw IllegalStateException("nobody mapped this") }

        assertEquals(1, transmitted.size)
        assertTrue(transmitted.single() is AppError.UnknownError)
    }

    @After
    fun tearDown() {
        ErrorReportingHook.reset()
    }
}
