package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject

// Fake implementation used in the dev flavor — no real network calls.
// Returns success after a short delay to simulate network latency.
//
// Special test emails trigger different scenarios:
//   - "error@test.com" → simulates a server error
//   - "taken@test.com" → simulates "email already registered"
//   - anything else → success with a fake user

class FakeAuthRepository @Inject constructor(
    private val authStateManager: AuthStateManager,
) : AuthRepository {

    override suspend fun register(email: String, password: String, name: String): Result<User> {
        delay(800) // Simulate network delay

        return when (email.lowercase()) {
            "error@test.com" -> Result.Error(AppError.ServerError(500, "Fake server error"))
            "taken@test.com" -> Result.Error(AppError.AuthError("Email is already registered."))
            else -> {
                val userId = UUID.randomUUID().toString()
                val fakeToken = "fake-token-${UUID.randomUUID()}"
                authStateManager.login(token = fakeToken, userId = userId)

                Result.Success(
                    User(id = userId, email = email, name = name)
                )
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        delay(800)

        return when (email.lowercase()) {
            "error@test.com" -> Result.Error(AppError.ServerError(500, "Fake server error"))
            "wrong@test.com" -> Result.Error(AppError.AuthError("Invalid email or password."))
            else -> {
                val userId = UUID.randomUUID().toString()
                val fakeToken = "fake-token-${UUID.randomUUID()}"
                authStateManager.login(token = fakeToken, userId = userId)

                Result.Success(
                    User(id = userId, email = email, name = "Dev User")
                )
            }
        }
    }

    override suspend fun logout(): Result<Unit> {
        authStateManager.logout()
        return Result.Success(Unit)
    }
}
