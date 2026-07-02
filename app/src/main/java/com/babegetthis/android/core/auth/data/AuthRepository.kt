package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.Result


interface AuthRepository {
    suspend fun register(email: String, password: String, name: String): Result<RegisterResult>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun updateUserName(name: String): Result<User>

    // Password reset is a two-step OTP flow: request emails a 6-digit code,
    // reset verifies it and sets the new password. Verifying the code creates
    // a session, so a successful reset leaves the user signed in.
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<User>
}

// The two possible outcomes of a *successful* sign-up call:
//   - SignedIn: "Confirm email" is OFF → a session exists, the user is logged in now.
//   - ConfirmationRequired: "Confirm email" is ON → no session yet, the user must confirm first.
// Modeling this as data (instead of throwing) keeps register's branches local and readable.
sealed interface RegisterResult {
    data class SignedIn(val user: User) : RegisterResult
    data object ConfirmationRequired : RegisterResult
}
