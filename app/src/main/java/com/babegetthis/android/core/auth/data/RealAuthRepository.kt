package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.data.network.dto.LoginRequest
import com.babegetthis.android.core.data.network.dto.RegisterRequest
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import javax.inject.Inject

// Real implementation that hits the actual API via Retrofit.
// Used in staging and prod flavors.
// safeCall() wraps everything in try/catch and maps exceptions to AppError types.

class RealAuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
    private val authStateManager: AuthStateManager,
    private val tokenManager: TokenManager,
) : AuthRepository {

    override suspend fun register(email: String, password: String, name: String): Result<User> =
        safeCall {
            val response = authApiService.register(
                RegisterRequest(email = email, password = password, name = name)
            )
            // Save token + user info so the profile screen can display them later
            authStateManager.login(
                token = response.token,
                userId = response.user.id,
                userName = response.user.name,
                userEmail = response.user.email,
            )

            User(
                id = response.user.id,
                email = response.user.email,
                name = response.user.name,
            )
        }

    override suspend fun login(email: String, password: String): Result<User> =
        safeCall {
            val response = authApiService.login(
                LoginRequest(email = email, password = password)
            )
            authStateManager.login(
                token = response.token,
                userId = response.user.id,
                userName = response.user.name,
                userEmail = response.user.email,
            )

            User(
                id = response.user.id,
                email = response.user.email,
                name = response.user.name,
            )
        }

    override suspend fun logout(): Result<Unit> = safeCall {
        authStateManager.logout()
    }

    override suspend fun updateUserName(name: String): Result<User> = safeCall {
        val response = authApiService.updateUserName(
            com.babegetthis.android.core.data.network.dto.UpdateNameRequest(name)
        )
        // Persist the updated name locally
        tokenManager.saveUserName(response.name)
        User(id = response.id, email = response.email, name = response.name)
    }
}
