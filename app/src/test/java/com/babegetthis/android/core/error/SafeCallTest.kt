package com.babegetthis.android.core.error

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class SafeCallTest {

    @Test
    fun `returns Success when block completes normally`() = runTest {
        val result = safeCall { 42 }
        assertEquals(Result.Success(42), result)
    }

    @Test
    fun `maps SQLiteException to DatabaseError`() = runTest {
        val result = safeCall { throw android.database.sqlite.SQLiteException("db died") }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.DatabaseError)
    }

    @Test
    fun `maps UnknownHostException to NetworkError`() = runTest {
        val result = safeCall { throw UnknownHostException("no dns") }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.NetworkError)
    }

    @Test
    fun `maps ConnectException to NetworkError with reachability message`() = runTest {
        val result = safeCall { throw ConnectException("refused") }
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.NetworkError)
        assertEquals("Cannot reach server.", error.message)
    }

    @Test
    fun `maps SSLException to NetworkError with secure-connection message`() = runTest {
        val result = safeCall { throw SSLException("bad cert") }
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.NetworkError)
        assertEquals("Secure connection failed.", error.message)
    }

    @Test
    fun `maps SocketTimeoutException to TimeoutError`() = runTest {
        val result = safeCall { throw SocketTimeoutException("timeout") }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.TimeoutError)
    }

    @Test
    fun `default 401 mapping returns UnauthorizedError`() = runTest {
        val result = safeCall { throw httpException(401) }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.UnauthorizedError)
    }

    @Test
    fun `custom onUnauthorized overrides 401 mapping`() = runTest {
        val custom = AppError.AuthError("Invalid email or password.")
        val result = safeCall(onUnauthorized = { custom }) {
            throw httpException(401)
        }
        assertEquals(Result.Error(custom), result)
    }

    @Test
    fun `maps 4xx HTTP error to AuthError`() = runTest {
        val result = safeCall { throw httpException(404) }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.AuthError)
    }

    @Test
    fun `maps 5xx HTTP error to ServerError carrying the code`() = runTest {
        val result = safeCall { throw httpException(503) }
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.ServerError)
        assertEquals(503, (error as AppError.ServerError).code)
    }

    @Test
    fun `maps unrecognized exception to UnknownError`() = runTest {
        val result = safeCall { throw IllegalStateException("boom") }
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.UnknownError)
    }

    // Helper — builds a Retrofit HttpException with the given status code.
    // Retrofit's HttpException needs a Response<*> wrapping an error body.
    private fun httpException(code: Int): HttpException {
        val body = "".toResponseBody(null)
        return HttpException(Response.error<Any>(code, body))
    }

    // Every other test here passes a block that completes synchronously, so the
    // resume half of safeCall's suspend state machine was never entered — which
    // is exactly what the last uncovered line was. A block that genuinely
    // suspends is the real-world case anyway: every production caller awaits a
    // DAO or a network call.
    @Test
    fun `a block that genuinely suspends still succeeds`() = runTest {
        val result = safeCall {
            delay(10)
            42
        }

        assertEquals(Result.Success(42), result)
    }

    @Test
    fun `a block that throws after suspending is still mapped`() = runTest {
        val result = safeCall {
            delay(10)
            throw UnknownHostException("no dns")
        }

        assertTrue((result as Result.Error).error is AppError.NetworkError)
    }

    @Test
    fun `both overrides supplied explicitly`() = runTest {
        val result = safeCall(
            onUnauthorized = { AppError.AuthError("explicit unauthorized") },
            onClientError = { AppError.ValidationError("explicit client $it") },
        ) { 7 }

        assertEquals(Result.Success(7), result)
    }

    @Test
    fun `explicit onClientError is used for a 4xx`() = runTest {
        val result = safeCall(
            onUnauthorized = { AppError.AuthError("u") },
            onClientError = { AppError.ValidationError("client $it") },
        ) { throw httpException(422) }

        assertEquals("client 422", (result as Result.Error).error.message)
    }

    // The `else` arm of the HttpException mapping: a status outside 400..599.
    //
    // Only reachable from ABOVE 599, not below 400 — Retrofit's Response.error()
    // rejects any code under 400, so a 3xx HttpException cannot be constructed at
    // all and the sub-400 half of this branch is dead by construction. Worth
    // knowing before anyone "simplifies" the else away.
    //
    // Task 12.1 listed this case and it was the last genuinely uncovered line in
    // the gated surface. I wrote it off as a Kover attribution quirk twice before
    // actually reading the per-line counters, which said mi=14 ci=0 — as
    // unambiguous as it gets.
    @Test
    fun `a status above the server range still maps to ServerError`() = runTest {
        val result = safeCall { throw httpException(600) }

        val error = (result as Result.Error).error
        assertTrue(error is AppError.ServerError)
        assertEquals(600, (error as AppError.ServerError).code)
    }

    // Unlike the generic `else` at the bottom of the mapping — which was changed
    // in task 10.2 to stop leaking exception text — this arm DOES pass the message
    // through. Retrofit's text is a status line ("HTTP 600 ..."), not internal
    // detail, so it is safe. Pinned so the difference stays deliberate.
    @Test
    fun `the out-of-range arm carries the http status line`() = runTest {
        val result = safeCall { throw httpException(600) }

        assertTrue((result as Result.Error).error.message.contains("600"))
    }
}
