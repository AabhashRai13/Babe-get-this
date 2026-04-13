package com.babegetthis.android.core.error

// sealed class = a closed set of subtypes. The compiler knows ALL possible cases.
// Like Dart's sealed class or Freezed @freezed union.
// When you use `when` on this, the compiler forces you to handle every case.

// This is the "abstract Failure class" you'd create in Flutter with dartz.
// Every error in the app is one of these types.

sealed class AppError(val message: String) {

    // -- Database errors --
    // Something went wrong reading or writing to Room
    data class DatabaseError(val details: String = "Something went wrong. Please try again.") :
        AppError(details)

    // -- Network errors (for future sync) --
    data class NetworkError(val details: String = "No internet connection.") :
        AppError(details)

    data class ServerError(val code: Int, val details: String = "Server error. Please try later.") :
        AppError(details)

    data class TimeoutError(val details: String = "Request timed out. Please try again.") :
        AppError(details)

    // -- Validation errors --
    // These shouldn't normally reach the repository (UI should catch them),
    // but they're here as a safety net
    data class ValidationError(val details: String) :
        AppError(details)

    // -- Not found --
    data class NotFoundError(val details: String = "Item not found.") :
        AppError(details)

    // -- Auth errors --
    // Login/register failed (wrong credentials, email taken, etc.)
    data class AuthError(val details: String = "Authentication failed.") :
        AppError(details)

    // Server returned 401 — token expired or invalid
    data class UnauthorizedError(val details: String = "Session expired. Please log in again.") :
        AppError(details)

    // -- Unknown / unexpected --
    data class UnknownError(val details: String = "An unexpected error occurred.") :
        AppError(details)
}
