package com.babegetthis.android.core.pin.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.util.Base64 — the PBKDF2 work is plain JVM crypto
// and needs no shim.
@RunWith(RobolectricTestRunner::class)
class PinCryptoTest {

    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun `hashing is deterministic for a given secret and salt`() {
        assertArrayEquals(PinCrypto.hash("1234", salt), PinCrypto.hash("1234", salt))
    }

    @Test
    fun `the same secret under a different salt hashes differently`() {
        val other = ByteArray(16) { (it + 1).toByte() }

        assertFalse(PinCrypto.hash("1234", salt).contentEquals(PinCrypto.hash("1234", other)))
    }

    @Test
    fun `different secrets under the same salt hash differently`() {
        assertFalse(PinCrypto.hash("1234", salt).contentEquals(PinCrypto.hash("1235", salt)))
    }

    @Test
    fun `the derived key is 256 bits`() {
        assertEquals(32, PinCrypto.hash("1234", salt).size)
    }

    @Test
    fun `matches accepts the right secret`() {
        assertTrue(PinCrypto.matches("1234", salt, PinCrypto.hash("1234", salt)))
    }

    @Test
    fun `matches rejects a wrong secret`() {
        assertFalse(PinCrypto.matches("9999", salt, PinCrypto.hash("1234", salt)))
    }

    // A near-miss must fail exactly like a total miss — MessageDigest.isEqual
    // does not early-exit, so no timing signal leaks about how much matched.
    @Test
    fun `matches rejects a secret differing in one digit`() {
        assertFalse(PinCrypto.matches("1235", salt, PinCrypto.hash("1234", salt)))
    }

    @Test
    fun `matches rejects the right secret under the wrong salt`() {
        val other = ByteArray(16) { (it + 7).toByte() }

        assertFalse(PinCrypto.matches("1234", other, PinCrypto.hash("1234", salt)))
    }

    @Test
    fun `salts are 16 bytes`() {
        assertEquals(16, PinCrypto.newSalt().size)
    }

    @Test
    fun `salts are not reused`() {
        assertFalse(PinCrypto.newSalt().contentEquals(PinCrypto.newSalt()))
    }

    @Test
    fun `encode and decode round-trip arbitrary bytes`() {
        val bytes = ByteArray(32) { (it * 7).toByte() }

        assertArrayEquals(bytes, PinCrypto.decode(PinCrypto.encode(bytes)))
    }

    @Test
    fun `encoding produces no line breaks`() {
        // NO_WRAP matters — a newline in a prefs value would corrupt the read.
        assertFalse(PinCrypto.encode(ByteArray(64) { it.toByte() }).contains("\n"))
    }

    // --- recovery codes ---

    private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    @Test
    fun `a recovery code is ten characters`() {
        assertEquals(10, PinCrypto.newRecoveryCode().length)
    }

    @Test
    fun `a recovery code uses only the look-alike-free alphabet`() {
        repeat(50) {
            val code = PinCrypto.newRecoveryCode()
            assertTrue("unexpected char in $code", code.all { it in alphabet })
        }
    }

    // I, L, O and U are excluded so a written-down code can't be misread.
    @Test
    fun `the alphabet omits the look-alike letters`() {
        repeat(50) {
            val code = PinCrypto.newRecoveryCode()
            assertTrue(code.none { it in "ILOU" })
        }
    }

    @Test
    fun `recovery codes are not repeated`() {
        val codes = List(50) { PinCrypto.newRecoveryCode() }

        assertEquals(50, codes.toSet().size)
    }

    // --- normalizeCode ---

    @Test
    fun `normalize uppercases`() {
        assertEquals("ABC123", PinCrypto.normalizeCode("abc123"))
    }

    @Test
    fun `normalize strips spaces and dashes`() {
        assertEquals("ABC123", PinCrypto.normalizeCode("ABC-123"))
        assertEquals("ABC123", PinCrypto.normalizeCode(" A B C 1 2 3 "))
    }

    @Test
    fun `normalize leaves a clean code untouched`() {
        val code = PinCrypto.newRecoveryCode()

        assertEquals(code, PinCrypto.normalizeCode(code))
    }

    @Test
    fun `normalize returns empty for input with nothing usable`() {
        assertEquals("", PinCrypto.normalizeCode("---"))
    }

    // The look-alike mapping, which is the whole reason I/L/O are absent from the
    // generated alphabet: a written 0 read back as "O" is the likeliest possible
    // transcription slip, and the generator can never legitimately emit an O, so
    // one in user input is unambiguously a misread zero.
    @Test
    fun `normalize maps letter O to zero`() {
        assertEquals("1023", PinCrypto.normalizeCode("1O23"))
    }

    @Test
    fun `normalize maps I and L to one`() {
        assertEquals("1123", PinCrypto.normalizeCode("1I23"))
        assertEquals("1123", PinCrypto.normalizeCode("1L23"))
    }

    @Test
    fun `normalize maps lowercase look-alikes too`() {
        assertEquals("1023", PinCrypto.normalizeCode("1o23"))
        assertEquals("1123", PinCrypto.normalizeCode("1l23"))
    }

    // U is excluded from the alphabet to stop generated codes spelling words, not
    // because it looks like anything — so there is nothing to map it to and it
    // stays dropped.
    @Test
    fun `normalize still drops U, which maps to nothing`() {
        assertEquals("123", PinCrypto.normalizeCode("1U23"))
    }

    // The case that motivated the change: someone reads the printed code off
    // paper, mistakes the leading zero for a letter, and gets back in anyway.
    @Test
    fun `a code misread with letter O still normalizes to the original`() {
        val original = "0AB12CD34E"
        val misread = "OAB12CD34E"

        assertEquals(original, PinCrypto.normalizeCode(misread))
    }

    @Test
    fun `a messily transcribed code still verifies against the real one`() {
        val original = "01ABC2DEF3"
        // Lowercased, spaced out, and with both look-alike slips.
        val misread = "o1 abc2 def3".replace("1", "l")

        assertEquals(original, PinCrypto.normalizeCode(misread))
    }
}
