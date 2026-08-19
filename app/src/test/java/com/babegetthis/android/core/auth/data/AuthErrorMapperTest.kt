package com.babegetthis.android.core.auth.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AuthErrorMapperTest {

    // --- isNetworkFailure ---

    @Test
    fun `a top-level transport exception is a network failure`() {
        assertTrue(UnknownHostException().isNetworkFailure())
        assertTrue(ConnectException().isNetworkFailure())
        assertTrue(SocketTimeoutException().isNetworkFailure())
        assertTrue(IOException().isNetworkFailure())
    }

    @Test
    fun `an ordinary exception is not a network failure`() {
        assertFalse(IllegalStateException("nope").isNetworkFailure())
    }

    // The whole reason this walks the chain: Supabase/Ktor wrap the real
    // transport error several causes deep, so a top-level type check reports a
    // dropped connection to the user as bad credentials.
    @Test
    fun `a transport exception buried in the cause chain still counts`() {
        val buried = RuntimeException("wrapper", RuntimeException("inner", UnknownHostException()))

        assertTrue(buried.isNetworkFailure())
    }

    @Test
    fun `a deep chain with no transport cause does not count`() {
        val deep = RuntimeException("a", RuntimeException("b", IllegalArgumentException("c")))

        assertFalse(deep.isNetworkFailure())
    }

    // A self-referential cause would spin forever without the seen-set guard.
    @Test
    fun `a cyclic cause chain terminates`() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)

        assertFalse(a.isNetworkFailure())
    }

    // --- friendlyAuthMessage ---

    @Test
    fun `bad credentials get their own message`() {
        assertEquals(
            "Invalid email or password.",
            friendlyAuthMessage(Exception("Invalid login credentials")),
        )
    }

    @Test
    fun `a taken email is called out`() {
        assertEquals(
            "That email is already registered.",
            friendlyAuthMessage(Exception("User already registered")),
        )
        assertEquals(
            "That email is already registered.",
            friendlyAuthMessage(Exception("Email has already been registered")),
        )
    }

    @Test
    fun `an unconfirmed email is explained`() {
        assertEquals(
            "Please confirm your email before signing in.",
            friendlyAuthMessage(Exception("Email not confirmed")),
        )
    }

    @Test
    fun `an expired code is explained`() {
        assertEquals(
            "That code has expired. Request a new one.",
            friendlyAuthMessage(Exception("Token has expired")),
        )
    }

    @Test
    fun `an otp or token problem is explained`() {
        assertEquals(
            "Invalid code. Check the email and try again.",
            friendlyAuthMessage(Exception("Invalid otp")),
        )
        assertEquals(
            "Invalid code. Check the email and try again.",
            friendlyAuthMessage(Exception("Bad token supplied")),
        )
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(
            "Invalid email or password.",
            friendlyAuthMessage(Exception("INVALID LOGIN CREDENTIALS")),
        )
    }

    // "expired" is checked before "token", so an expired token reads as expired
    // rather than as an invalid code. Pinned because the ordering is load-bearing
    // and both branches match that string.
    @Test
    fun `expired wins over token when a message contains both`() {
        assertEquals(
            "That code has expired. Request a new one.",
            friendlyAuthMessage(Exception("token expired")),
        )
    }

    // The point of the else branch: never surface raw provider wording.
    @Test
    fun `an unrecognised message is replaced, not passed through`() {
        val raw = "PGRST301: JWT sub claim missing from role mapping"

        val friendly = friendlyAuthMessage(Exception(raw))

        assertEquals("Authentication failed. Please try again.", friendly)
        assertFalse(friendly.contains("PGRST301"))
    }

    @Test
    fun `an exception with no message still yields copy`() {
        assertEquals(
            "Authentication failed. Please try again.",
            friendlyAuthMessage(Exception()),
        )
    }
}
