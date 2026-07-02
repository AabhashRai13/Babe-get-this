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
//   - "confirm@test.com" → simulates sign-up that needs email confirmation
//   - anything else → success with a fake user

class FakeAuthRepository @Inject constructor(
    private val authStateManager: AuthStateManager,
    private val tokenManager: TokenManager,
) : AuthRepository {

    override suspend fun register(email: String, password: String, name: String): Result<RegisterResult> {
        delay(800) // Simulate network delay

        return when (email.lowercase()) {
            "error@test.com" -> Result.Error(AppError.ServerError(500, "Fake server error"))
            "taken@test.com" -> Result.Error(AppError.AuthError("Email is already registered."))
            "confirm@test.com" -> Result.Success(RegisterResult.ConfirmationRequired)
            else -> {
                val userId = UUID.randomUUID().toString()
                val fakeToken = "fake-token-${UUID.randomUUID()}"
                authStateManager.login(
                    token = fakeToken,
                    userId = userId,
                    userName = name,
                    userEmail = email,
                )

                Result.Success(
                    RegisterResult.SignedIn(User(id = userId, email = email, name = name))
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
                authStateManager.login(
                    token = fakeToken,
                    userId = userId,
                    userName = "Dev User",
                    userEmail = email,
                )

                Result.Success(
                    User(id = userId, email = email, name = "Dev User")
                )
            }
        }
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        delay(800)
        return when (email.lowercase()) {
            "error@test.com" -> Result.Error(AppError.ServerError(500, "Fake server error"))
            else -> Result.Success(Unit)
        }
    }

    // Any code works except all zeros, which simulates a bad/expired code.
    override suspend fun resetPassword(email: String, code: String, newPassword: String): Result<User> {
        delay(800)
        return when {
            code.all { it == '0' } -> Result.Error(AppError.AuthError("Invalid code. Check the email and try again."))
            else -> {
                val userId = UUID.randomUUID().toString()
                authStateManager.login(
                    token = "fake-token-${UUID.randomUUID()}",
                    userId = userId,
                    userName = "Dev User",
                    userEmail = email,
                )
                Result.Success(User(id = userId, email = email, name = "Dev User"))
            }
        }
    }

    override suspend fun logout(): Result<Unit> {
        authStateManager.logout()
        return Result.Success(Unit)
    }

    override suspend fun updateUserName(name: String): Result<User> {
        delay(500) // Simulate network delay
        // Persist locally so the UI reflects the change
        tokenManager.saveUserName(name)
        val userId = tokenManager.getUserId() ?: "fake-id"
        val email = tokenManager.getUserEmail() ?: "dev@test.com"
        return Result.Success(User(id = userId, email = email, name = name))
    }
}
