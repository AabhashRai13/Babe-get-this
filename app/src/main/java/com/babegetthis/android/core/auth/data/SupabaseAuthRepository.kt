package com.babegetthis.android.core.auth.data

import com.babegetthis.android.core.auth.model.User
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.network.NetworkMonitor
import java.io.IOException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

class SupabaseAuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authStateManager: AuthStateManager,
    private val networkMonitor: NetworkMonitor,
) : AuthRepository {

    override suspend fun register(email: String, password: String, name: String): Result<RegisterResult> =
        runCatchingAuth {
            // We pass the display name as user_metadata so it lives on the
            // Supabase user record, not just locally.
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = buildJsonObject { put("name", name) }
            }
            // With "Confirm email" OFF, sign-up creates a session immediately → SignedIn.
            // With it ON, there is no session yet → the user must confirm first.
            if (supabaseClient.auth.currentSessionOrNull() == null) {
                RegisterResult.ConfirmationRequired
            } else {
                RegisterResult.SignedIn(
                    persistCurrentSession(fallbackName = name, fallbackEmail = email)
                )
            }
        }

    override suspend fun login(email: String, password: String): Result<User> =
        runCatchingAuth {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            persistCurrentSession(
                fallbackName = email.substringBefore("@"),
                fallbackEmail = email,
            )
        }

    override suspend fun logout(): Result<Unit> = runCatchingAuth {
        // Sign out of Supabase, but always clear local state even if the network
        // call fails (e.g. the user is offline) so they're never stuck logged in.
        try {
            supabaseClient.auth.signOut()
        } catch (_: Exception) {
            // ignored on purpose — local logout below is what matters to the UI
        }
        authStateManager.logout()
    }

    override suspend fun updateUserName(name: String): Result<User> = runCatchingAuth {
        val updated = supabaseClient.auth.updateUser {
            data = buildJsonObject { put("name", name) }
        }
        // Keep the locally cached name in sync for the profile screen
        authStateManager.updateName(name)
        updated.toUser(fallbackName = name, fallbackEmail = authStateManager.currentEmail() ?: "")
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> =
        runCatchingAuth {
            // Supabase emails a 6-digit OTP ({{ .Token }} in the email template).
            // Succeeds even for unknown emails, so we can't leak who's registered.
            supabaseClient.auth.resetPasswordForEmail(email)
        }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): Result<User> =
        runCatchingAuth {
            // Verifying the recovery OTP signs the user in, so updateUser has a
            // session to work with and the user lands in the app authenticated.
            supabaseClient.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = email,
                token = code,
            )
            supabaseClient.auth.updateUser {
                password = newPassword
            }
            persistCurrentSession(
                fallbackName = email.substringBefore("@"),
                fallbackEmail = email,
            )
        }

    // -- Helpers --

    // Reads the current Supabase session, copies it into our own storage via
    // AuthStateManager (which flips AuthState to Authenticated), and returns the
    // domain User. Throws if there is somehow no active session.
    private fun persistCurrentSession(fallbackName: String, fallbackEmail: String): User {
        val session = supabaseClient.auth.currentSessionOrNull()
            ?: error("Authentication succeeded but no session was found.")
        val userInfo = session.user
            ?: supabaseClient.auth.currentUserOrNull()
            ?: error("Authentication succeeded but no user was found.")

        val resolvedName = userInfo.readName() ?: fallbackName
        val resolvedEmail = userInfo.email ?: fallbackEmail

        authStateManager.login(
            token = session.accessToken,
            userId = userInfo.id,
            userName = resolvedName,
            userEmail = resolvedEmail,
        )
        return User(id = userInfo.id, email = resolvedEmail, name = resolvedName)
    }

    // The display name is stored in Supabase user_metadata as a JSON field.
    private fun UserInfo.readName(): String? =
        userMetadata?.get("name")?.jsonPrimitive?.contentOrNull

    private fun UserInfo.toUser(fallbackName: String, fallbackEmail: String): User =
        User(
            id = id,
            email = email ?: fallbackEmail,
            name = readName() ?: fallbackName,
        )

    // One place to turn auth failures into our AppError types. Supabase throws
    // its own exceptions (not Retrofit's), so we map by message here rather than
    // reusing safeCall()'s Retrofit-specific HttpException handling.
    private suspend fun <T> runCatchingAuth(block: suspend () -> T): Result<T> {
        // Proactive guard: if we're offline, fail fast with a clean message
        // instead of letting Supabase throw a raw transport error (which is how
        // the offline login leaked provider text before).
        if (!networkMonitor.isOnline()) {
            return Result.Error(AppError.NetworkError())
        }
        return try {
            Result.Success(block())
        } catch (e: SocketTimeoutException) {
            Result.Error(AppError.TimeoutError())
        } catch (e: Exception) {
            // Defensive layer: the connection can drop mid-request, and
            // Supabase/Ktor wrap the underlying network exception several causes
            // deep — a top-level type check misses it. Walk the cause chain
            // before deciding this is an auth (credentials) error.
            if (e.isNetworkFailure()) {
                Result.Error(AppError.NetworkError())
            } else {
                Result.Error(AppError.AuthError(friendlyMessage(e)))
            }
        }
    }

    // True if any exception in the cause chain is a transport-level failure.
    private fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is UnknownHostException ||
                current is ConnectException ||
                current is SocketTimeoutException ||
                current is IOException
            ) return true
            current = current.cause
        }
        return false
    }

    // Map known Supabase error messages to friendly copy. The else branch NEVER
    // returns the raw provider text — an unrecognized failure gets a generic
    // message so we don't leak internal/Supabase wording to the user.
    private fun friendlyMessage(e: Exception): String {
        val raw = e.message ?: return "Authentication failed. Please try again."
        return when {
            raw.contains("Invalid login", ignoreCase = true) ->
                "Invalid email or password."
            raw.contains("already registered", ignoreCase = true) ||
                raw.contains("already been registered", ignoreCase = true) ->
                "That email is already registered."
            raw.contains("Email not confirmed", ignoreCase = true) ->
                "Please confirm your email before signing in."
            raw.contains("expired", ignoreCase = true) ->
                "That code has expired. Request a new one."
            raw.contains("otp", ignoreCase = true) || raw.contains("token", ignoreCase = true) ->
                "Invalid code. Check the email and try again."
            else -> "Authentication failed. Please try again."
        }
    }
}
