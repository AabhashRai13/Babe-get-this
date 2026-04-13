package com.babegetthis.android.core.auth.model

// Domain model for a user. This is what the rest of the app sees.
// Network DTOs and database entities map to this.

data class User(
    val id: String,
    val email: String,
    val name: String,
)
