package com.babegetthis.android.testing

import com.babegetthis.android.core.pin.data.PinStore
import io.mockk.every
import io.mockk.mockk

// A PinStore backed by plain fields instead of EncryptedSharedPreferences.
//
// The real store's MasterKey needs the AndroidKeyStore provider, which
// Robolectric does not supply, so PinRepository cannot be exercised on the JVM
// against the real thing (PinStoreTest covers that on a device). This keeps the
// repository's own logic — throttling, verification, recovery — testable here,
// where it belongs: none of that logic cares how the bytes are persisted.
//
// Deliberately stateful rather than a call-verifying mock: the repository reads
// back what it writes constantly (attempts, lockout expiries), so assertions
// about behavior only mean anything if writes are actually visible to
// subsequent reads.
fun inMemoryPinStore(): PinStore {
    val store = mockk<PinStore>()

    var pinHash: String? = null
    var pinSalt: String? = null
    var recoveryHash: String? = null
    var recoverySalt: String? = null
    var attempts = 0
    var lockoutUntilWall = 0L
    var lockoutUntilElapsed = 0L

    every { store.pinHash } answers { pinHash }
    every { store.pinHash = any() } answers { pinHash = firstArg() }
    every { store.pinSalt } answers { pinSalt }
    every { store.pinSalt = any() } answers { pinSalt = firstArg() }
    every { store.recoveryHash } answers { recoveryHash }
    every { store.recoveryHash = any() } answers { recoveryHash = firstArg() }
    every { store.recoverySalt } answers { recoverySalt }
    every { store.recoverySalt = any() } answers { recoverySalt = firstArg() }
    every { store.attempts } answers { attempts }
    every { store.attempts = any() } answers { attempts = firstArg() }
    every { store.lockoutUntilWall } answers { lockoutUntilWall }
    every { store.lockoutUntilWall = any() } answers { lockoutUntilWall = firstArg() }
    every { store.lockoutUntilElapsed } answers { lockoutUntilElapsed }
    every { store.lockoutUntilElapsed = any() } answers { lockoutUntilElapsed = firstArg() }
    every { store.clearAll() } answers {
        pinHash = null
        pinSalt = null
        recoveryHash = null
        recoverySalt = null
        attempts = 0
        lockoutUntilWall = 0L
        lockoutUntilElapsed = 0L
    }

    return store
}

// Wall and elapsed clocks the test drives by hand, so throttle expiry is a
// deliberate step rather than a real wait. Two independent clocks because the
// throttle honours whichever leaves more time — that is the anti-tamper
// property, and testing it needs them to move independently.
class FakePinClock(
    var wall: Long = 1_000_000L,
    var elapsed: Long = 500_000L,
) : com.babegetthis.android.core.pin.data.PinClock {
    override fun wallMillis(): Long = wall
    override fun elapsedMillis(): Long = elapsed

    // Advance both clocks together, the way real time passes.
    fun advance(millis: Long) {
        wall += millis
        elapsed += millis
    }
}
