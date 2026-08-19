package com.babegetthis.android.core.data.network

import com.babegetthis.android.core.auth.data.AuthStateManager
import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.testing.inMemoryTokenManager
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// Real OkHttp against a MockWebServer, so the interceptor and authenticator are
// exercised through the actual call pipeline rather than with a hand-rolled fake
// Chain.
class AuthNetworkTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url() = server.url("/thing").toString()

    // --- AuthInterceptor ---

    @Test
    fun `a saved token is attached as a bearer header`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(url()).build()).execute().close()

        assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `no token means no header rather than an empty one`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(inMemoryTokenManager()))
            .build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(url()).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `the request is otherwise untouched`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder().url(url()).header("X-Custom", "kept").build()
        ).execute().close()

        val recorded = server.takeRequest()
        assertEquals("kept", recorded.getHeader("X-Custom"))
        assertEquals("/thing", recorded.path)
    }

    // header() replaces rather than appends, so a caller-supplied Authorization
    // is overwritten by the saved token. Pinned as current behavior — worth
    // knowing before anyone adds a call that signs itself.
    @Test
    fun `a caller-supplied Authorization header is overwritten`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder().url(url()).header("Authorization", "Basic other").build()
        ).execute().close()

        assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"))
    }

    // --- AuthAuthenticator ---

    // OkHttp consults the authenticator on a 401. Returning null tells it to stop
    // rather than retry, and the session is cleared so navigation redirects.
    @Test
    fun `a 401 on an authenticated request logs the user out`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        tokens.saveUserId("u1")
        val authStateManager = AuthStateManager(tokens).apply { initialize() }
        assertEquals(AuthState.Authenticated("u1"), authStateManager.authState.value)

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(AuthAuthenticator(authStateManager))
            .build()
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(Request.Builder().url(url()).build()).execute().close()

        assertEquals(AuthState.Unauthenticated, authStateManager.authState.value)
    }

    // Only one attempt: returning null means OkHttp does not loop.
    @Test
    fun `a 401 is not retried`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(AuthAuthenticator(AuthStateManager(tokens)))
            .build()
        server.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(Request.Builder().url(url()).build()).execute()
        response.close()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    // With no token the interceptor attaches no header, so the authenticator has
    // nothing to work with and must not clear a session it never authenticated.
    @Test
    fun `a 401 on an unauthenticated request does not touch auth state`() {
        val authStateManager = mockk<AuthStateManager>(relaxed = true)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(inMemoryTokenManager()))
            .authenticator(AuthAuthenticator(authStateManager))
            .build()
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(Request.Builder().url(url()).build()).execute().close()

        verify(exactly = 0) { authStateManager.logout() }
    }

    @Test
    fun `a successful response never consults the authenticator`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        val authStateManager = mockk<AuthStateManager>(relaxed = true)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(AuthAuthenticator(authStateManager))
            .build()
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(url()).build()).execute().close()

        verify(exactly = 0) { authStateManager.logout() }
    }

    // A 500 is a server problem, not an auth problem — the session must survive.
    @Test
    fun `a 500 does not log the user out`() {
        val tokens = inMemoryTokenManager()
        tokens.saveToken("abc123")
        val authStateManager = mockk<AuthStateManager>(relaxed = true)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(AuthAuthenticator(authStateManager))
            .build()
        server.enqueue(MockResponse().setResponseCode(500))

        client.newCall(Request.Builder().url(url()).build()).execute().close()

        verify(exactly = 0) { authStateManager.logout() }
    }
}
