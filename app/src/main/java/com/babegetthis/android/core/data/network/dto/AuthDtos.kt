package com.babegetthis.android.core.data.network.dto

import kotlinx.serialization.Serializable

// Network DTOs — these match the JSON the API sends/receives.
// Like Dart's fromJson/toJson classes from json_serializable.
// @Serializable generates the parser at compile time (no reflection).

// -- Requests --

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
)

// -- Responses --

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String,
)
