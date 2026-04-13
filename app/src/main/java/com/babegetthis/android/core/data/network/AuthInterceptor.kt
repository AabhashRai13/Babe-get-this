package com.babegetthis.android.core.data.network

import com.babegetthis.android.core.auth.data.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// OkHttp Interceptor that attaches the Bearer token to every outgoing request.
// Like Dio's interceptor in Flutter: dio.interceptors.add(AuthInterceptor()).
// If no token is saved (user not logged in), the request goes through without a header.

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getToken()

        // If we have a token, attach it as a Bearer header
        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
