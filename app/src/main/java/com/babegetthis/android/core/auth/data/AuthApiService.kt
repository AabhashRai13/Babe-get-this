package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.data.network.dto.AuthResponse
import com.babegetthis.android.core.data.network.dto.LoginRequest
import com.babegetthis.android.core.data.network.dto.RegisterRequest
import retrofit2.http.Body
import retrofit2.http.POST

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
}
