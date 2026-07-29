package com.babegetthis.android.core.auth.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// The decision-making half of SupabaseAuthRepository's error handling, split out
// of it so it can be tested directly.
//
// The repository itself is a thin adapter over the Supabase SDK, reachable only
// through extension properties (`supabaseClient.auth`) that are painful and
// brittle to stub — but none of THAT is logic worth testing. This is: walking a
// cause chain, and mapping provider messages to copy the user sees. Keeping it
// here means the branchy part is gated and covered, rather than sitting inside a
// class the gate has to skip.

// True if any exception in the cause chain is a transport-level failure.
//
// The chain walk is the point: the connection can drop mid-request and
// Supabase/Ktor wrap the underlying network exception several causes deep, so a
// top-level type check misses it and the failure gets reported to the user as
// bad credentials.
internal fun Throwable.isNetworkFailure(): Boolean {
    var current: Throwable? = this
    // Guard against a self-referential or cyclic cause chain, which would
    // otherwise spin here forever.
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is UnknownHostException ||
            current is ConnectException ||
            current is SocketTimeoutException ||
            current is IOException
        ) return true
        current = current.cause
    }
    return false
}

// Map known Supabase error messages to friendly copy.
//
// The else branch NEVER returns the raw provider text: an unrecognised failure
// gets a generic message so internal/Supabase wording cannot leak to the user.
internal fun friendlyAuthMessage(e: Exception): String {
    val raw = e.message ?: return "Authentication failed. Please try again."
    return when {
        raw.contains("Invalid login", ignoreCase = true) ->
            "Invalid email or password."
        raw.contains("already registered", ignoreCase = true) ||
            raw.contains("already been registered", ignoreCase = true) ->
            "That email is already registered."
        raw.contains("Email not confirmed", ignoreCase = true) ->
            "Please confirm your email before signing in."
        raw.contains("expired", ignoreCase = true) ->
            "That code has expired. Request a new one."
        raw.contains("otp", ignoreCase = true) || raw.contains("token", ignoreCase = true) ->
            "Invalid code. Check the email and try again."
        else -> "Authentication failed. Please try again."
    }
}
