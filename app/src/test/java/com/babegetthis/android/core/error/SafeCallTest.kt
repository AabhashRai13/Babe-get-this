package com.babegetthis.android.core.error

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
}
