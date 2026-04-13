package com.babegetthis.android.core.data.network

import com.babegetthis.android.core.auth.data.AuthStateManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

// OkHttp Authenticator — called automatically when the server returns 401 Unauthorized.
// This is different from an Interceptor: interceptors run on EVERY request,
// authenticators only run when authentication fails.
//
// For now: on 401 we just log the user out (clear token, redirect to login).
// Later when the backend supports refresh tokens, this is where we'd attempt a token refresh
// before giving up.

class AuthAuthenticator @Inject constructor(
    private val authStateManager: AuthStateManager,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // If we already tried re-authenticating and still got 401, give up
        // (returning null tells OkHttp to stop retrying)
        if (response.request.header("Authorization") != null) {
            authStateManager.logout()
            return null
        }

        // No token was on the request — nothing we can do
        return null
    }
}
