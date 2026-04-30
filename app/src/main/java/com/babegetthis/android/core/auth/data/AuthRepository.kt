package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.Result

// Interface for authentication operations.
// This is the key to environment switching:
//   - In dev flavor → FakeAuthRepository is injected (no network calls)
//   - In staging/prod → RealAuthRepository is injected (real API calls)
//
// ViewModels only see this interface — they never know if data is real or fake.
// Like defining an abstract repository in Flutter and swapping implementations with GetIt.

interface AuthRepository {
    suspend fun register(email: String, password: String, name: String): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun updateUserName(name: String): Result<User>
}
