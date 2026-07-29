package com.babegetthis.android.core.pin.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Outcome of any PIN or recovery-code entry, carrying enough for the UI to
// render throttle state without knowing the rules.
sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsRemaining: Int) : PinResult
    data class LockedOut(val remainingMs: Long) : PinResult
}

// Device-wide PIN: creation, verification, throttling, change/removal, and the
// one-time recovery code. Stores only salted hashes (see PinCrypto/PinStore).
// Knows nothing about lists — unlocking lists on PIN removal is the caller's
// job, so this stays a pure credential store.
@Singleton
class PinRepository @Inject constructor(
    private val store: PinStore,
    private val clock: PinClock,
) {
    private val _pinExists = MutableStateFlow(store.pinHash != null)
    val pinExists: StateFlow<Boolean> = _pinExists.asStateFlow()

    fun hasPin(): Boolean = store.pinHash != null

    // First-time setup ONLY. Returns the recovery code to show once.
    //
    // The precondition is enforced here rather than trusted: this writes a new
    // PIN without verifying the old one, so if it were ever reached while a PIN
    // existed it would silently replace that PIN — a complete bypass of the
    // control. Every call site is already gated on !pinExists, which is to say
    // the invariant was being held entirely by two `if`s in the UI. Failing loudly
    // is the right trade for a security control: changing a PIN goes through
    // changePin(), which verifies, and recovery goes through
    // resetPinWithRecoveryCode(), which verifies the code.
    fun setupPin(pin: String): String {
        check(!hasPin()) { "setupPin called while a PIN exists — use changePin or resetPinWithRecoveryCode" }
        writePin(pin)
        val code = writeNewRecoveryCode()
        resetAttempts()
        _pinExists.value = true
        return code
    }

    // No PIN stored → no PIN can match, so this is `false`, not a crash. The
    // `!!` this replaces was reachable: any verify racing a removal (or a stale
    // unlock dialog left on screen after the PIN was cleared) hit a null salt and
    // took the whole app down. Mirrors verifyRecoveryCode below, which always
    // handled its own nulls this way.
    fun verifyPin(pin: String): PinResult = guarded {
        val salt = store.pinSalt ?: return@guarded false
        val hash = store.pinHash ?: return@guarded false
        PinCrypto.matches(pin, PinCrypto.decode(salt), PinCrypto.decode(hash))
    }

    // Change requires the current PIN. Recovery code is left untouched.
    fun changePin(currentPin: String, newPin: String): PinResult {
        val r = verifyPin(currentPin)
        if (r is PinResult.Success) writePin(newPin)
        return r
    }

    // Remove requires the current PIN. Wipes pin, recovery, and throttle state.
    // Callers must unlock any locked lists afterwards.
    fun removePin(currentPin: String): PinResult {
        val r = verifyPin(currentPin)
        if (r is PinResult.Success) {
            store.clearAll()
            _pinExists.value = false
        }
        return r
    }

    fun verifyRecoveryCode(code: String): PinResult = guarded {
        val normalized = PinCrypto.normalizeCode(code)
        val salt = store.recoverySalt ?: return@guarded false
        val hash = store.recoveryHash ?: return@guarded false
        PinCrypto.matches(normalized, PinCrypto.decode(salt), PinCrypto.decode(hash))
    }

    // Recovery: a valid code sets a new PIN and issues a NEW recovery code,
    // which overwrites the old hash so the used code stops working. Destroys no
    // list data. Returns the new code on success, null otherwise.
    fun resetPinWithRecoveryCode(code: String, newPin: String): Pair<PinResult, String?> {
        val r = verifyRecoveryCode(code)
        if (r !is PinResult.Success) return r to null
        writePin(newPin)
        val newCode = writeNewRecoveryCode()
        _pinExists.value = true
        return r to newCode
    }

    // Regenerate the recovery code (Settings). Caller must have verified the PIN
    // first. Invalidates the previous code by overwriting its hash.
    fun regenerateRecoveryCode(): String = writeNewRecoveryCode()

    // --- internals ---

    // Runs a hash comparison behind the throttle: refuses while locked out,
    // resets the counter on success, escalates the lockout on failure.
    private inline fun guarded(check: () -> Boolean): PinResult {
        val remaining = throttleRemaining()
        if (remaining > 0) return PinResult.LockedOut(remaining)
        return if (check()) {
            resetAttempts()
            PinResult.Success
        } else {
            registerFailure()
            val after = throttleRemaining()
            if (after > 0) PinResult.LockedOut(after)
            else PinResult.Wrong(PinThrottle.THRESHOLD - (store.attempts % PinThrottle.THRESHOLD))
        }
    }

    private fun throttleRemaining(): Long = PinThrottle.remainingMs(
        store.lockoutUntilWall, store.lockoutUntilElapsed,
        clock.wallMillis(), clock.elapsedMillis(),
    )

    private fun registerFailure() {
        val next = PinThrottle.onFailure(
            store.attempts, store.lockoutUntilWall, store.lockoutUntilElapsed,
            clock.wallMillis(), clock.elapsedMillis(),
        )
        store.attempts = next.attempts
        store.lockoutUntilWall = next.untilWall
        store.lockoutUntilElapsed = next.untilElapsed
    }

    private fun resetAttempts() {
        store.attempts = 0
        store.lockoutUntilWall = 0L
        store.lockoutUntilElapsed = 0L
    }

    private fun writePin(pin: String) {
        val salt = PinCrypto.newSalt()
        store.pinSalt = PinCrypto.encode(salt)
        store.pinHash = PinCrypto.encode(PinCrypto.hash(pin, salt))
    }

    private fun writeNewRecoveryCode(): String {
        val code = PinCrypto.newRecoveryCode()
        val salt = PinCrypto.newSalt()
        store.recoverySalt = PinCrypto.encode(salt)
        store.recoveryHash = PinCrypto.encode(PinCrypto.hash(code, salt))
        return code
    }
}
