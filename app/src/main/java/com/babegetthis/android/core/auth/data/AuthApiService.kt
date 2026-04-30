package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.data.network.dto.AuthResponse
import com.babegetthis.android.core.data.network.dto.LoginRequest
import com.babegetthis.android.core.data.network.dto.RegisterRequest
import com.babegetthis.android.core.data.network.dto.UpdateNameRequest
import com.babegetthis.android.core.data.network.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

// Retrofit interface — defines the HTTP endpoints for authentication.
// Retrofit generates the implementation at compile/runtime from this interface.
//
// Like defining API methods in a Dio service class in Flutter,
// but Retrofit does the serialization/deserialization automatically.
// Each suspend function = one HTTP call that can be awaited.

interface AuthApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    // Update the user's display name.
    // Backend endpoint may not exist yet — safeCall will catch the HTTP error gracefully.
    @PUT("user/name")
    suspend fun updateUserName(@Body request: UpdateNameRequest): UserDto
}
