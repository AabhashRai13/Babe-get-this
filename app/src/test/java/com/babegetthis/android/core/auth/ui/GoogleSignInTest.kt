package com.babegetthis.android.core.auth.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInTest {

    // Supabase hashes the raw nonce as lowercase SHA-256 hex and compares it
    // with the nonce Google signed into the token. Any encoding slip (missing
    // zero-padding, uppercase, base64) fails every sign-in with a nonce
    // mismatch. "abc" is the FIPS 180-2 vector; its digest has bytes < 0x10.
    @Test
    fun `sha256Hex matches the standard test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }
}
