package com.babegetthis.android.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Every AppError's `message` is rendered directly to the user, so the defaults
// are product copy rather than incidental strings.
class AppErrorTest {

    @Test
    fun `defaults are user-facing copy, not exception jargon`() {
        val defaults = listOf(
            AppError.DatabaseError(),
            AppError.NetworkError(),
            AppError.ServerError(500),
            AppError.TimeoutError(),
            AppError.NotFoundError(),
            AppError.AuthError(),
            AppError.UnauthorizedError(),
            AppError.UnknownError(),
        )

        defaults.forEach {
            assertTrue("blank message on ${it::class.simpleName}", it.message.isNotBlank())
            assertTrue(
                "${it::class.simpleName} should end in a sentence: ${it.message}",
                it.message.trimEnd().endsWith("."),
            )
        }
    }

    @Test
    fun `each default reads as expected`() {
        assertEquals("Something went wrong. Please try again.", AppError.DatabaseError().message)
        assertEquals("No internet connection.", AppError.NetworkError().message)
        assertEquals("Server error. Please try later.", AppError.ServerError(500).message)
        assertEquals("Request timed out. Please try again.", AppError.TimeoutError().message)
        assertEquals("Item not found.", AppError.NotFoundError().message)
        assertEquals("Authentication failed.", AppError.AuthError().message)
        assertEquals("Session expired. Please log in again.", AppError.UnauthorizedError().message)
        assertEquals("An unexpected error occurred.", AppError.UnknownError().message)
    }

    @Test
    fun `every subtype accepts an override`() {
        assertEquals("custom", AppError.DatabaseError("custom").message)
        assertEquals("custom", AppError.NetworkError("custom").message)
        assertEquals("custom", AppError.ServerError(500, "custom").message)
        assertEquals("custom", AppError.TimeoutError("custom").message)
        assertEquals("custom", AppError.ValidationError("custom").message)
        assertEquals("custom", AppError.NotFoundError("custom").message)
        assertEquals("custom", AppError.AuthError("custom").message)
        assertEquals("custom", AppError.UnauthorizedError("custom").message)
        assertEquals("custom", AppError.UnknownError("custom").message)
    }

    // ValidationError has no default on purpose — a validation failure that
    // cannot say what was wrong is useless, so the caller must supply the copy.
    @Test
    fun `ValidationError requires its own message`() {
        assertEquals("List name can't be empty.", AppError.ValidationError("List name can't be empty.").message)
    }

    @Test
    fun `ServerError carries the status code alongside the copy`() {
        val error = AppError.ServerError(503)

        assertEquals(503, error.code)
        assertEquals("Server error. Please try later.", error.message)
    }

    // Data classes, so equality is by value — the ViewModel tests rely on this to
    // compare an emitted error against an expected one.
    @Test
    fun `errors compare by value`() {
        assertEquals(AppError.DatabaseError(), AppError.DatabaseError())
        assertEquals(AppError.ServerError(500), AppError.ServerError(500))
        assertTrue(AppError.ServerError(500) != AppError.ServerError(503))
        assertTrue(AppError.NetworkError() != AppError.NetworkError("other"))
    }

    @Test
    fun `AppErrorException carries its error and mirrors the message`() {
        val error = AppError.ValidationError("Item name can't be empty.")

        val exception = AppErrorException(error)

        assertEquals(error, exception.appError)
        assertEquals(error.message, exception.message)
    }
}
