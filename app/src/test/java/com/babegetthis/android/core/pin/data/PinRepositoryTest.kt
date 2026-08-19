package com.babegetthis.android.core.pin.data

import com.babegetthis.android.testing.FakePinClock
import com.babegetthis.android.testing.inMemoryPinStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Base64 (via PinCrypto). Real PBKDF2 runs here —
// 120k iterations per hash makes these tests deliberate rather than free, so
// they assert behavior rather than sweeping every input.
@RunWith(RobolectricTestRunner::class)
class PinRepositoryTest {

    private lateinit var clock: FakePinClock
    private lateinit var repository: PinRepository

    @Before
    fun setUp() {
        clock = FakePinClock()
        repository = PinRepository(inMemoryPinStore(), clock)
    }

    // --- setup / existence ---

    @Test
    fun `a fresh repository has no pin`() {
        assertFalse(repository.hasPin())
        assertFalse(repository.pinExists.value)
    }

    @Test
    fun `setupPin stores a pin and flips pinExists`() {
        repository.setupPin("1234")

        assertTrue(repository.hasPin())
        assertTrue(repository.pinExists.value)
    }

    @Test
    fun `setupPin returns a usable recovery code`() {
        val code = repository.setupPin("1234")

        assertEquals(10, code.length)
        assertEquals(PinResult.Success, repository.verifyRecoveryCode(code))
    }

    // The precondition is enforced, not merely documented: reaching this with a
    // PIN present would replace it without verifying the old one.
    @Test(expected = IllegalStateException::class)
    fun `setupPin refuses to overwrite an existing pin`() {
        repository.setupPin("1234")

        repository.setupPin("5678")
    }

    // --- verification ---

    @Test
    fun `verifyPin accepts the right pin`() {
        repository.setupPin("1234")

        assertEquals(PinResult.Success, repository.verifyPin("1234"))
    }

    @Test
    fun `verifyPin rejects the wrong pin and counts down attempts`() {
        repository.setupPin("1234")

        assertEquals(PinResult.Wrong(attemptsRemaining = 4), repository.verifyPin("9999"))
    }

    // No stored PIN means nothing can match — this must not throw.
    @Test
    fun `verifyPin with no pin stored is simply wrong`() {
        assertTrue(repository.verifyPin("1234") is PinResult.Wrong)
    }

    // --- change / remove ---

    @Test
    fun `changePin swaps the pin when the current one is right`() {
        repository.setupPin("1234")

        assertEquals(PinResult.Success, repository.changePin("1234", "5678"))
        assertEquals(PinResult.Success, repository.verifyPin("5678"))
        assertTrue(repository.verifyPin("1234") is PinResult.Wrong)
    }

    @Test
    fun `changePin leaves the pin alone when the current one is wrong`() {
        repository.setupPin("1234")

        assertTrue(repository.changePin("0000", "5678") is PinResult.Wrong)
        assertEquals(PinResult.Success, repository.verifyPin("1234"))
    }

    // Changing the PIN deliberately leaves the recovery code working.
    @Test
    fun `changePin keeps the existing recovery code valid`() {
        val code = repository.setupPin("1234")
        repository.changePin("1234", "5678")

        assertEquals(PinResult.Success, repository.verifyRecoveryCode(code))
    }

    @Test
    fun `removePin clears everything when the current pin is right`() {
        repository.setupPin("1234")

        assertEquals(PinResult.Success, repository.removePin("1234"))
        assertFalse(repository.hasPin())
        assertFalse(repository.pinExists.value)
    }

    @Test
    fun `removePin keeps the pin when the current one is wrong`() {
        repository.setupPin("1234")

        assertTrue(repository.removePin("0000") is PinResult.Wrong)
        assertTrue(repository.hasPin())
    }

    @Test
    fun `the recovery code dies with the pin`() {
        val code = repository.setupPin("1234")
        repository.removePin("1234")

        assertTrue(repository.verifyRecoveryCode(code) is PinResult.Wrong)
    }

    // --- throttling ---

    private fun failTimes(n: Int) = repeat(n) { repository.verifyPin("0000") }

    @Test
    fun `attempts remaining counts down within a tier`() {
        repository.setupPin("1234")

        assertEquals(PinResult.Wrong(4), repository.verifyPin("0000"))
        assertEquals(PinResult.Wrong(3), repository.verifyPin("0000"))
        assertEquals(PinResult.Wrong(2), repository.verifyPin("0000"))
        assertEquals(PinResult.Wrong(1), repository.verifyPin("0000"))
    }

    @Test
    fun `the fifth failure locks out for thirty seconds`() {
        repository.setupPin("1234")

        failTimes(4)
        val result = repository.verifyPin("0000")

        assertTrue(result is PinResult.LockedOut)
        assertEquals(30_000L, (result as PinResult.LockedOut).remainingMs)
    }

    // The whole point of the lockout: the right PIN is refused too, so the
    // attacker gains nothing by guessing correctly during the wait.
    @Test
    fun `the correct pin is refused while locked out`() {
        repository.setupPin("1234")
        failTimes(5)

        assertTrue(repository.verifyPin("1234") is PinResult.LockedOut)
    }

    @Test
    fun `waiting out the lockout re-allows entry`() {
        repository.setupPin("1234")
        failTimes(5)

        clock.advance(30_000L)

        assertEquals(PinResult.Success, repository.verifyPin("1234"))
    }

    @Test
    fun `a successful verify resets the attempt counter`() {
        repository.setupPin("1234")
        failTimes(3)

        repository.verifyPin("1234")

        // Back to a full tier rather than continuing from 3.
        assertEquals(PinResult.Wrong(4), repository.verifyPin("0000"))
    }

    @Test
    fun `lockouts escalate across tiers`() {
        repository.setupPin("1234")

        failTimes(4)
        assertEquals(30_000L, (repository.verifyPin("0000") as PinResult.LockedOut).remainingMs)

        clock.advance(30_000L)
        failTimes(4)
        assertEquals(120_000L, (repository.verifyPin("0000") as PinResult.LockedOut).remainingMs)

        clock.advance(120_000L)
        failTimes(4)
        assertEquals(600_000L, (repository.verifyPin("0000") as PinResult.LockedOut).remainingMs)

        clock.advance(600_000L)
        failTimes(4)
        assertEquals(3_600_000L, (repository.verifyPin("0000") as PinResult.LockedOut).remainingMs)
    }

    // Anti-tamper: winding the wall clock forward past the expiry is not enough,
    // because the elapsed-clock expiry still has time left and the throttle
    // honours whichever leaves more.
    @Test
    fun `moving only the wall clock does not clear a lockout`() {
        repository.setupPin("1234")
        failTimes(5)

        clock.wall += 60_000L

        assertTrue(repository.verifyPin("1234") is PinResult.LockedOut)
    }

    // The mirror case: a reboot zeroes elapsedRealtime, but the wall expiry is
    // still in the future.
    @Test
    fun `rebooting alone does not clear a lockout`() {
        repository.setupPin("1234")
        failTimes(5)

        clock.elapsed = 0L

        assertTrue(repository.verifyPin("1234") is PinResult.LockedOut)
    }

    // --- recovery ---

    @Test
    fun `verifyRecoveryCode accepts a messily typed code`() {
        val code = repository.setupPin("1234")
        val messy = code.lowercase().chunked(4).joinToString(" - ")

        assertEquals(PinResult.Success, repository.verifyRecoveryCode(messy))
    }

    @Test
    fun `verifyRecoveryCode rejects a wrong code`() {
        repository.setupPin("1234")

        assertTrue(repository.verifyRecoveryCode("ZZZZZZZZZZ") is PinResult.Wrong)
    }

    @Test
    fun `resetPinWithRecoveryCode sets the new pin and issues a fresh code`() {
        val code = repository.setupPin("1234")

        val (result, newCode) = repository.resetPinWithRecoveryCode(code, "5678")

        assertEquals(PinResult.Success, result)
        assertNotEquals(code, newCode)
        assertEquals(PinResult.Success, repository.verifyPin("5678"))
    }

    // A used code must stop working, or a leaked one stays live forever.
    @Test
    fun `the old recovery code stops working after a reset`() {
        val code = repository.setupPin("1234")
        repository.resetPinWithRecoveryCode(code, "5678")

        assertTrue(repository.verifyRecoveryCode(code) is PinResult.Wrong)
    }

    @Test
    fun `a failed reset changes nothing and returns no code`() {
        repository.setupPin("1234")

        val (result, newCode) = repository.resetPinWithRecoveryCode("ZZZZZZZZZZ", "5678")

        assertTrue(result is PinResult.Wrong)
        assertNull(newCode)
        assertEquals(PinResult.Success, repository.verifyPin("1234"))
    }

    // Recovery entry shares the PIN's attempt counter, so an attacker cannot
    // switch surfaces to dodge the lockout.
    @Test
    fun `recovery attempts count toward the same throttle as pin attempts`() {
        repository.setupPin("1234")

        failTimes(3)
        repository.verifyRecoveryCode("ZZZZZZZZZZ")
        val result = repository.verifyRecoveryCode("ZZZZZZZZZZ")

        assertTrue("recovery should share the PIN throttle", result is PinResult.LockedOut)
    }

    @Test
    fun `regenerateRecoveryCode invalidates the previous code`() {
        val original = repository.setupPin("1234")

        val fresh = repository.regenerateRecoveryCode()

        assertNotEquals(original, fresh)
        assertEquals(PinResult.Success, repository.verifyRecoveryCode(fresh))
        assertTrue(repository.verifyRecoveryCode(original) is PinResult.Wrong)
    }
}
