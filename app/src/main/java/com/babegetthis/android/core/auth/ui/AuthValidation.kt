package com.babegetthis.android.core.auth.ui

// Shared email check used by both the Login and Register validation.
// We use a small regex instead of android.util.Patterns.EMAIL_ADDRESS so it
// works in plain JUnit tests — Patterns is part of the Android framework and
// is null when running on the JVM without Robolectric.
//
// The pattern is intentionally simple: "something @ something . something",
// with no spaces. It catches the common mistakes (missing @, missing domain)
// without trying to fully implement the email RFC, which is famously huge.
private val EMAIL_REGEX = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)
